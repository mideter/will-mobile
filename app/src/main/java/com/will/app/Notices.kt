package com.will.app

import will.v1.MessengerOuterClass.NoticeCode

/**
 * Уведомления сервера по-русски. Сервер говорит кодом ([NoticeCode]), а имена и номера
 * в уведомлении — отдельно ([say]); перевод — по коду, на каждый код свой. Без кода
 * (сбой, не ставший отказом мира) — по английским словам сервера ([ru]), незнакомое —
 * как есть.
 */
object Notices {

    fun say(code: NoticeCode, args: List<String>, message: String): String {
        fun arg(i: Int) = args.getOrElse(i) { "" }
        return when (code) {
            // Чего не позволяет мир.
            NoticeCode.NOTICE_ALREADY_DWELLS -> "Он уже обитает у вас"
            NoticeCode.NOTICE_NOT_AT_GATES -> "Его уже нет у Врат"
            NoticeCode.NOTICE_NO_SUCH_UNBORN -> "Такого нерождённого нет"
            NoticeCode.NOTICE_DOES_NOT_DWELL -> "Он не обитает у вас"
            NoticeCode.NOTICE_DOES_NOT_ENTER_ROOM -> "Вам не открыта эта комната"
            NoticeCode.NOTICE_STAND_IN_CELL -> "Это делают в Слове, Ведении или Послушании"
            NoticeCode.NOTICE_STAND_AT_HELM -> "Это делают в Кормчей"
            NoticeCode.NOTICE_STAND_AT_GATES -> "Это делают, стоя во Вратах"
            NoticeCode.NOTICE_ONLY_HOST_ACTS -> "Здесь действует только хозяин"
            NoticeCode.NOTICE_PASS_THROUGH_GATES -> "Туда идут через Врата"
            NoticeCode.NOTICE_TELL_WHAT_WAS_DONE -> "Сначала скажите, что сделано в подходе"
            NoticeCode.NOTICE_FATHER_BY_FLESH_NEIGHBOUR -> "Отец по плоти — всегда не ниже ближнего"
            NoticeCode.NOTICE_FATHER_BY_SPIRIT_FRIEND -> "Отец по духу — всегда друг"
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
        "pending supplication already exists for this pair" to "Просьба уже отправлена и ждёт ответа",
        "the host is not a dweller of his own abode" to "Себя принимать не нужно",
        "Saying must be non-empty" to "Пустое сообщение",
        "Saying exceeds MaxBodyLength" to "Сообщение слишком длинное",
    )

    fun ru(message: String): String = exact[message] ?: message
}
