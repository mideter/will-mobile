package com.will.app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast


class MainActivity : Activity() {

    private lateinit var session: ChatSession
    private lateinit var chatAdapter: ChatListAdapter
    private lateinit var chatList: ListView
    private lateinit var composer: ChatComposer
    private lateinit var connectionStatus: TextView

    private val sessionListener = object : ChatSession.Listener {
        override fun isSessionActive(): Boolean = !isFinishing

        override fun onEvent(event: ChatUiEvent) {
            when (event) {
                ChatUiEvent.ClearChat -> chatAdapter.clear()
                is ChatUiEvent.AppendPeer -> {
                    chatAdapter.append(ChatLine.Peer(event.text, event.authorName))
                    scrollChatToEnd()
                }
                is ChatUiEvent.AppendSelf -> {
                    chatAdapter.append(ChatLine.Self(event.text))
                    scrollChatToEnd()
                    composer.clearIfMatches(event.text)
                }
                ChatUiEvent.ConfirmNextSelfAck -> chatAdapter.confirmNextSelfAck()
                is ChatUiEvent.ApplyHistory -> {
                    val added = chatAdapter.applyHistory(event.items)
                    if (added > 0) scrollChatToEnd()
                }
                is ChatUiEvent.ConnectionChanged -> {
                    val state = event.state
                    // Новый цикл: индексы pending-ack больше не валидны.
                    if (state !is ChatConnectionState.Ready) {
                        chatAdapter.clearPendingSelfAcks()
                    }
                    when (state) {
                        is ChatConnectionState.Busy -> {
                            connectionStatus.setText(state.statusRes)
                            connectionStatus.visibility = View.VISIBLE
                        }
                        ChatConnectionState.Ready -> {
                            connectionStatus.text = ""
                            connectionStatus.visibility = View.GONE
                        }
                    }
                    composer.setEnabled(state.composerEnabled)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SystemBarInsets.applyToHeader(this, R.id.headerWrap)

        bindViews()
        setupChatList()

        session = ChatSession(this, sessionListener)
        session.connect(isReconnect = false)
    }

    private fun bindViews() {
        chatList = findViewById(R.id.chatList)
        connectionStatus = findViewById(R.id.connectionStatus)
        composer = ChatComposer(
            activity = this,
            wrap = findViewById(R.id.composerWrap),
            editMessage = findViewById(R.id.editMessage),
            btnSend = findViewById(R.id.btnSend),
            canRequestFocus = { session.isReadyForComposerFocus() },
            onSend = ::onSend,
        )
    }

    private fun setupChatList() {
        chatAdapter = ChatListAdapter(this)
        chatList.adapter = chatAdapter
        composer.attachChatTapToggle(chatList)
    }

    override fun onResume() {
        super.onResume()
        session.ensureConnected()
    }

    override fun onDestroy() {
        composer.destroy()
        session.destroy()
        super.onDestroy()
    }

    private fun scrollChatToEnd() {
        val pos = chatAdapter.count - 1
        if (pos >= 0) {
            chatList.setSelection(pos)
        }
    }

    private fun onSend(text: String) {
        when (val result = session.send(text)) {
            is ChatSession.SendResult.TooLong -> {
                Toast.makeText(
                    this,
                    getString(R.string.chat_message_too_long, result.maxLen),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            ChatSession.SendResult.Accepted,
            ChatSession.SendResult.NotReady,
            ChatSession.SendResult.Empty,
            -> Unit
        }
    }
}
