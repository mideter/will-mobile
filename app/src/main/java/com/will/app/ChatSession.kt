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

    /**
     * Peer-сообщения, пришедшие UserChat до HistoryEnd.
     * В UI не пускаем сразу — иначе applyHistory часто не находит overlap и дублирует snapshot.
     */
    private val deferredPeers = ArrayList<DeferredPeer>()

    /** true с момента TCP-up до ApplyHistory (включительно обработка deferred). */
    private var awaitingHistory = false

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
        deferredPeers.clear()
        awaitingHistory = false
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
        awaitingHistory = false
        historyBuffer.clear()
        deferredPeers.clear()
        mainHandler.removeCallbacks(reconnectRunnable)
        bridge.disconnectServer()
    }

    private fun listenerFor(gen: Int) = object : WillChatBridge.Listener {
        override fun onPeerMessage(authorName: String, text: String) {
            if (!isCurrent(gen)) return
            if (awaitingHistory) {
                deferredPeers.add(DeferredPeer(authorName, text))
                return
            }
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
            val snapshot = historyBuffer.toList()
            historyBuffer.clear()
            emit(ChatUiEvent.ApplyHistory(snapshot))

            val pending = deferredPeers.toList()
            deferredPeers.clear()
            awaitingHistory = false

            val skip = countDeferredAlreadyInHistory(snapshot, pending)
            for (i in skip until pending.size) {
                val p = pending[i]
                emit(ChatUiEvent.AppendPeer(p.authorName, p.text))
            }

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
            deferredPeers.clear()
            awaitingHistory = true
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
        awaitingHistory = false
        historyBuffer.clear()
        deferredPeers.clear()
        setConnectionState(ChatConnectionState.Busy(R.string.chat_reconnecting))
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS)
    }

    private data class DeferredPeer(val authorName: String, val text: String)

    companion object {
        private const val TAG = "ChatSession"
        private const val RECONNECT_DELAY_MS = 3_000L

        /**
         * Сколько ведущих deferred уже есть суффиксом snapshot истории
         * (UserChat дублирует хвост HistoryItem).
         */
        private fun countDeferredAlreadyInHistory(
            snapshot: List<ChatLine>,
            pending: List<DeferredPeer>,
        ): Int {
            if (snapshot.isEmpty() || pending.isEmpty()) return 0
            val maxK = minOf(snapshot.size, pending.size)
            for (k in maxK downTo 1) {
                var ok = true
                for (i in 0 until k) {
                    val hist = snapshot[snapshot.size - k + i]
                    val peer = pending[i]
                    if (hist !is ChatLine.Peer ||
                        hist.text != peer.text ||
                        hist.authorName != peer.authorName
                    ) {
                        ok = false
                        break
                    }
                }
                if (ok) return k
            }
            return 0
        }
    }
}
