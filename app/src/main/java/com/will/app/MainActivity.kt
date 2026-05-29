package com.will.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import java.util.ArrayDeque


class MainActivity : Activity() {

    private val bridge = WillChatBridge()
    private lateinit var chatAdapter: ChatListAdapter
    private lateinit var chatList: ListView
    private lateinit var composerWrap: View
    private lateinit var editMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnConnect: Button

    /** Позиции своих сообщений, ожидающих ack сервера (FIFO, как кадры на сервере). */
    private val pendingSelfAckPositions = ArrayDeque<Int>()

    /** История с сервера получена; можно отправлять сообщения. */
    private var historyLoaded = false

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
            appendChatLine(kind, text)
        }

        override fun onHistoryLoaded() {
            if (isFinishing) return
            historyLoaded = true
            setComposerEnabled(true)
            appendChatLine(ChatLineKind.SYSTEM, getString(R.string.chat_connected))
        }

        override fun onError(message: String) {
            if (isFinishing) return
            AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.network_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            appendChatLine(ChatLineKind.SYSTEM, getString(R.string.chat_error_prefix, message))
        }

        override fun onConnectionChanged(connected: Boolean) {
            if (isFinishing) return
            if (!connected) {
                historyLoaded = false
                pendingSelfAckPositions.clear()
                setConnectedUi(connected = false)
                appendChatLine(ChatLineKind.SYSTEM, getString(R.string.chat_disconnected))
                return
            }
            historyLoaded = false
            setConnectedUi(connected = true, composerEnabled = false)
            appendChatLine(ChatLineKind.SYSTEM, getString(R.string.chat_loading_history))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chatList = findViewById(R.id.chatList)
        composerWrap = findViewById(R.id.composerWrap)
        editMessage = findViewById(R.id.editMessage)
        btnSend = findViewById(R.id.btnSend)
        btnConnect = findViewById(R.id.btnConnect)

        chatAdapter = ChatListAdapter(this)
        chatList.adapter = chatAdapter

        chatList.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                showComposer()
            }
            false
        }

        btnConnect.setOnClickListener { onToggleConnect() }
        btnSend.setOnClickListener { onSend() }

        editMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                onSend()
                true
            } else {
                false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        markPeerMessagesRead()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            markPeerMessagesRead()
        }
    }

    override fun onDestroy() {
        bridge.disconnectServer()
        super.onDestroy()
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

    private fun onToggleConnect() {
        if (bridge.isConnected()) {
            bridge.disconnectServer()
            return
        }

        chatAdapter.clear()
        pendingSelfAckPositions.clear()
        historyLoaded = false
        appendChatLine(ChatLineKind.SYSTEM, getString(R.string.chat_connecting))
        bridge.connectDefaultServer(bridgeListener)
    }

    private fun onSend() {
        if (!bridge.isConnected() || !historyLoaded) return
        val trimmed = editMessage.text?.toString()?.trim().orEmpty()
        if (trimmed.isEmpty()) return
        val maxLen = resources.getInteger(R.integer.max_message_length)
        if (trimmed.length > maxLen) {
            appendChatLine(
                ChatLineKind.SYSTEM,
                getString(R.string.chat_message_too_long, maxLen),
            )
            return
        }
        appendChatLine(ChatLineKind.SELF, trimmed)
        pendingSelfAckPositions.addLast(chatAdapter.count - 1)
        bridge.sendLine(trimmed)
        editMessage.text?.clear()
    }

    private fun setConnectedUi(connected: Boolean, composerEnabled: Boolean = connected) {
        btnConnect.setText(if (connected) R.string.disconnect else R.string.connect)
        setComposerEnabled(composerEnabled)
    }

    private fun setComposerEnabled(enabled: Boolean) {
        editMessage.isEnabled = enabled
        btnSend.isEnabled = enabled
    }
}
