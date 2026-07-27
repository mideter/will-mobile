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
import java.util.ArrayDeque


sealed class ChatLine {
    abstract val text: String

    data class Self(override val text: String, var selfServerAcked: Boolean = false) : ChatLine()

    data class Peer(override val text: String, val authorName: String = "") : ChatLine()
}


class ChatListAdapter(private val context: Context) : BaseAdapter() {

    private val lines = ArrayList<ChatLine>()
    private val inflater = LayoutInflater.from(context)

    /** Позиции своих сообщений, ожидающих ack сервера (FIFO, как кадры на сервере). */
    private val pendingSelfAckPositions = ArrayDeque<Int>()

    fun append(line: ChatLine) {
        lines.add(line)
        if (line is ChatLine.Self && !line.selfServerAcked) {
            pendingSelfAckPositions.addLast(lines.size - 1)
        }
        notifyDataSetChanged()
    }

    fun clear() {
        pendingSelfAckPositions.clear()
        if (lines.isEmpty()) return
        lines.clear()
        notifyDataSetChanged()
    }

    /** Сброс очереди ack без очистки списка (reconnect / новый цикл сессии). */
    fun clearPendingSelfAcks() {
        pendingSelfAckPositions.clear()
    }

    /** Подтверждение следующего своего сообщения в порядке отправки. */
    fun confirmNextSelfAck() {
        val position = pendingSelfAckPositions.pollFirst() ?: return
        markSelfServerAckedAt(position)
    }

    /**
     * Подставляет историю с сервера без мигания: общий суффикс списка с префиксом
     * [items] помечает ack и не дублирует; остальное дописывает.
     * @return сколько строк добавлено
     */
    fun applyHistory(items: List<ChatLine>): Int {
        if (items.isEmpty()) return 0
        if (lines.isEmpty()) {
            lines.addAll(items)
            notifyDataSetChanged()
            return items.size
        }

        var overlap = 0
        val maxOverlap = minOf(lines.size, items.size)
        for (o in maxOverlap downTo 1) {
            if (regionMatches(lines.size - o, items, o)) {
                overlap = o
                break
            }
        }

        var acksChanged = false
        for (i in 0 until overlap) {
            val local = lines[lines.size - overlap + i]
            if (local is ChatLine.Self && !local.selfServerAcked) {
                local.selfServerAcked = true
                acksChanged = true
            }
        }

        val toAdd = items.subList(overlap, items.size)
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

            val same = when {
                a is ChatLine.Self && b is ChatLine.Self ->
                    a.text == b.text
                a is ChatLine.Peer && b is ChatLine.Peer ->
                    a.text == b.text && a.authorName == b.authorName
                else -> false
            }

            if (!same) return false
        }
        return true
    }

    private fun markSelfServerAckedAt(position: Int) {
        if (position < 0 || position >= lines.size) return
        val line = lines[position] as? ChatLine.Self ?: return
        if (line.selfServerAcked) return
        line.selfServerAcked = true
        notifyDataSetChanged()
    }

    override fun getCount(): Int = lines.size

    override fun getItem(position: Int): ChatLine = lines[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.item_chat_line, parent, false)
        val author = view.findViewById<TextView>(R.id.chatLineAuthor)
        val tv = view.findViewById<TextView>(R.id.chatLineText)
        val icon = view.findViewById<ImageView>(R.id.serverReceiptIcon)
        val line = lines[position]

        author.visibility = View.GONE
        icon.visibility = View.GONE
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        icon.contentDescription = null

        when (line) {
            is ChatLine.Self -> {
                tv.text = line.text
                tv.gravity = Gravity.END
                tv.setTextColor(context.getColor(R.color.will_ink))
                view.background = rowDrawable(R.color.will_row)
                // INVISIBLE, не GONE: место под галочку сразу, без сдвига текста при ack.
                if (line.selfServerAcked) {
                    icon.visibility = View.VISIBLE
                    icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    icon.contentDescription = context.getString(R.string.chat_server_receipt_cd)
                } else {
                    icon.visibility = View.INVISIBLE
                }
            }
            is ChatLine.Peer -> {
                tv.text = line.text
                tv.gravity = Gravity.START
                tv.setTextColor(context.getColor(R.color.will_ink))
                view.background = rowDrawable(R.color.will_row)
                if (shouldShowPeerAuthor(position, line)) {
                    author.text = line.authorName.ifEmpty { "peer" }
                    author.visibility = View.VISIBLE
                }
            }
        }
        return view
    }

    /** Подпись автора только у первого сообщения в серии от того же peer. */
    private fun shouldShowPeerAuthor(position: Int, line: ChatLine.Peer): Boolean {
        if (position == 0) return true
        val prev = lines[position - 1] as? ChatLine.Peer ?: return true
        return prev.authorName != line.authorName
    }

    private fun rowDrawable(colorRes: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(context.getColor(colorRes))
        return d
    }
}
