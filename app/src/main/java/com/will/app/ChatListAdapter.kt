package com.will.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
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
        val icon = view.findViewById<ImageView>(R.id.serverReceiptIcon)
        val line = lines[position]

        icon.visibility = View.GONE
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        icon.contentDescription = null

        when (line.kind) {
            ChatLineKind.SYSTEM -> {
                tv.text = line.text
                tv.gravity = Gravity.START
                tv.setTextColor(context.getColor(R.color.will_muted))
                view.background = null
            }
            ChatLineKind.SELF -> {
                tv.text = context.getString(R.string.chat_self_prefix, line.text)
                tv.gravity = Gravity.END
                tv.setTextColor(context.getColor(R.color.will_ink))
                view.background = rowDrawable(R.color.will_row)
                if (line.selfServerAcked) {
                    icon.visibility = View.VISIBLE
                    icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    icon.contentDescription = context.getString(R.string.chat_server_receipt_cd)
                }
            }
            ChatLineKind.PEER -> {
                tv.text = context.getString(R.string.chat_peer_prefix, line.text)
                tv.gravity = Gravity.START
                tv.setTextColor(context.getColor(R.color.will_ink))
                val bg = if (line.peerUnread) R.color.will_row_unread else R.color.will_row
                view.background = rowDrawable(bg)
            }
        }
        return view
    }

    private fun rowDrawable(colorRes: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(context.getColor(colorRes))
        return d
    }
}
