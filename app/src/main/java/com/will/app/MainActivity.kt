package com.will.app

import android.app.Activity
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast


class MainActivity : Activity() {

    private lateinit var session: ChatSession
    private lateinit var chatAdapter: ChatListAdapter
    private lateinit var chatList: ListView
    private lateinit var composerWrap: View
    private lateinit var editMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var connectionStatus: TextView

    private val sessionListener = object : ChatSession.Listener {
        override fun isSessionActive(): Boolean = !isFinishing

        override fun onEvent(event: ChatUiEvent): Int = when (event) {
            ChatUiEvent.ClearChat -> {
                chatAdapter.clear()
                0
            }
            is ChatUiEvent.AppendPeer -> {
                chatAdapter.append(ChatLine.Peer(event.text, event.authorName))
                scrollChatToEnd()
                0
            }
            is ChatUiEvent.AppendSelf -> {
                chatAdapter.append(ChatLine.Self(event.text))
                scrollChatToEnd()
                chatAdapter.count - 1
            }
            is ChatUiEvent.MarkSelfAcked -> {
                chatAdapter.markSelfServerAckedAt(event.position)
                0
            }
            is ChatUiEvent.ApplyHistory -> {
                val added = chatAdapter.applyHistoryReplay(event.items)
                if (added > 0) scrollChatToEnd()
                0
            }
            is ChatUiEvent.ConnectionChanged -> {
                val state = event.state
                val statusRes = state.statusRes
                if (statusRes == null) {
                    connectionStatus.text = ""
                    connectionStatus.visibility = View.GONE
                } else {
                    connectionStatus.setText(statusRes)
                    connectionStatus.visibility = View.VISIBLE
                }
                editMessage.isEnabled = state.composerEnabled
                btnSend.isEnabled = state.composerEnabled
                0
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SystemBarInsets.applyToHeader(this, R.id.headerWrap)

        chatList = findViewById(R.id.chatList)
        composerWrap = findViewById(R.id.composerWrap)
        editMessage = findViewById(R.id.editMessage)
        btnSend = findViewById(R.id.btnSend)
        connectionStatus = findViewById(R.id.connectionStatus)

        chatAdapter = ChatListAdapter(this)
        chatList.adapter = chatAdapter

        session = ChatSession(this, sessionListener)

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

        session.connect(isReconnect = false)
    }

    override fun onResume() {
        super.onResume()
        session.ensureConnected()
    }

    override fun onDestroy() {
        session.destroy()
        super.onDestroy()
    }

    private fun scrollChatToEnd() {
        val pos = chatAdapter.count - 1
        if (pos >= 0) {
            chatList.setSelection(pos)
        }
    }

    private fun showComposer() {
        if (composerWrap.visibility == View.VISIBLE) return
        composerWrap.visibility = View.VISIBLE
        if (session.isReadyForComposerFocus()) {
            editMessage.requestFocus()
        }
    }

    private fun onSend() {
        when (val result = session.send(editMessage.text?.toString().orEmpty())) {
            is ChatSession.SendResult.TooLong -> {
                Toast.makeText(
                    this,
                    getString(R.string.chat_message_too_long, result.maxLen),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            ChatSession.SendResult.Sent -> editMessage.text?.clear()
            ChatSession.SendResult.NotReady, ChatSession.SendResult.Empty -> Unit
        }
    }
}
