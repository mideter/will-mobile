package com.will.app

import will.v1.MessengerOuterClass.NoticeCode

/**
 * Уведомления сервера по-русски. Сервер говорит кодом ([NoticeCode]), а имена и номера
 * в уведомлении — отдельно ([say]); перевод — по коду, на каждый код свой. Сделанное мной
 * не говорится: его показывает сам экран. Без кода (сбой, не ставший отказом мира) — по
 * английским словам сервера ([ru]), незнакомое — как есть.
 */
object Notices {

    fun say(code: NoticeCode, args: List<String>, message: String): String? {
        fun arg(i: Int) = args.getOrElse(i) { "" }
        return when (code) {
            // Чего не позволяет мир.
            NoticeCode.NOTICE_ALREADY_DWELLS -> "Вы уже знаете этого человека"
            NoticeCode.NOTICE_NOT_AT_GATES -> "Уже не у Врат"
            // Номер берут из списка, и список сразу говорит, что его уже нет: экран показывает сам.
            NoticeCode.NOTICE_NO_SUCH_UNBORN -> null
            NoticeCode.NOTICE_DOES_NOT_DWELL -> "Вы не знаете этого человека"
            NoticeCode.NOTICE_DOES_NOT_ENTER_ROOM -> "Вам не открыта эта комната"
            NoticeCode.NOTICE_STAND_IN_CELL -> "Это делают в Слове, Ведении или Послушании"
            NoticeCode.NOTICE_STAND_AT_HELM -> "Это делают в Кормчей"
            NoticeCode.NOTICE_STAND_AT_GATES -> "Это делают, стоя во Вратах"
            NoticeCode.NOTICE_ONLY_HOST_ACTS -> "Здесь действует только хозяин"
            NoticeCode.NOTICE_PASS_THROUGH_GATES -> "Туда идут через Врата"
            NoticeCode.NOTICE_TELL_WHAT_WAS_DONE -> "Сначала скажите, что сделано в подходе"
            NoticeCode.NOTICE_FATHER_BY_FLESH_NEIGHBOUR -> "Отец по плоти — всегда не ниже ближнего"
            NoticeCode.NOTICE_FATHER_BY_SPIRIT_FRIEND -> "Отец по духу — всегда друг"
            NoticeCode.NOTICE_HEAR_ONLY_DWELLER -> "Просить можно только того, кто знает вас"
            NoticeCode.NOTICE_ALREADY_TIED -> "Вы уже связаны"
            NoticeCode.NOTICE_TIE_ONLY_MUTUAL_DWELLERS -> "Узы — только когда вы знаете друг друга"
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
            NoticeCode.NOTICE_YOU_DO_NOT_DWELL -> "Здесь вас не знают"
            NoticeCode.NOTICE_YOU_DO_NOT_ENTER -> "Вам не открыта эта комната"
            NoticeCode.NOTICE_UNKNOWN_BEHEST -> "Такого задания нет"
            NoticeCode.NOTICE_UNKNOWN_KIND -> "Неизвестный статус"
            NoticeCode.NOTICE_INVALID_TRAINING -> "Тренировка составлена неверно"
            NoticeCode.NOTICE_UNKNOWN_TRAINING -> "Такой тренировки нет"
            NoticeCode.NOTICE_FATHER_BY_SPIRIT_CLOSED -> "Отец по духу пока не открыт"
            NoticeCode.NOTICE_AWAITS_BIRTH -> "Имени пока нет — назовите свой номер тому, кто уже здесь"
            // Что сделано мной — видно на экране, поэтому не говорится.
            NoticeCode.NOTICE_SUPPLICATION_SENT,
            NoticeCode.NOTICE_BEHEST_FULFILLED,
            NoticeCode.NOTICE_ADMITTED,
            NoticeCode.NOTICE_REGARDED,
            NoticeCode.NOTICE_ARRANGED_OUTER,
            NoticeCode.NOTICE_ARRANGED_INNER,
            NoticeCode.NOTICE_TRAINING_WILLED,
            NoticeCode.NOTICE_BORN,
            NoticeCode.NOTICE_SUPPLICATION_REJECTED -> null
            // Что сделано другим.
            NoticeCode.NOTICE_SUPPLICATION_DECLINED -> "Просьба к ${arg(0)} отклонена"
            // Без кода — по словам сервера.
            NoticeCode.NOTICE_UNSPECIFIED, NoticeCode.UNRECOGNIZED -> ru(message)
        }
    }

    private val exact = mapOf(
        "pending supplication already exists for this pair" to "Просьба уже отправлена и ждёт ответа",
        "the host is not a dweller of his own abode" to "Себя принимать не нужно",
        "Saying must be non-empty" to "Пустое сообщение",
        "Saying exceeds MaxBodyLength" to "Сообщение слишком длинное",
    )

    fun ru(message: String): String = exact[message] ?: message
}
