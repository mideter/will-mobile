package com.will.app

import android.app.Activity
import android.os.Build
import android.os.Bundle
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

        override fun hasWindowFocus(): Boolean = this@MainActivity.hasWindowFocus()

        override fun clearChat() {
            chatAdapter.clear()
        }

        override fun appendPeer(authorName: String, text: String, unread: Boolean) {
            appendChatLine(ChatLineKind.PEER, text, peerUnread = unread, authorName = authorName)
        }

        override fun appendSelf(text: String): Int {
            appendChatLine(ChatLineKind.SELF, text)
            return chatAdapter.count - 1
        }

        override fun markSelfAcked(position: Int) {
            chatAdapter.markSelfServerAckedAt(position)
        }

        override fun applyHistory(items: List<ChatLine>): Int =
            chatAdapter.applyHistoryReplay(items)

        override fun setConnectionStatus(textRes: Int?) {
            if (textRes == null) {
                connectionStatus.text = ""
                connectionStatus.visibility = View.GONE
            } else {
                connectionStatus.setText(textRes)
                connectionStatus.visibility = View.VISIBLE
            }
        }

        override fun setComposerEnabled(enabled: Boolean) {
            editMessage.isEnabled = enabled
            btnSend.isEnabled = enabled
        }

        override fun scrollChatToEnd() {
            val pos = chatAdapter.count - 1
            if (pos >= 0) {
                chatList.setSelection(pos)
            }
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
        chatAdapter.markPeerRead
        session.ensureConnected()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            chatAdapter.markPeerRead()
        }
    }

    override fun onDestroy() {
        session.destroy()
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
        headerWrap.setOnApplyWindowInsetsListener { view, insets ->
            val statusBarTop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.statusBars()).top
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsets.top
            }
            view.setPadding(
                view.paddingLeft,
                statusBarTop,
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
        if (session.isReadyForComposerFocus()) {
            editMessage.requestFocus()
        }
    }

    private fun appendChatLine(
        kind: ChatLineKind,
        text: String,
        peerUnread: Boolean = false,
        authorName: String = "",
    ) {
        chatAdapter.append(ChatLine(kind, text, peerUnread, authorName = authorName))
        sessionListener.scrollChatToEnd()
    }

    private fun onSend() {
        val maxLen = resources.getInteger(R.integer.max_message_length)
        when (
            val result = session.send(editMessage.text?.toString().orEmpty(), maxLen)
        ) {
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
