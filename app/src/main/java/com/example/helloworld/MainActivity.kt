package com.example.helloworld

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ListView

class MainActivity : Activity() {

    private val bridge = WillChatBridge()
    private lateinit var chatAdapter: ChatListAdapter
    private lateinit var chatList: ListView
    private lateinit var composerWrap: View
    private lateinit var editMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnConnect: Button

    private val bridgeListener = object : WillChatBridge.Listener {
        override fun onPeerMessage(text: String) {
            if (isFinishing) return
            val unread = !hasWindowFocus()
            appendChatLine(ChatLineKind.PEER, text, peerUnread = unread)
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
            setConnectedUi(connected)
            if (connected) {
                appendChatLine(ChatLineKind.SYSTEM, getString(R.string.chat_connected))
            } else {
                appendChatLine(ChatLineKind.SYSTEM, getString(R.string.chat_disconnected))
            }
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
        if (bridge.isConnected()) {
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

        appendChatLine(ChatLineKind.SYSTEM, getString(R.string.chat_connecting))
        bridge.connectDefaultServer(bridgeListener)
    }

    private fun onSend() {
        if (!bridge.isConnected()) return
        val text = editMessage.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        appendChatLine(ChatLineKind.SELF, text)
        bridge.sendLine(text)
        editMessage.text?.clear()
    }

    private fun setConnectedUi(connected: Boolean) {
        btnConnect.setText(if (connected) R.string.disconnect else R.string.connect)
        editMessage.isEnabled = connected
        btnSend.isEnabled = connected
    }
}
