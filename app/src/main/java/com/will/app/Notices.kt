package com.will.app

import will.v1.MessengerOuterClass.NoticeCode

/**
 * Уведомления сервера по-русски. Сервер говорит кодом ([NoticeCode]), а имена и номера
 * в уведомлении — отдельно ([say]); перевод — по коду, на каждый код свой. Без кода
 * (непредвиденное или старый сервер) — по английским словам сервера ([ru]), незнакомое —
 * как есть.
 */
object Notices {

    /** Уведомление по его коду; имена и номера — из [args], по порядку, что называет код. */
    fun say(code: NoticeCode, args: List<String>, message: String): String {
        fun arg(i: Int) = args.getOrElse(i) { "" }
        return when (code) {
            // Чего не позволяет мир.
            NoticeCode.NOTICE_ADMIT_ONLY_AT_GATES -> "Впускают, стоя во Вратах"
            NoticeCode.NOTICE_KEEP_GATES_BY_KIND -> "Ваш статус в этой Обители не позволяет впускать в эти Врата"
            NoticeCode.NOTICE_ALREADY_DWELLS -> "Он уже обитает у вас"
            NoticeCode.NOTICE_NOT_AT_GATES -> "Его уже нет у Врат"
            NoticeCode.NOTICE_BEAR_ONLY_AT_GATES -> "Рождают, стоя во Вратах"
            NoticeCode.NOTICE_BEAR_ONLY_AT_KEPT_GATES -> "Рождает тот, кто может впускать в эти Врата"
            NoticeCode.NOTICE_NO_SUCH_UNBORN -> "Такого нерождённого нет"
            NoticeCode.NOTICE_DOES_NOT_DWELL -> "Он не обитает у вас"
            NoticeCode.NOTICE_DOES_NOT_ENTER_ROOM -> "Вам не открыта эта комната"
            NoticeCode.NOTICE_REGARD_ONLY_IN_UPPER_ROOM -> "Статус обитателя меняют в Горнице"
            NoticeCode.NOTICE_ARRANGE_ONLY_IN_UPPER_ROOM -> "Комнаты переносят в Горнице"
            NoticeCode.NOTICE_FATHER_BY_FLESH_NEIGHBOUR -> "Отец по плоти — всегда не ниже ближнего"
            NoticeCode.NOTICE_FATHER_BY_SPIRIT_FRIEND -> "Отец по духу — всегда друг"
            NoticeCode.NOTICE_SAY_ONLY_IN_WORD -> "Здесь не пишут: пишут в своём Слове или в комнате ученика"
            NoticeCode.NOTICE_CANNOT_SUPPLICATE_ONESELF -> "Нельзя просить самого себя"
            NoticeCode.NOTICE_SUPPLICATE_ONLY_DWELLER -> "Просить можно только того, кто обитает у вас"
            NoticeCode.NOTICE_HEAR_ONLY_DWELLER -> "Он ещё не принял вас в свою Обитель"
            NoticeCode.NOTICE_ALREADY_TIED -> "Вы уже связаны"
            NoticeCode.NOTICE_TIE_ONLY_MUTUAL_DWELLERS -> "Тренером можно стать только тому, кто принял вас, а вы — его"
            NoticeCode.NOTICE_UNKNOWN_SUPPLICATION -> "Такой просьбы нет"
            NoticeCode.NOTICE_WILL_ONLY_IN_CONTEMPLATED_TIE -> "Задавать можно, находясь в комнате ученика"
            NoticeCode.NOTICE_ONLY_TESTATOR_WILLS -> "Задаёт только Тренер"
            NoticeCode.NOTICE_FULFIL_ONLY_IN_CONTEMPLATED_TIE -> "Выполнять можно, находясь в комнате Тренера"
            NoticeCode.NOTICE_ONLY_NOVICE_FULFILS -> "Выполняет только ученик"
            NoticeCode.NOTICE_ALREADY_FULFILLED -> "Задание уже выполнено"
            NoticeCode.NOTICE_PLAIN_BEHEST_WITHOUT_EXERCISES -> "Это задание выполняют без упражнений"
            NoticeCode.NOTICE_TRAIN_ONLY_IN_CONTEMPLATED_TIE -> "Тренировку выполняют в комнате Тренера"
            NoticeCode.NOTICE_ONLY_NOVICE_TRAINS -> "Тренировку выполняет только ученик"
            NoticeCode.NOTICE_NO_SUCH_EXERCISE -> "Такого упражнения нет"
            NoticeCode.NOTICE_NO_SUCH_APPROACH -> "Такого подхода нет"
            NoticeCode.NOTICE_APPROACH_ALREADY_DONE -> "Этот подход уже сделан"
            NoticeCode.NOTICE_APPROACHES_IN_ORDER -> "Подходы упражнения выполняют по порядку"
            NoticeCode.NOTICE_ANOTHER_APPROACH_UNDERWAY -> "Уже идёт другой подход"
            NoticeCode.NOTICE_NO_APPROACH_UNDERWAY -> "Сейчас не идёт ни один подход"
            NoticeCode.NOTICE_FINISH_APPROACH_FIRST -> "Сначала завершите идущий подход"
            NoticeCode.NOTICE_NOT_OWN_FATHER -> "Нельзя быть отцом самому себе"
            NoticeCode.NOTICE_NOT_DESCENDANT_AS_FATHER -> "Нельзя избрать отцом своего духовного потомка"
            // Чего не позволяет сервер.
            NoticeCode.NOTICE_INVALID_NAME -> "Неверное имя"
            NoticeCode.NOTICE_UNKNOWN_NAME -> "Такого имени нет"
            NoticeCode.NOTICE_NO_SUCH_ROOM -> "Такой комнаты нет"
            NoticeCode.NOTICE_YOU_DO_NOT_DWELL -> "Вы не обитаете в этой Обители"
            NoticeCode.NOTICE_YOU_DO_NOT_ENTER -> "Вам не открыта эта комната"
            NoticeCode.NOTICE_UNKNOWN_BEHEST -> "Такого задания нет"
            NoticeCode.NOTICE_UNKNOWN_KIND -> "Неизвестный статус обитателя"
            NoticeCode.NOTICE_INVALID_TRAINING -> "Тренировка составлена неверно"
            NoticeCode.NOTICE_UNKNOWN_TRAINING -> "Такой тренировки нет"
            NoticeCode.NOTICE_FATHER_BY_SPIRIT_CLOSED -> "Отец по духу пока не открыт"
            NoticeCode.NOTICE_AWAITS_BIRTH -> "Вы ещё не рождены: ждите"
            // Что сделано.
            NoticeCode.NOTICE_SUPPLICATION_SENT -> "Просьба отправлена"
            NoticeCode.NOTICE_BEHEST_FULFILLED -> "Задание выполнено"
            NoticeCode.NOTICE_ADMITTED -> "${arg(0)} принят знакомым"
            NoticeCode.NOTICE_LET_IN -> "${arg(0)} впустил ${arg(1)} знакомым"
            NoticeCode.NOTICE_REGARDED -> "Статус ${arg(0)} изменён"
            NoticeCode.NOTICE_ARRANGED_OUTER -> "«${arg(0)}» — во внешней части"
            NoticeCode.NOTICE_ARRANGED_INNER -> "«${arg(0)}» — во внутренней части"
            NoticeCode.NOTICE_TRAINING_WILLED -> "Тренировка задана"
            NoticeCode.NOTICE_BORN -> "Родился ${arg(0)}"
            NoticeCode.NOTICE_SUPPLICATION_REJECTED -> "Просьба ${arg(0)} отклонена"
            // Без кода — по словам сервера.
            NoticeCode.NOTICE_UNSPECIFIED, NoticeCode.UNRECOGNIZED -> ru(message)
        }
    }

    private val exact = mapOf(
        "supplication sent" to "Просьба отправлена",
        "invalid soul name" to "Неверное имя",
        "unknown soul name" to "Такого имени нет",
        "no such room" to "Такой комнаты нет",
        "you do not dwell in that abode" to "Вы не обитаете в этой Обители",
        "you do not enter that room" to "Вам не открыта эта комната",
        "one writes in a room: /room Слово" to "Пишут в комнатах: войдите в Слово",
        "only the host writes in his abode" to "В Обители пишет только хозяин",
        "one does not write here" to "Здесь не пишут",
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
        "one says only in one's cell" to "Записи пишут в Слове",
        "not the novice of this behest" to "Это задание не вам",
        "one arranges rooms only in one's upper room" to "Комнаты переносят в Горнице",
        "one regards dwellers only in one's upper room" to "Статус обитателя меняют в Горнице",
        "one admits only standing in gates" to "Впускают, стоя во Вратах",
        "one keeps the gates only by one's kind" to "Ваш статус в этой Обители не позволяет впускать в эти Врата",
        "he does not stand at the gates" to "Его уже нет у Врат",
        "one awaits one's birth" to "Вы ещё не рождены: ждите",
        "no such unborn" to "Такого нерождённого нет",
        "one bears only standing in gates" to "Рождают, стоя во Вратах",
        "one bears only at gates one keeps" to "Рождает тот, кто может впускать в эти Врата",
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
