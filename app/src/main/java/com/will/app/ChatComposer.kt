package com.will.app

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText


/**
 * Нижняя панель ввода: показ/скрытие по тапу, потере фокуса и простою.
 * Отправку делегирует в [onSend].
 */
class ChatComposer(
    private val activity: Activity,
    private val wrap: View,
    private val editMessage: EditText,
    private val btnSend: Button,
    private val canRequestFocus: () -> Boolean,
    private val onSend: (String) -> Unit,
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideIfUnusedRunnable = Runnable { hideIfUnused() }

    init {
        btnSend.setOnClickListener { onSend(editMessage.text?.toString().orEmpty()) }
        editMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                onSend(editMessage.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }
        editMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                bumpIdleTimer()
            } else {
                // Короткая задержка: успеть обработать tap по «→».
                scheduleHide(FOCUS_LOSS_HIDE_MS)
            }
        }
        editMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (wrap.visibility == View.VISIBLE) {
                    bumpIdleTimer()
                }
            }
        })
    }

    fun setEnabled(enabled: Boolean) {
        editMessage.isEnabled = enabled
        btnSend.isEnabled = enabled
    }

    /** Тап по чату: показать или спрятать (черновик только снимает фокус/IME). */
    fun toggleFromChatTouch() {
        if (wrap.visibility == View.VISIBLE) {
            if (hasDraft()) {
                editMessage.clearFocus()
                hideKeyboard()
            } else {
                hide()
            }
        } else {
            show()
        }
    }

    fun show() {
        cancelHide()
        wrap.visibility = View.VISIBLE
        if (canRequestFocus()) {
            editMessage.requestFocus()
        }
        bumpIdleTimer()
    }

    fun hide() {
        cancelHide()
        if (wrap.visibility != View.VISIBLE) return
        editMessage.clearFocus()
        hideKeyboard()
        wrap.visibility = View.GONE
    }

    /** После подтверждённой отправки — очистить поле, если текст ещё совпадает. */
    fun clearIfMatches(sentText: String) {
        val typed = editMessage.text?.toString().orEmpty()
        if (typed.trim() == sentText) {
            editMessage.text?.clear()
        }
    }

    fun destroy() {
        cancelHide()
    }

    private fun hideIfUnused() {
        if (wrap.visibility != View.VISIBLE) return
        if (hasDraft()) return
        // Не прятать, пока палец на кнопке отправки.
        if (btnSend.isPressed) {
            scheduleHide(FOCUS_LOSS_HIDE_MS)
            return
        }
        hide()
    }

    private fun hasDraft(): Boolean =
        editMessage.text?.toString().orEmpty().isNotBlank()

    private fun bumpIdleTimer() {
        scheduleHide(IDLE_HIDE_MS)
    }

    private fun scheduleHide(delayMs: Long) {
        mainHandler.removeCallbacks(hideIfUnusedRunnable)
        mainHandler.postDelayed(hideIfUnusedRunnable, delayMs)
    }

    private fun cancelHide() {
        mainHandler.removeCallbacks(hideIfUnusedRunnable)
    }

    private fun hideKeyboard() {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val token = activity.currentFocus?.windowToken ?: wrap.windowToken
        imm.hideSoftInputFromWindow(token, 0)
    }

    companion object {
        /** Простой без ввода — спрятать пустой композер. */
        private const val IDLE_HIDE_MS = 16_000L

        /** После потери фокуса; короче idle, чтобы не мешать «→». */
        private const val FOCUS_LOSS_HIDE_MS = 1_000L
    }
}
