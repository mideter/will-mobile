package com.will.app

import will.v1.MessengerOuterClass.Word

enum class Connection { Connecting, Ready, Reconnecting }

/**
 * Ответ сервера, которого ждёт окно: просьбы нет, она отправлена и ждёт или исполнена.
 * Сервер отвечает на неё ровно одним уведомлением; отказ возвращает к `None`.
 */
enum class Reply { None, Pending, Granted }

/** Комната в списке Обители: в какой она части и что отражает — переносят только Кельи ([Aspect.Words]). */
data class RoomItem(val name: String, val outer: Boolean, val aspect: Aspect = Aspect.Words)

/** Всплывающее: [text] — что сказано; [room] — комната своей Обители, куда оно ведёт, если ведёт. */
data class Notice(val text: String, val room: String? = null)

/**
 * Неисполненное Веление в обзоре хозяина: [word] — оно само; [counterpart] — другой в Узах: Тренер,
 * если велено мне, ученик, если велел я; [underway] — подход, если он идёт в тренировке.
 */
data class Waiting(val room: String, val word: WordItem, val counterpart: String, val underway: UnderwayItem? = null)

/** Подход: вес в граммах (0 — свой вес), повторы и отдых перед ним, в секундах. */
data class ApproachItem(val weightGrams: Int, val repetitions: Int, val restSeconds: Int = 0)

data class ExerciseItem(val name: String, val approaches: List<ApproachItem>, val instruction: String = "")

/**
 * Сделанный подход тренировки: какой (номера с нуля), с каким весом и сколько повторов,
 * начат и закончен когда (наносекунды от эпохи, по часам сервера) и как прошёл — [strain]
 * знаков: ни одного — как надо, «!» — с нагрузкой, «!!» — тяжело.
 */
data class EffortItem(
    val exercise: Int,
    val approach: Int,
    val weightGrams: Int,
    val repetitions: Int,
    val begunAtNs: Long,
    val finishedAtNs: Long,
    val strain: Int = 0,
)

/** Заметка ученика к упражнению тренировки (номер с нуля): что сказал и когда, по часам сервера. */
data class RemarkItem(val exercise: Int, val body: String, val saidAtNs: Long)

/** Подход, который Послушник выполняет сейчас; [endedAtNs] — закончен, и ещё не сказано, что сделано. */
data class UnderwayItem(val behestId: Long, val exercise: Int, val approach: Int, val begunAtNs: Long, val endedAtNs: Long? = null)

/**
 * Слово в ленте комнаты. У Дела [behestId] — номер Веления, которое оно исполняет. У
 * тренировки [exercises] — велённые упражнения, у Дела, исполнившего её, — сделанные;
 * [efforts] — подходы тренировки, сделанные по ходу; [remarks] — заметки ученика к её
 * упражнениям, в том порядке, в каком сказаны.
 */
data class WordItem(
    val id: Long,
    val author: String,
    val body: String,
    val mine: Boolean,
    val kind: Word.Kind,
    val behestId: Long,
    val exercises: List<ExerciseItem> = emptyList(),
    val efforts: List<EffortItem> = emptyList(),
    val remarks: List<RemarkItem> = emptyList(),
)

enum class Kind { Acquaintance, Neighbour, Friend }

/** Как обитатель стоит к хозяину в тренерстве; Узы бывают в обе стороны, поэтому отношений может быть несколько. */
enum class Standing { Asked, Asks, Trainer, Novice }

data class Person(val name: String, val kind: Kind, val standings: Set<Standing> = emptySet())

enum class Aspect { Words, Threshold, Dwellers }

/** Чадо по духу и его отец — звено духовной линии. */
data class Descent(val name: String, val father: String)

/** Что перед глазами: Обитель (своя, когда [host] пуст) или комната в ней. */
sealed interface View {
    val host: String

    /**
     * Обитель. [owed] — что мне выполнить, задания, где я Послушник; [unreviewed] — что выполнено
     * у меня как у Тренера, а я ещё не видел; [awaited] — чего я ожидаю от других.
     */
    data class Abode(
        override val host: String,
        val rooms: List<RoomItem> = emptyList(),
        val owed: List<Waiting> = emptyList(),
        val awaited: List<Waiting> = emptyList(),
        val unreviewed: List<Waiting> = emptyList(),
    ) : View

    /**
     * Комната. [aspect] — что она отражает: слова (Слово, Узы), порог (Врата) или
     * обитателей (Кормчая). [writable] — здесь пишут: своё Слово; Ведение — у Тренера.
     * Во Вратах — [gatesOpen], [keeping] (я их хозяин) и, хозяину, [waiting] и метки
     * нерождённых [unborn]; в Кормчей — [people] и все комнаты Обители с их частями [rooms]:
     * здесь переносят Кельи. В комнате Уз [underways] — начатые подходы её тренировок, по
     * номеру тренировки: в каждой не больше одного.
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
        val underways: Map<Long, UnderwayItem> = emptyMap(),
        val rooms: List<RoomItem> = emptyList(),
    ) : View
}

/** Тренировка, которую выполняют: где она — Обитель [host] и комната [room] — и её номер. */
data class Workout(val host: String, val room: String, val behestId: Long)

/**
 * Всё, из чего рисуются экраны. [loading] — слова и списки нового вида ещё идут;
 * [unbornMark] — тело ещё не рождено, вот его метка. [willing] и [fulfilling] — что с
 * тренировкой из редактора и с заданием, отправленным на выполнение; [beginning] — `Pending`,
 * пока сервер не сказал, что подход идёт, или почему нет; [telling] и [remarking] — так же
 * рассказ о подходе и заметка: до ответа пульт и поле не закрываются, и сказанное не теряется
 * с оборванной связью. [choice] видят и экран выполнения, и
 * уведомление; [workout] — тренировка с первого подхода до завершения, её ведёт [TrainingService].
 */
data class WillState(
    val connection: Connection = Connection.Connecting,
    val ownName: String = "",
    val view: View? = null,
    val loading: Boolean = true,
    val dwellers: List<Person> = emptyList(),
    val dwellings: List<Person> = emptyList(),
    val supplications: List<String> = emptyList(),
    val unbornMark: Long? = null,
    val lineage: List<Descent> = emptyList(),
    val willing: Reply = Reply.None,
    val fulfilling: Reply = Reply.None,
    val beginning: Reply = Reply.None,
    val telling: Reply = Reply.None,
    val remarking: Reply = Reply.None,
    val choice: Choice = Choice(),
    val workout: Workout? = null,
)
