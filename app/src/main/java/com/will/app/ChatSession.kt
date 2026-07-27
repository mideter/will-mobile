package com.will.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log


sealed class ChatConnectionState {
    abstract val statusRes: Int?
    open val composerEnabled: Boolean get() = false

    data object Idle : ChatConnectionState() {
        override val statusRes: Int? = null
    }

    data class Connecting(val isReconnect: Boolean) : ChatConnectionState() {
        override val statusRes: Int
            get() = if (isReconnect) R.string.chat_reconnecting else R.string.chat_connecting
    }

    data object Authenticating : ChatConnectionState() {
        override val statusRes: Int = R.string.chat_authenticating
    }

    data object LoadingHistory : ChatConnectionState() {
        override val statusRes: Int = R.string.chat_loading_history
    }

    data object Ready : ChatConnectionState() {
        override val statusRes: Int? = null
        override val composerEnabled: Boolean = true
    }

    data object Reconnecting : ChatConnectionState() {
        override val statusRes: Int = R.string.chat_reconnecting
    }
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
 * UI обновляется через [Listener] на главном потоке (колбэки bridge уже на main).
 * Очередь server-ack живёт в [ChatListAdapter] — сессия только сигналит ConfirmNextSelfAck.
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
        data object Sent : SendResult()
    }

    private val appContext = context.applicationContext
    private val bridge = WillChatBridge()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Буфер HistoryItem до HistoryEnd — чтобы не мигать чатом при reconnect. */
    private val historyBuffer = ArrayList<ChatLine>()

    private var connectionState: ChatConnectionState = ChatConnectionState.Idle

    private val reconnectRunnable = Runnable {
        if (!listener.isSessionActive() ||
            connectionState is ChatConnectionState.Connecting ||
            bridge.isConnected()
        ) {
            return@Runnable
        }
        connect(isReconnect = true)
    }

    private val bridgeListener = object : WillChatBridge.Listener {
        override fun onPeerMessage(authorName: String, text: String) {
            if (!listener.isSessionActive()) return
            emit(ChatUiEvent.AppendPeer(authorName, text))
        }

        override fun onServerReceiptConfirmed() {
            if (!listener.isSessionActive()) return
            emit(ChatUiEvent.ConfirmNextSelfAck)
        }

        override fun onHistoryItem(authorName: String, text: String, isMine: Boolean) {
            if (!listener.isSessionActive()) return
            historyBuffer.add(
                if (isMine) ChatLine.Self(text, selfServerAcked = true)
                else ChatLine.Peer(text, authorName),
            )
        }

        override fun onHistoryLoaded() {
            if (!listener.isSessionActive()) return
            emit(ChatUiEvent.ApplyHistory(historyBuffer.toList()))
            historyBuffer.clear()
            setConnectionState(ChatConnectionState.Ready)
        }

        override fun onError(message: String) {
            if (!listener.isSessionActive()) return
            // Тихий обрыв: без диалога, чат на экране, авто-reconnect.
            Log.w(TAG, message)
            enterReconnectingState()
        }

        override fun onAuthenticating() {
            if (!listener.isSessionActive()) return
            setConnectionState(ChatConnectionState.Authenticating)
        }

        override fun onConnectionChanged(isConnected: Boolean) {
            if (!listener.isSessionActive()) return
            if (!isConnected) {
                enterReconnectingState()
                return
            }
            resetSessionBuffers()
            setConnectionState(ChatConnectionState.LoadingHistory)
        }
    }

    fun isReadyForComposerFocus(): Boolean = connectionState is ChatConnectionState.Ready

    fun connect(isReconnect: Boolean) {
        if (connectionState is ChatConnectionState.Connecting || bridge.isConnected()) return
        mainHandler.removeCallbacks(reconnectRunnable)
        if (!isReconnect) {
            emit(ChatUiEvent.ClearChat)
        }
        resetSessionBuffers()
        setConnectionState(ChatConnectionState.Connecting(isReconnect))

        val deviceToken = DeviceTokenStore.loadOrCreate(appContext)
        bridge.connect(
            WillChatBridge.DEFAULT_HOST,
            WillChatBridge.DEFAULT_PORT,
            deviceToken,
            bridgeListener,
        )
    }

    /** Если сессия не подключена и не в процессе connect — начать reconnect. */
    fun ensureConnected() {
        if (connectionState !is ChatConnectionState.Connecting && !bridge.isConnected()) {
            connect(isReconnect = true)
        }
    }

    fun send(text: String): SendResult {
        if (connectionState !is ChatConnectionState.Ready) return SendResult.NotReady
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SendResult.Empty
        val maxLen = appContext.resources.getInteger(R.integer.max_message_length)
        if (trimmed.length > maxLen) return SendResult.TooLong(maxLen)
        emit(ChatUiEvent.AppendSelf(trimmed))
        bridge.sendLine(trimmed)
        return SendResult.Sent
    }

    fun destroy() {
        mainHandler.removeCallbacks(reconnectRunnable)
        bridge.disconnectServer()
    }

    private fun emit(event: ChatUiEvent) = listener.onEvent(event)

    private fun setConnectionState(state: ChatConnectionState) {
        connectionState = state
        emit(ChatUiEvent.ConnectionChanged(state))
    }

    /** Сброс локального буфера истории перед новым циклом сессии. */
    private fun resetSessionBuffers() {
        historyBuffer.clear()
    }

    private fun enterReconnectingState() {
        resetSessionBuffers()
        setConnectionState(ChatConnectionState.Reconnecting)
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
