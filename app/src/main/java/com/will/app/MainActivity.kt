package com.will.app

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideComposerRunnable = Runnable { hideComposerIfUnused() }

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
                    val typed = editMessage.text?.toString().orEmpty()
                    if (typed.trim() == event.text) {
                        editMessage.text?.clear()
                    }
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
                    editMessage.isEnabled = state.composerEnabled
                    btnSend.isEnabled = state.composerEnabled
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
        setupComposer()

        session = ChatSession(this, sessionListener)
        session.connect(isReconnect = false)
    }

    private fun bindViews() {
        chatList = findViewById(R.id.chatList)
        composerWrap = findViewById(R.id.composerWrap)
        editMessage = findViewById(R.id.editMessage)
        btnSend = findViewById(R.id.btnSend)
        connectionStatus = findViewById(R.id.connectionStatus)
    }

    private fun setupChatList() {
        chatAdapter = ChatListAdapter(this)
        chatList.adapter = chatAdapter
        chatList.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                toggleComposerFromChatTouch()
            }
            false
        }
    }

    private fun setupComposer() {
        btnSend.setOnClickListener { onSend() }
        editMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                onSend()
                true
            } else {
                false
            }
        }
        editMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                bumpComposerIdleTimer()
            } else {
                // Короткая задержка: успеть обработать tap по «→».
                scheduleComposerHide(COMPOSER_FOCUS_LOSS_HIDE_MS)
            }
        }
        editMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (composerWrap.visibility == View.VISIBLE) {
                    bumpComposerIdleTimer()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        session.ensureConnected()
    }

    override fun onDestroy() {
        cancelComposerHide()
        session.destroy()
        super.onDestroy()
    }

    private fun scrollChatToEnd() {
        val pos = chatAdapter.count - 1
        if (pos >= 0) {
            chatList.setSelection(pos)
        }
    }

    /** Тап по чату: показать композер или спрятать, если черновика нет. */
    private fun toggleComposerFromChatTouch() {
        if (composerWrap.visibility == View.VISIBLE) {
            if (composerHasDraft()) {
                editMessage.clearFocus()
                hideKeyboard()
            } else {
                hideComposer()
            }
        } else {
            showComposer()
        }
    }

    private fun showComposer() {
        cancelComposerHide()
        composerWrap.visibility = View.VISIBLE
        if (session.isReadyForComposerFocus()) {
            editMessage.requestFocus()
        }
        bumpComposerIdleTimer()
    }

    private fun hideComposer() {
        cancelComposerHide()
        if (composerWrap.visibility != View.VISIBLE) return
        editMessage.clearFocus()
        hideKeyboard()
        composerWrap.visibility = View.GONE
    }

    /** Скрыть только если нет черновика (фокус-loss / idle). */
    private fun hideComposerIfUnused() {
        if (composerWrap.visibility != View.VISIBLE) return
        if (composerHasDraft()) return
        // Не прятать, пока палец на кнопке отправки.
        if (btnSend.isPressed) {
            scheduleComposerHide(COMPOSER_FOCUS_LOSS_HIDE_MS)
            return
        }
        hideComposer()
    }

    private fun composerHasDraft(): Boolean =
        editMessage.text?.toString().orEmpty().isNotBlank()

    private fun bumpComposerIdleTimer() {
        scheduleComposerHide(COMPOSER_IDLE_HIDE_MS)
    }

    private fun scheduleComposerHide(delayMs: Long) {
        mainHandler.removeCallbacks(hideComposerRunnable)
        mainHandler.postDelayed(hideComposerRunnable, delayMs)
    }

    private fun cancelComposerHide() {
        mainHandler.removeCallbacks(hideComposerRunnable)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val token = currentFocus?.windowToken ?: composerWrap.windowToken
        imm.hideSoftInputFromWindow(token, 0)
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
            ChatSession.SendResult.Accepted,
            ChatSession.SendResult.NotReady,
            ChatSession.SendResult.Empty,
            -> Unit
        }
    }

    companion object {
        /** Простой без ввода — спрятать пустой композер. */
        private const val COMPOSER_IDLE_HIDE_MS = 4_000L

        /** После потери фокуса; короче idle, чтобы не мешать «→». */
        private const val COMPOSER_FOCUS_LOSS_HIDE_MS = 250L
    }
}
