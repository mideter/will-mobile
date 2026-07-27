package com.will.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.ArrayDeque


sealed class ChatUiEvent {
    data object ClearChat : ChatUiEvent()
    data class AppendPeer(val authorName: String, val text: String) : ChatUiEvent()
    data class AppendSelf(val text: String) : ChatUiEvent()
    data class MarkSelfAcked(val position: Int) : ChatUiEvent()
    data class ApplyHistory(val items: List<ChatLine>) : ChatUiEvent()
    data class Status(val textRes: Int?) : ChatUiEvent()
    data class ComposerEnabled(val enabled: Boolean) : ChatUiEvent()
}


/**
 * Сессия чата: TCP-bridge, reconnect, буфер истории и очередь ack своих сообщений.
 * UI обновляется через [Listener] на главном потоке (колбэки bridge уже на main).
 */
class ChatSession(
    context: Context,
    private val listener: Listener,
) {

    interface Listener {
        fun isSessionActive(): Boolean
        /**
         * Обработка UI-события.
         * @return для [ChatUiEvent.AppendSelf] — позиция строки; иначе 0
         */
        fun onEvent(event: ChatUiEvent): Int
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

    /** Позиции своих сообщений, ожидающих ack сервера (FIFO, как кадры на сервере). */
    private val pendingSelfAckPositions = ArrayDeque<Int>()

    /** Буфер HistoryItem до HistoryEnd — чтобы не мигать чатом при reconnect. */
    private val historyBuffer = ArrayList<ChatLine>()

    /** История с сервера получена; можно отправлять сообщения. */
    private var historyLoaded = false

    private var connecting = false

    private val reconnectRunnable = Runnable {
        if (!listener.isSessionActive() || connecting || bridge.isConnected()) return@Runnable
        connect(isReconnect = true)
    }

    private val bridgeListener = object : WillChatBridge.Listener {
        override fun onPeerMessage(authorName: String, text: String) {
            if (!listener.isSessionActive()) return
            emit(ChatUiEvent.AppendPeer(authorName, text))
        }

        override fun onServerReceiptConfirmed() {
            if (!listener.isSessionActive()) return
            val pos = pendingSelfAckPositions.pollFirst() ?: return
            emit(ChatUiEvent.MarkSelfAcked(pos))
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
            historyLoaded = true
            emit(ChatUiEvent.ComposerEnabled(true))
            emit(ChatUiEvent.Status(null))
        }

        override fun onError(message: String) {
            if (!listener.isSessionActive()) return
            // Тихий обрыв: без диалога, чат на экране, авто-reconnect.
            Log.w(TAG, message)
            enterReconnectingState()
        }

        override fun onAuthenticating() {
            if (!listener.isSessionActive()) return
            emit(ChatUiEvent.Status(R.string.chat_authenticating))
        }

        override fun onConnectionChanged(isConnected: Boolean) {
            if (!listener.isSessionActive()) return
            if (!isConnected) {
                enterReconnectingState()
                return
            }
            historyLoaded = false
            historyBuffer.clear()
            connecting = false
            emit(ChatUiEvent.ComposerEnabled(false))
            emit(ChatUiEvent.Status(R.string.chat_loading_history))
        }
    }

    fun isReadyForComposerFocus(): Boolean = bridge.isConnected() && historyLoaded

    fun connect(isReconnect: Boolean) {
        if (connecting || bridge.isConnected()) return
        mainHandler.removeCallbacks(reconnectRunnable)
        if (!isReconnect) {
            emit(ChatUiEvent.ClearChat)
        }
        historyBuffer.clear()
        pendingSelfAckPositions.clear()
        historyLoaded = false
        connecting = true
        emit(ChatUiEvent.ComposerEnabled(false))
        emit(
            ChatUiEvent.Status(
                if (isReconnect) R.string.chat_reconnecting else R.string.chat_connecting,
            ),
        )

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
        if (!connecting && !bridge.isConnected()) {
            connect(isReconnect = true)
        }
    }

    fun send(text: String): SendResult {
        if (!bridge.isConnected() || !historyLoaded) return SendResult.NotReady
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SendResult.Empty
        val maxLen = appContext.resources.getInteger(R.integer.max_message_length)
        if (trimmed.length > maxLen) return SendResult.TooLong(maxLen)
        val position = emit(ChatUiEvent.AppendSelf(trimmed))
        pendingSelfAckPositions.addLast(position)
        bridge.sendLine(trimmed)
        return SendResult.Sent
    }

    fun destroy() {
        mainHandler.removeCallbacks(reconnectRunnable)
        bridge.disconnectServer()
    }

    private fun emit(event: ChatUiEvent): Int = listener.onEvent(event)

    private fun enterReconnectingState() {
        connecting = false
        historyLoaded = false
        historyBuffer.clear()
        pendingSelfAckPositions.clear()
        emit(ChatUiEvent.ComposerEnabled(false))
        emit(ChatUiEvent.Status(R.string.chat_reconnecting))
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
