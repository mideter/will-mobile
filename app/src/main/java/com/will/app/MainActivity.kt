package com.will.app

import android.app.Activity
import android.app.AlertDialog
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
import java.util.ArrayDeque


class MainActivity : Activity() {

    private enum class AuthPhase {
        Idle,
        RequestingOtp,
        AwaitingCode,
        Connected,
    }

    private val bridge = WillChatBridge()
    private lateinit var chatAdapter: ChatListAdapter
    private lateinit var chatList: ListView
    private lateinit var composerWrap: View
    private lateinit var editMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnConnect: Button
    private lateinit var editPhone: EditText
    private lateinit var editOtpCode: EditText
    private lateinit var connectionStatus: TextView

    /** Позиции своих сообщений, ожидающих ack сервера (FIFO, как кадры на сервере). */
    private val pendingSelfAckPositions = ArrayDeque<Int>()

    /** История с сервера получена; можно отправлять сообщения. */
    private var historyLoaded = false

    private var authPhase = AuthPhase.Idle

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
            setConnectionStatus(null)
        }

        override fun onError(message: String) {
            if (isFinishing) return
            AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.network_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            resetAuthUi()
        }

        override fun onRequestingOtp() {
            if (isFinishing) return
            setAuthPhase(AuthPhase.RequestingOtp)
            setConnectionStatus(R.string.chat_requesting_otp)
        }

        override fun onOtpSent() {
            if (isFinishing) return
            setAuthPhase(AuthPhase.AwaitingCode)
            setConnectionStatus(R.string.chat_otp_sent)
            editOtpCode.visibility = View.VISIBLE
            editOtpCode.requestFocus()
        }

        override fun onConnectionChanged(connected: Boolean) {
            if (isFinishing) return
            if (!connected) {
                historyLoaded = false
                pendingSelfAckPositions.clear()
                resetAuthUi()
                setConnectionStatus(R.string.chat_disconnected)
                return
            }
            historyLoaded = false
            setAuthPhase(AuthPhase.Connected)
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
        btnConnect = findViewById(R.id.btnConnect)
        editPhone = findViewById(R.id.editPhone)
        editOtpCode = findViewById(R.id.editOtpCode)
        connectionStatus = findViewById(R.id.connectionStatus)

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

        editOtpCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && authPhase == AuthPhase.AwaitingCode) {
                onToggleConnect()
                true
            } else {
                false
            }
        }

        setAuthPhase(AuthPhase.Idle)
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

    private fun setAuthPhase(phase: AuthPhase) {
        authPhase = phase
        when (phase) {
            AuthPhase.Idle -> {
                btnConnect.setText(R.string.action_send_otp)
                btnConnect.isEnabled = true
                editPhone.isEnabled = true
                editOtpCode.isEnabled = true
                editOtpCode.visibility = View.GONE
                editOtpCode.text?.clear()
            }
            AuthPhase.RequestingOtp -> {
                btnConnect.isEnabled = false
                editPhone.isEnabled = false
                editOtpCode.visibility = View.GONE
            }
            AuthPhase.AwaitingCode -> {
                btnConnect.setText(R.string.action_verify_otp)
                btnConnect.isEnabled = true
                editPhone.isEnabled = false
                editOtpCode.isEnabled = true
                editOtpCode.visibility = View.VISIBLE
            }
            AuthPhase.Connected -> {
                btnConnect.setText(R.string.disconnect)
                btnConnect.isEnabled = true
                editPhone.isEnabled = false
                editOtpCode.isEnabled = false
                editOtpCode.visibility = View.GONE
            }
        }
    }

    private fun resetAuthUi() {
        setAuthPhase(AuthPhase.Idle)
        setComposerEnabled(false)
    }

    private fun onToggleConnect() {
        when (authPhase) {
            AuthPhase.Connected -> {
                bridge.disconnectServer()
                return
            }
            AuthPhase.AwaitingCode -> {
                submitOtpCode()
                return
            }
            AuthPhase.RequestingOtp -> return
            AuthPhase.Idle -> requestOtp()
        }
    }

    private fun requestOtp() {
        val rawPhone = editPhone.text?.toString()?.trim().orEmpty()
        if (rawPhone.isEmpty()) {
            Toast.makeText(this, R.string.chat_phone_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val phone = WillChatBridge.normalizePhoneE164(rawPhone)
        if (phone == null) {
            Toast.makeText(this, R.string.chat_phone_invalid, Toast.LENGTH_SHORT).show()
            return
        }

        chatAdapter.clear()
        pendingSelfAckPositions.clear()
        historyLoaded = false
        setConnectionStatus(R.string.chat_connecting)
        bridge.requestOtp(
            WillChatBridge.DEFAULT_HOST,
            WillChatBridge.DEFAULT_PORT,
            phone,
            bridgeListener,
        )
    }

    private fun submitOtpCode() {
        val code = editOtpCode.text?.toString()?.trim().orEmpty()
        if (code.isEmpty()) {
            Toast.makeText(this, R.string.chat_otp_empty, Toast.LENGTH_SHORT).show()
            return
        }
        if (!WillChatBridge.isValidOtpCode(code)) {
            Toast.makeText(this, R.string.chat_otp_invalid, Toast.LENGTH_SHORT).show()
            return
        }

        btnConnect.isEnabled = false
        editOtpCode.isEnabled = false
        setConnectionStatus(R.string.chat_verifying_otp)
        bridge.submitOtpCode(code)
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
}
