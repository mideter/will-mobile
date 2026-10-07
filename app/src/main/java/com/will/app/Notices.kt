package com.will.app

/**
 * Уведомления сервера по-русски. Сервер говорит по-английски — так же, как консоль; здесь
 * переводятся те, что может увидеть человек. Незнакомое показывается как есть.
 */
object Notices {

    private val exact = mapOf(
        "supplication sent" to "Просьба отправлена",
        "invalid soul name" to "Неверное имя",
        "unknown soul name" to "Такого имени нет",
        "no such room" to "Такой комнаты нет",
        "you do not dwell in that abode" to "Вы не обитаете в этой Обители",
        "you do not enter that room" to "Вам не открыта эта комната",
        "one writes in a room: /room Келья" to "Пишут в комнатах: войдите в Келью",
        "only the host writes in his abode" to "В Обители пишет только хозяин",
        "in a tie the novice fulfils behests: /done <number> [report]" to "Послушник выполняет задание, нажав на него",
        "enter the room of the tie first: /room <name>" to "Сначала войдите в комнату Тренера или ученика",
        "unknown behest" to "Такого задания нет",
        "only the testator wills a training" to "Тренировку задаёт Тренер",
        "invalid training" to "Тренировка составлена неверно",
        "a plain behest is fulfilled without exercises" to "Это задание выполняют без упражнений",
        "unknown dweller kind" to "Неизвестный статус обитателя",
        "unknown supplication" to "Такого прошения нет",
        "cannot supplicate oneself" to "Нельзя просить самого себя",
        "obedience already exists for this pair" to "Вы уже связаны",
        "pending supplication already exists for this pair" to "Просьба уже отправлена и ждёт ответа",
        "one supplicates only a dweller of one's abode" to "Просить можно только того, кто обитает у вас",
        "one hears only a dweller of one's abode" to "Он ещё не принял вас в свою Обитель",
        "a tie binds only those who dwell in each other's abodes" to "Тренером можно стать только тому, кто принял вас, а вы — его",
        "he already dwells in this abode" to "Он уже обитает у вас",
        "he does not dwell in this abode" to "Он не обитает у вас",
        "the host is not a dweller of his own abode" to "Себя принимать не нужно",
        "behest is already executed" to "Задание уже выполнено",
        "one fulfils a behest only in the tie one contemplates" to "Выполнять можно, находясь в комнате Тренера",
        "one wills only in the tie one contemplates" to "Задавать можно, находясь в комнате ученика",
        "one says only in one's cell" to "Записи пишут в Келье",
        "not the novice of this behest" to "Это задание не вам",
        "one arranges rooms only in one's upper room" to "Комнаты переносят в Горнице",
        "one regards dwellers only in one's upper room" to "Статус обитателя меняют в Горнице",
        "one admits only standing in gates" to "Впускают, стоя во Вратах",
        "one keeps the gates only by one's kind" to "Ваш статус в этой Обители не позволяет впускать в эти Врата",
        "he does not stand at the gates" to "Его уже нет у Врат",
        "one awaits one's birth" to "Вы ещё не рождены: ждите",
        "the birth room holds one at a time" to "В Родильной уже кто-то есть: там бывает только один",
        "no such unborn" to "Такого нерождённого нет",
        "one bears only standing in a birth room" to "Рождают, стоя в Родильной",
        "one is not one's own father" to "Нельзя быть отцом самому себе",
        "one does not choose a descendant by spirit as one's father" to "Нельзя избрать отцом своего духовного потомка",
        "the father by flesh is ever at least a neighbour" to "Отец по плоти — всегда не ниже ближнего",
        "the father by spirit is ever a friend" to "Отец по духу — всегда друг",
        "the father by spirit is not yet open" to "Отец по духу пока не открыт",
        "Saying must be non-empty" to "Пустое сообщение",
        "Saying exceeds MaxBodyLength" to "Сообщение слишком длинное",
    )

    private val patterns = listOf(
        Regex("behest (\\d+) fulfilled") to { _: MatchResult -> "Задание выполнено" },
        Regex("training (\\d+) willed") to { _: MatchResult -> "Тренировка задана" },
        Regex("supplication of (.+) rejected") to { m: MatchResult -> "Просьба ${m.groupValues[1]} отклонена" },
        Regex("(.+) let (.+) in as an acquaintance") to { m: MatchResult ->
            "${m.groupValues[1]} впустил ${m.groupValues[2]} знакомым"
        },
        Regex("(.+) admitted as an acquaintance") to { m: MatchResult -> "${m.groupValues[1]} принят знакомым" },
        Regex("(.+) regarded anew") to { m: MatchResult -> "Статус ${m.groupValues[1]} изменён" },
        Regex("(.+) is now in the outer part") to { m: MatchResult -> "«${m.groupValues[1]}» — во внешней части" },
        Regex("(.+) born") to { m: MatchResult -> "Родился ${m.groupValues[1]}" },
        Regex("(.+) is now your father by spirit") to { m: MatchResult -> "${m.groupValues[1]} — ваш отец по духу" },
        Regex("(.+) chose you as his father by spirit") to { m: MatchResult -> "${m.groupValues[1]} избрал вас отцом по духу" },
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
