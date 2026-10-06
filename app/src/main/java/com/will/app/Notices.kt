package com.will.app

/**
 * Уведомления сервера по-русски. Сервер говорит по-английски — так же, как консоль; здесь
 * переводятся те, что может увидеть человек. Незнакомое показывается как есть.
 */
object Notices {

    private val exact = mapOf(
        "supplication sent" to "Прошение подано",
        "invalid soul name" to "Неверное имя",
        "unknown soul name" to "Такого имени нет",
        "no such room" to "Такой комнаты нет",
        "you do not dwell in that abode" to "Вы не обитаете в этой Обители",
        "you do not enter that room" to "Вам не открыта эта комната",
        "one writes in a room: /room Келья" to "Пишут в комнатах: войдите в Келью",
        "only the host writes in his abode" to "В Обители пишет только хозяин",
        "in a tie the novice fulfils behests: /done <number> [report]" to "Послушник исполняет Веления кнопкой «Исполнить»",
        "enter the room of the tie first: /room <name>" to "Сначала войдите в комнату Уз",
        "unknown behest" to "Такого Веления нет",
        "unknown dweller kind" to "Неизвестный род обитателя",
        "unknown supplication" to "Такого прошения нет",
        "cannot supplicate oneself" to "Нельзя просить самого себя",
        "obedience already exists for this pair" to "Узы уже есть",
        "pending supplication already exists for this pair" to "Прошение уже подано и ждёт ответа",
        "one supplicates only a dweller of one's abode" to "Просить можно только того, кто обитает у вас",
        "one hears only a dweller of one's abode" to "Он ещё не принял вас в свою Обитель",
        "a tie binds only those who dwell in each other's abodes" to "Узы — только между теми, кто принял друг друга",
        "he already dwells in this abode" to "Он уже обитает у вас",
        "he does not dwell in this abode" to "Он не обитает у вас",
        "the host is not a dweller of his own abode" to "Себя принимать не нужно",
        "behest is already executed" to "Веление уже исполнено",
        "one fulfils a behest only in the tie one contemplates" to "Исполнять можно, находясь в комнате Уз",
        "one wills only in the tie one contemplates" to "Велеть можно, находясь в комнате Уз",
        "one says only in one's cell" to "Записи пишут в Келье",
        "not the novice of this behest" to "Это Веление не вам",
        "Saying must be non-empty" to "Пустое сообщение",
        "Saying exceeds MaxBodyLength" to "Сообщение слишком длинное",
    )

    private val patterns = listOf(
        Regex("behest (\\d+) fulfilled") to { _: MatchResult -> "Веление исполнено" },
        Regex("supplication of (.+) rejected") to { m: MatchResult -> "Прошение ${m.groupValues[1]} отклонено" },
        Regex("(.+) admitted as an acquaintance") to { m: MatchResult -> "${m.groupValues[1]} принят знакомым" },
        Regex("(.+) regarded anew") to { m: MatchResult -> "Род ${m.groupValues[1]} изменён" },
        Regex("(.+) is now in the outer part") to { m: MatchResult -> "«${m.groupValues[1]}» — во внешней части" },
        Regex("(.+) is now in the inner part") to { m: MatchResult -> "«${m.groupValues[1]}» — во внутренней части" },
    )

    fun ru(message: String): String {
        exact[message]?.let { return it }
        for ((regex, say) in patterns) {
            regex.matchEntire(message)?.let { return say(it) }
        }
        return message
    }
}
