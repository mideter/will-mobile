package com.will.app

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.ArrayDeque


class MainActivity : Activity() {

    private val bridge = WillChatBridge()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var chatAdapter: ChatListAdapter
    private lateinit var chatList: ListView
    private lateinit var composerWrap: View
    private lateinit var editMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var connectionStatus: TextView

    /** Позиции своих сообщений, ожидающих ack сервера (FIFO, как кадры на сервере). */
    private val pendingSelfAckPositions = ArrayDeque<Int>()

    /** Буфер HistoryItem до HistoryEnd — чтобы не мигать чатом при reconnect. */
    private val historyBuffer = ArrayList<ChatLine>()

    /** История с сервера получена; можно отправлять сообщения. */
    private var historyLoaded = false

    private var connecting = false

    private val reconnectRunnable = Runnable {
        if (isFinishing || connecting || bridge.isConnected()) return@Runnable
        connect(isReconnect = true)
    }

    private val bridgeListener = object : WillChatBridge.Listener {
        override fun onPeerMessage(text: String) {
            if (isFinishing) return
            val unread = !hasWindowFocus()
            appendChatLine(ChatLineKind.PEER, text, peerUnread = unread)
        }

        override fun onServerReceiptConfirmed() {
            if (isFinishing) return
            val pos = pendingSelfAckPositions.pollFirst() ?: return
            chatAdapter.markSelfServerAckedAt(pos)
        }

        override fun onHistoryItem(text: String, isMine: Boolean) {
            if (isFinishing) return
            val kind = if (isMine) ChatLineKind.SELF else ChatLineKind.PEER
            historyBuffer.add(ChatLine(kind, text, selfServerAcked = isMine))
        }

        override fun onHistoryLoaded() {
            if (isFinishing) return
            val added = chatAdapter.applyHistoryReplay(historyBuffer)
            historyBuffer.clear()
            historyLoaded = true
            setComposerEnabled(true)
            setConnectionStatus(null)
            if (added > 0) {
                val pos = chatAdapter.count - 1
                if (pos >= 0) chatList.setSelection(pos)
            }
        }

        override fun onError(message: String) {
            if (isFinishing) return
            // Тихий обрыв: без диалога, чат на экране, авто-reconnect.
            connecting = false
            historyLoaded = false
            historyBuffer.clear()
            pendingSelfAckPositions.clear()
            setComposerEnabled(false)
            setConnectionStatus(R.string.chat_reconnecting)
            scheduleReconnect()
        }

        override fun onAuthenticating() {
            if (isFinishing) return
            setConnectionStatus(R.string.chat_authenticating)
        }

        override fun onConnectionChanged(isConnected: Boolean) {
            if (isFinishing) return
            if (!isConnected) {
                historyLoaded = false
                historyBuffer.clear()
                pendingSelfAckPositions.clear()
                connecting = false
                setComposerEnabled(false)
                setConnectionStatus(R.string.chat_reconnecting)
                scheduleReconnect()
                return
            }
            historyLoaded = false
            historyBuffer.clear()
            connecting = false
            setComposerEnabled(false)
            setConnectionStatus(R.string.chat_loading_history)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemBarInsets()

        chatList = findViewById(R.id.chatList)
        composerWrap = findViewById(R.id.composerWrap)
        editMessage = findViewById(R.id.editMessage)
        btnSend = findViewById(R.id.btnSend)
        connectionStatus = findViewById(R.id.connectionStatus)

        chatAdapter = ChatListAdapter(this)
        chatList.adapter = chatAdapter

        chatList.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                showComposer()
            }
            false
        }

        btnSend.setOnClickListener { onSend() }

        editMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                onSend()
                true
            } else {
                false
            }
        }

        connect(isReconnect = false)
    }

    override fun onResume() {
        super.onResume()
        markPeerMessagesRead()
        if (!connecting && !bridge.isConnected()) {
            connect(isReconnect = true)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            markPeerMessagesRead()
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(reconnectRunnable)
        bridge.disconnectServer()
        super.onDestroy()
    }

    private fun applySystemBarInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }

        val headerWrap = findViewById<View>(R.id.headerWrap)
        val baseHeaderPaddingTop = headerWrap.paddingTop
        headerWrap.setOnApplyWindowInsetsListener { view, insets ->
            val statusBarTop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.statusBars()).top
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsets.top
            }
            view.setPadding(
                view.paddingLeft,
                baseHeaderPaddingTop + statusBarTop,
                view.paddingRight,
                view.paddingBottom,
            )
            insets
        }
        headerWrap.requestApplyInsets()
    }

    private fun showComposer() {
        if (composerWrap.visibility == View.VISIBLE) return
        composerWrap.visibility = View.VISIBLE
        if (bridge.isConnected() && historyLoaded) {
            editMessage.requestFocus()
        }
    }

    private fun markPeerMessagesRead() {
        chatAdapter.markPeerRead()
    }

    private fun appendChatLine(kind: ChatLineKind, text: String, peerUnread: Boolean = false) {
        chatAdapter.append(ChatLine(kind, text, peerUnread))
        val pos = chatAdapter.count - 1
        if (pos >= 0) {
            chatList.setSelection(pos)
        }
    }

    private fun setConnectionStatus(textRes: Int?) {
        if (textRes == null) {
            connectionStatus.text = ""
            connectionStatus.visibility = View.GONE
        } else {
            connectionStatus.setText(textRes)
            connectionStatus.visibility = View.VISIBLE
        }
    }

    private fun scheduleReconnect() {
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS)
    }

    private fun connect(isReconnect: Boolean) {
        if (connecting || bridge.isConnected()) return
        mainHandler.removeCallbacks(reconnectRunnable)
        if (!isReconnect) {
            chatAdapter.clear()
        }
        historyBuffer.clear()
        pendingSelfAckPositions.clear()
        historyLoaded = false
        connecting = true
        setComposerEnabled(false)
        setConnectionStatus(
            if (isReconnect) R.string.chat_reconnecting else R.string.chat_connecting,
        )

        val deviceToken = DeviceTokenStore.loadOrCreate(this)
        bridge.connect(
            WillChatBridge.DEFAULT_HOST,
            WillChatBridge.DEFAULT_PORT,
            deviceToken,
            bridgeListener,
        )
    }

    private fun onSend() {
        if (!bridge.isConnected() || !historyLoaded) return
        val trimmed = editMessage.text?.toString()?.trim().orEmpty()
        if (trimmed.isEmpty()) return
        val maxLen = resources.getInteger(R.integer.max_message_length)
        if (trimmed.length > maxLen) {
            Toast.makeText(
                this,
                getString(R.string.chat_message_too_long, maxLen),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        appendChatLine(ChatLineKind.SELF, trimmed)
        pendingSelfAckPositions.addLast(chatAdapter.count - 1)
        bridge.sendLine(trimmed)
        editMessage.text?.clear()
    }

    private fun setComposerEnabled(enabled: Boolean) {
        editMessage.isEnabled = enabled
        btnSend.isEnabled = enabled
    }

    companion object {
        private const val RECONNECT_DELAY_MS = 3_000L
    }
}
