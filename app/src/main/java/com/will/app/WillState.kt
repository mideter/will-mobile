package com.will.app

import will.v1.MessengerOuterClass.Word

/** Связь с сервером. */
enum class Connection { Connecting, Ready, Reconnecting }

/**
 * Ответ сервера, которого ждёт окно: просьбы нет, она отправлена и ждёт или исполнена.
 * Сервер отвечает на неё ровно одним уведомлением; отказ возвращает к `None`.
 */
enum class Reply { None, Pending, Granted }

/** Комната в списке Обители: в какой она части и что отражает — переносят только Кельи ([Aspect.Words]). */
data class RoomItem(val name: String, val outer: Boolean, val aspect: Aspect = Aspect.Words)

/** Неисполненное Веление в обзоре хозяина. */
data class Waiting(val room: String, val behestId: Long, val body: String)

/** Подход: вес в граммах (0 — свой вес), повторы и отдых перед ним, в секундах. */
data class ApproachItem(val weightGrams: Int, val repetitions: Int, val restSeconds: Int = 0)

/** Упражнение: свободное название и подходы. */
data class ExerciseItem(val name: String, val approaches: List<ApproachItem>)

/**
 * Сделанный подход тренировки: какой (номера с нуля), с каким весом и сколько повторов,
 * начат и закончен когда (наносекунды от эпохи, по часам сервера).
 */
data class EffortItem(
    val exercise: Int,
    val approach: Int,
    val weightGrams: Int,
    val repetitions: Int,
    val begunAtNs: Long,
    val finishedAtNs: Long,
)

/** Подход, который Послушник выполняет сейчас. */
data class UnderwayItem(val behestId: Long, val exercise: Int, val approach: Int, val begunAtNs: Long)

/** Слово в ленте комнаты. */
data class WordItem(
    val id: Long,
    val author: String,
    val body: String,
    val mine: Boolean,
    val kind: Word.Kind,
    /** Для Дела — номер Веления, которое оно исполняет. */
    val behestId: Long,
    /** У тренировки — велённые упражнения; у Дела, исполнившего её, — сделанные. */
    val exercises: List<ExerciseItem> = emptyList(),
    /** У тренировки — подходы, сделанные по ходу. */
    val efforts: List<EffortItem> = emptyList(),
)

/** Род обитателя. */
enum class Kind { Acquaintance, Neighbour, Friend }

/** Как обитатель стоит к хозяину в тренерстве; Узы бывают в обе стороны, поэтому отношений может быть несколько. */
enum class Standing { Asked, Asks, Trainer, Novice }

data class Person(val name: String, val kind: Kind, val standings: Set<Standing> = emptySet())

/** Что отражает комната. */
enum class Aspect { Words, Threshold, Dwellers }

/** Чадо по духу и его отец — звено духовной линии. */
data class Descent(val name: String, val father: String)

/** Что перед глазами: Обитель (своя, когда [host] пуст) или комната в ней. */
sealed interface View {
    val host: String

    data class Abode(
        override val host: String,
        val rooms: List<RoomItem> = emptyList(),
        /** Что ожидает моего внимания: задания, где я Послушник. */
        val owed: List<Waiting> = emptyList(),
        /** Что я задал другим: задания, где я Тренер. */
        val awaited: List<Waiting> = emptyList(),
    ) : View

    /**
     * Комната. [aspect] — что она отражает: слова (Слово, Узы), порог (Врата) или
     * обитателей (Горница). [writable] — здесь пишут: своё Слово; Ведение — у Тренера.
     * Во Вратах — [gatesOpen], [keeping] (я их хозяин) и, хозяину, [waiting] и метки
     * нерождённых [unborn]; в Горнице — [people].
     */
    data class Room(
        override val host: String,
        val room: String,
        val aspect: Aspect = Aspect.Words,
        val writable: Boolean = false,
        val words: List<WordItem> = emptyList(),
        val gatesOpen: Boolean = false,
        val keeping: Boolean = false,
        val waiting: List<String> = emptyList(),
        val people: List<Person> = emptyList(),
        val unborn: List<Long> = emptyList(),
        /** В комнате Уз — начатые подходы её тренировок, по номеру тренировки: в каждой не больше одного. */
        val underways: Map<Long, UnderwayItem> = emptyMap(),
        /** В Горнице — все комнаты Обители с их частями: здесь переносят Кельи. */
        val rooms: List<RoomItem> = emptyList(),
    ) : View
}

/** Тренировка, которую выполняют: где она — Обитель [host] и комната [room] — и её номер. */
data class Workout(val host: String, val room: String, val behestId: Long)

data class WillState(
    val connection: Connection = Connection.Connecting,
    val ownName: String = "",
    val view: View? = null,
    /** Слова и списки нового вида ещё идут. */
    val loading: Boolean = true,
    val dwellers: List<Person> = emptyList(),
    val dwellings: List<Person> = emptyList(),
    val supplications: List<String> = emptyList(),
    /** Тело ещё не рождено: метка, по которой его видят во Вратах. */
    val unbornMark: Long? = null,
    /** Моя духовная линия, поколение за поколением. */
    val lineage: List<Descent> = emptyList(),
    /** Что с тренировкой, отправленной из редактора. */
    val willing: Reply = Reply.None,
    /** Что с заданием, отправленным на выполнение. */
    val fulfilling: Reply = Reply.None,
    /** Подход, к которому приступили: `Pending`, пока сервер не сказал, что он идёт, или почему нет. */
    val beginning: Reply = Reply.None,
    /** Выбор Послушника в тренировке: его видят и экран выполнения, и уведомление. */
    val choice: Choice = Choice(),
    /** Тренировка, которую выполняют, — с первого подхода до завершения; её ведёт [TrainingService]. */
    val workout: Workout? = null,
)
