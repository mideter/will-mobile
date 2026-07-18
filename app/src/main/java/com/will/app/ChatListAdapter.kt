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

    fun clear() {
        if (lines.isEmpty()) return
        lines.clear()
        notifyDataSetChanged()
    }

    /**
     * Подставляет историю с сервера без мигания: сохраняет уже показанные строки,
     * дописывает только новые; хвост локальных (например неотправленное) не трогает.
     * @return сколько строк добавлено
     */
    fun applyHistoryReplay(items: List<ChatLine>): Int {
        if (items.isEmpty()) return 0
        if (lines.isEmpty()) {
            lines.addAll(items)
            notifyDataSetChanged()
            return items.size
        }

        var bestOverlap = 0
        var bestTrailingSkip = 0
        val maxTrailingSkip = lines.size
        for (trailingSkip in 0..maxTrailingSkip) {
            val effectiveSize = lines.size - trailingSkip
            val maxOverlap = minOf(effectiveSize, items.size)
            for (overlap in maxOverlap downTo 1) {
                if (overlap <= bestOverlap) break
                if (regionMatches(effectiveSize - overlap, items, overlap)) {
                    bestOverlap = overlap
                    bestTrailingSkip = trailingSkip
                    break
                }
            }
            if (bestOverlap == items.size) break
        }

        var acksChanged = false
        val localStart = lines.size - bestTrailingSkip - bestOverlap
        for (i in 0 until bestOverlap) {
            val local = lines[localStart + i]
            if (local.kind == ChatLineKind.SELF && !local.selfServerAcked) {
                local.selfServerAcked = true
                acksChanged = true
            }
        }

        val toAdd = items.subList(bestOverlap, items.size)
        if (toAdd.isEmpty()) {
            if (acksChanged) notifyDataSetChanged()
            return 0
        }
        lines.addAll(toAdd)
        notifyDataSetChanged()
        return toAdd.size
    }

    private fun regionMatches(localStart: Int, items: List<ChatLine>, length: Int): Boolean {
        for (i in 0 until length) {
            val a = lines[localStart + i]
            val b = items[i]
            if (a.kind != b.kind || a.text != b.text) return false
        }
        return true
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
                tv.text = line.text
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
                tv.text = line.text
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
