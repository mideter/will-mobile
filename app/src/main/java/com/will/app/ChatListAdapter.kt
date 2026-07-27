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
     * Подставляет историю с сервера без мигания.
     * Ищет самый длинный суффикс локального списка (без хвостовых неподтверждённых Self)
     * как непрерывный фрагмент [items] (не только с начала), помечает ack и дописывает хвост.
     * При отсутствии overlap — ресинк matchable-части из истории, сохраняя локальные unacked.
     * @return сколько строк добавлено (после ресинка — дельта размера списка)
     */
    fun applyHistory(items: List<ChatLine>): Int {
        if (items.isEmpty()) return 0
        if (lines.isEmpty()) {
            lines.addAll(items)
            notifyDataSetChanged()
            return items.size
        }

        val trailingUnacked = countTrailingUnackedSelf()
        val matchableLen = lines.size - trailingUnacked

        var matchLen = 0
        var matchItemsEnd = 0
        if (matchableLen > 0) {
            val maxO = minOf(matchableLen, items.size)
            outer@ for (o in maxO downTo 1) {
                val localStart = matchableLen - o
                for (itemsStart in (items.size - o) downTo 0) {
                    if (regionMatchesAt(localStart, items, itemsStart, o)) {
                        matchLen = o
                        matchItemsEnd = itemsStart + o
                        break@outer
                    }
                }
            }
        }

        if (matchLen == 0) {
            // Нет общего фрагмента (или только локальные unacked) — ресинк из snapshot.
            return resyncWithHistory(items, trailingUnacked)
        }

        var acksChanged = false
        val localMatchStart = matchableLen - matchLen
        for (i in 0 until matchLen) {
            val local = lines[localMatchStart + i]
            if (local is ChatLine.Self && !local.selfServerAcked) {
                local.selfServerAcked = true
                acksChanged = true
            }
        }

        var itemsCursor = matchItemsEnd
        var trailIdx = matchableLen
        while (trailIdx < lines.size && itemsCursor < items.size) {
            if (!contentEquals(lines[trailIdx], items[itemsCursor])) break
            val local = lines[trailIdx]
            if (local is ChatLine.Self && !local.selfServerAcked) {
                local.selfServerAcked = true
                acksChanged = true
            }
            trailIdx++
            itemsCursor++
        }

        val toAdd = items.subList(itemsCursor, items.size)
        if (toAdd.isEmpty()) {
            if (acksChanged) notifyDataSetChanged()
            return 0
        }
        lines.addAll(toAdd)
        notifyDataSetChanged()
        return toAdd.size
    }

    private fun countTrailingUnackedSelf(): Int {
        var n = 0
        while (n < lines.size) {
            val line = lines[lines.size - 1 - n]
            if (line is ChatLine.Self && !line.selfServerAcked) n++
            else break
        }
        return n
    }

    /** Нет общего фрагмента — заменить подтверждённую часть историей, сохранить локальные unacked. */
    private fun resyncWithHistory(items: List<ChatLine>, trailingUnacked: Int): Int {
        val kept = ArrayList<ChatLine>(trailingUnacked)
        for (i in lines.size - trailingUnacked until lines.size) {
            kept.add(lines[i])
        }

        var skipKept = 0
        val maxK = minOf(kept.size, items.size)
        for (k in maxK downTo 1) {
            if (regionEquals(kept, 0, items, items.size - k, k)) {
                skipKept = k
                break
            }
        }

        val oldSize = lines.size
        pendingSelfAckPositions.clear()
        lines.clear()
        lines.addAll(items)
        for (i in skipKept until kept.size) {
            val line = kept[i]
            lines.add(line)
            if (line is ChatLine.Self && !line.selfServerAcked) {
                pendingSelfAckPositions.addLast(lines.size - 1)
            }
        }
        notifyDataSetChanged()
        return lines.size - oldSize
    }

    private fun regionMatchesAt(
        localStart: Int,
        items: List<ChatLine>,
        itemsStart: Int,
        length: Int,
    ): Boolean {
        for (i in 0 until length) {
            if (!contentEquals(lines[localStart + i], items[itemsStart + i])) return false
        }
        return true
    }

    private fun regionEquals(
        a: List<ChatLine>,
        aStart: Int,
        b: List<ChatLine>,
        bStart: Int,
        length: Int,
    ): Boolean {
        for (i in 0 until length) {
            if (!contentEquals(a[aStart + i], b[bStart + i])) return false
        }
        return true
    }

    private fun contentEquals(a: ChatLine, b: ChatLine): Boolean = when {
        a is ChatLine.Self && b is ChatLine.Self -> a.text == b.text
        a is ChatLine.Peer && b is ChatLine.Peer ->
            a.text == b.text && a.authorName == b.authorName
        else -> false
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
