package com.will.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
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

    override fun getCount(): Int = lines.size

    override fun getItem(position: Int): ChatLine = lines[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.item_chat_line, parent, false)
        val tv = view.findViewById<TextView>(R.id.chatLineText)
        val line = lines[position]

        val display = when (line.kind) {
            ChatLineKind.SYSTEM -> line.text
            ChatLineKind.SELF -> context.getString(R.string.chat_self_prefix, line.text)
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

    private fun rowDrawable(colorRes: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(context.getColor(colorRes))
        return d
    }
}
