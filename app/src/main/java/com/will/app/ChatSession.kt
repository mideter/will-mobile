package com.will.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log


/**
 * Для UI и send() важны только два состояния: заняты подключением или готовы писать в чат.
 * Промежуточные фазы (auth, history, reconnect) — разные [Busy.statusRes].
 */
sealed class ChatConnectionState {
    data class Busy(val statusRes: Int) : ChatConnectionState()
    data object Ready : ChatConnectionState()

    val composerEnabled: Boolean
        get() = this is Ready
}


sealed class ChatUiEvent {
    data object ClearChat : ChatUiEvent()
    data class AppendPeer(val authorName: String, val text: String) : ChatUiEvent()
    data class AppendSelf(val text: String) : ChatUiEvent()
    data object ConfirmNextSelfAck : ChatUiEvent()
    data class ApplyHistory(val items: List<ChatLine>) : ChatUiEvent()
    data class ConnectionChanged(val state: ChatConnectionState) : ChatUiEvent()
}


/**
 * Сессия чата: TCP-bridge, reconnect и буфер истории.
 * [generation] отсекает колбэки предыдущего цикла connect.
 * Своё сообщение попадает в UI только после успешной записи в сокет.
 */
class ChatSession(
    context: Context,
    private val listener: Listener,
) {

    interface Listener {
        fun isSessionActive(): Boolean
        fun onEvent(event: ChatUiEvent)
    }

    sealed class SendResult {
        data object NotReady : SendResult()
        data object Empty : SendResult()
        data class TooLong(val maxLen: Int) : SendResult()
        /** Принято в очередь отправки; строка в чате появится после записи в сокет. */
        data object Accepted : SendResult()
    }

    private val appContext = context.applicationContext
    private val bridge = WillChatBridge()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Буфер HistoryItem до HistoryEnd — чтобы не мигать чатом при reconnect. */
    private val historyBuffer = ArrayList<ChatLine>()

    private var connectionState: ChatConnectionState =
        ChatConnectionState.Busy(R.string.chat_connecting)

    /** Инвалидирует колбэки предыдущего connect / обрыва. */
    private var generation = 0

    /** Идёт попытка TCP-connect (не путать с ожиданием delayed reconnect). */
    private var connectAttemptActive = false

    private var sendInFlight = false

    private val reconnectRunnable = Runnable {
        if (!listener.isSessionActive() || connectAttemptActive || connectionState is ChatConnectionState.Ready) {
            return@Runnable
        }
        connect(isReconnect = true)
    }

    fun isReadyForComposerFocus(): Boolean = connectionState is ChatConnectionState.Ready

    fun connect(isReconnect: Boolean) {
        if (connectAttemptActive) return
        connectAttemptActive = true
        sendInFlight = false
        mainHandler.removeCallbacks(reconnectRunnable)

        val gen = ++generation
        if (!isReconnect) {
            emit(ChatUiEvent.ClearChat)
        }
        historyBuffer.clear()
        setConnectionState(
            ChatConnectionState.Busy(
                if (isReconnect) R.string.chat_reconnecting else R.string.chat_connecting,
            ),
        )

        val deviceToken = DeviceTokenStore.loadOrCreate(appContext)
        bridge.connect(
            WillChatBridge.DEFAULT_HOST,
            WillChatBridge.DEFAULT_PORT,
            deviceToken,
            listenerFor(gen),
        )
    }

    /** Если ещё не Ready и нет активной попытки — начать reconnect. */
    fun ensureConnected() {
        if (connectionState is ChatConnectionState.Ready || connectAttemptActive) return
        connect(isReconnect = true)
    }

    fun send(text: String): SendResult {
        if (connectionState !is ChatConnectionState.Ready || sendInFlight) return SendResult.NotReady
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SendResult.Empty
        val maxLen = appContext.resources.getInteger(R.integer.max_message_length)
        if (trimmed.length > maxLen) return SendResult.TooLong(maxLen)

        val gen = generation
        sendInFlight = true
        bridge.sendLine(trimmed) { ok ->
            if (!isCurrent(gen)) {
                sendInFlight = false
                return@sendLine
            }
            sendInFlight = false
            if (ok) {
                emit(ChatUiEvent.AppendSelf(trimmed))
            }
            // При ошибке bridge уже шлёт onError / рвёт сокет → Busy + reconnect.
        }
        return SendResult.Accepted
    }

    fun destroy() {
        generation++
        sendInFlight = false
        connectAttemptActive = false
        mainHandler.removeCallbacks(reconnectRunnable)
        bridge.disconnectServer()
    }

    private fun listenerFor(gen: Int) = object : WillChatBridge.Listener {
        override fun onPeerMessage(authorName: String, text: String) {
            if (!isCurrent(gen)) return
            emit(ChatUiEvent.AppendPeer(authorName, text))
        }

        override fun onServerReceiptConfirmed() {
            if (!isCurrent(gen)) return
            emit(ChatUiEvent.ConfirmNextSelfAck)
        }

        override fun onHistoryItem(authorName: String, text: String, isMine: Boolean) {
            if (!isCurrent(gen)) return
            historyBuffer.add(
                if (isMine) ChatLine.Self(text, selfServerAcked = true)
                else ChatLine.Peer(text, authorName),
            )
        }

        override fun onHistoryLoaded() {
            if (!isCurrent(gen)) return
            emit(ChatUiEvent.ApplyHistory(historyBuffer.toList()))
            historyBuffer.clear()
            connectAttemptActive = false
            setConnectionState(ChatConnectionState.Ready)
        }

        override fun onError(message: String) {
            if (!isCurrent(gen)) return
            Log.w(TAG, message)
            enterReconnectingState()
        }

        override fun onAuthenticating() {
            if (!isCurrent(gen)) return
            setConnectionState(ChatConnectionState.Busy(R.string.chat_authenticating))
        }

        override fun onConnectionChanged(isConnected: Boolean) {
            if (!isCurrent(gen)) return
            if (!isConnected) {
                enterReconnectingState()
                return
            }
            historyBuffer.clear()
            setConnectionState(ChatConnectionState.Busy(R.string.chat_loading_history))
        }
    }

    private fun isCurrent(gen: Int): Boolean =
        listener.isSessionActive() && gen == generation

    private fun emit(event: ChatUiEvent) = listener.onEvent(event)

    private fun setConnectionState(state: ChatConnectionState) {
        connectionState = state
        emit(ChatUiEvent.ConnectionChanged(state))
    }

    private fun enterReconnectingState() {
        generation++
        sendInFlight = false
        connectAttemptActive = false
        historyBuffer.clear()
        setConnectionState(ChatConnectionState.Busy(R.string.chat_reconnecting))
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS)
    }

    companion object {
        private const val TAG = "ChatSession"
        private const val RECONNECT_DELAY_MS = 3_000L
    }
}
