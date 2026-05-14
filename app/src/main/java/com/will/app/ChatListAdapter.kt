package com.will.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

enum class ChatLineKind { SYSTEM, SELF, PEER }

data class ChatLine(
    val kind: ChatLineKind,
    val text: String,
    var peerUnread: Boolean = false,
    /** Только для [ChatLineKind.SELF]: сервер принял кадр (WillMessage ack). */
    var selfServerAcked: Boolean = false,
)

class ChatListAdapter(private val context: Context) : BaseAdapter() {

    private val lines = ArrayList<ChatLine>()
    private val inflater = LayoutInflater.from(context)

    fun append(line: ChatLine) {
        lines.add(line)
        notifyDataSetChanged()
    }

    fun markPeerRead() {
        var changed = false
        for (line in lines) {
            if (line.kind == ChatLineKind.PEER && line.peerUnread) {
                line.peerUnread = false
                changed = true
            }
        }
        if (changed) notifyDataSetChanged()
    }

    fun markSelfServerAckedAt(position: Int) {
        if (position < 0 || position >= lines.size) return
        val line = lines[position]
        if (line.kind != ChatLineKind.SELF || line.selfServerAcked) return
        line.selfServerAcked = true
        notifyDataSetChanged()
    }

    override fun getCount(): Int = lines.size

    override fun getItem(position: Int): ChatLine = lines[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.item_chat_line, parent, false)
        val tv = view.findViewById<TextView>(R.id.chatLineText)
        val line = lines[position]

        val display: CharSequence = when (line.kind) {
            ChatLineKind.SYSTEM -> line.text
            ChatLineKind.SELF -> selfLineCharSequence(line)
            ChatLineKind.PEER -> context.getString(R.string.chat_peer_prefix, line.text)
        }
        tv.text = display

        when (line.kind) {
            ChatLineKind.SYSTEM -> {
                tv.setTextColor(context.getColor(R.color.will_muted))
                tv.background = null
            }
            ChatLineKind.SELF -> {
                tv.setTextColor(context.getColor(R.color.will_ink))
                tv.background = rowDrawable(R.color.will_row)
            }
            ChatLineKind.PEER -> {
                tv.setTextColor(context.getColor(R.color.will_ink))
                val bg = if (line.peerUnread) R.color.will_row_unread else R.color.will_row
                tv.background = rowDrawable(bg)
            }
        }
        return view
    }

    /** Одна приглушённая галочка: сервер подтвердил приём (WillMessage ack). */
    private fun selfLineCharSequence(line: ChatLine): CharSequence {
        val base = context.getString(R.string.chat_self_prefix, line.text)
        if (!line.selfServerAcked) return base
        val suffix = "  \u2713"
        val ss = SpannableString(base + suffix)
        val start = base.length
        val end = ss.length
        val tickColor = context.getColor(R.color.will_muted)
        ss.setSpan(RelativeSizeSpan(0.78f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(ForegroundColorSpan(tickColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return ss
    }

    private fun rowDrawable(colorRes: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(context.getColor(colorRes))
        return d
    }
}
