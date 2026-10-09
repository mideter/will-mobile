package com.will.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import will.v1.MessengerOuterClass.ClientEvent
import will.v1.MessengerOuterClass.DwellerKind
import will.v1.MessengerOuterClass.RoomAspect
import will.v1.MessengerOuterClass.RoomPart
import will.v1.MessengerOuterClass.ServerEvent
import will.v1.MessengerOuterClass.Word
import will.v1.acceptSupplication
import will.v1.admit
import will.v1.approach
import will.v1.arrange
import will.v1.beginApproach
import will.v1.bear
import will.v1.chooseFather
import will.v1.chatMessage
import will.v1.clientEvent
import will.v1.exercise
import will.v1.finishApproach
import will.v1.fulfil
import will.v1.historyRequest
import will.v1.listDwellers
import will.v1.listDwellings
import will.v1.listLineage
import will.v1.listSupplications
import will.v1.regard
import will.v1.rejectSupplication
import will.v1.supplicate
import will.v1.train
import will.v1.turn


/** Связь с сервером. */
enum class Connection { Connecting, Ready, Reconnecting }

/**
 * Ответ сервера, которого ждёт окно: просьбы нет, она отправлена и ждёт или исполнена.
 * Сервер отвечает на неё ровно одним уведомлением; отказ возвращает к `None`.
 */
enum class Reply { None, Pending, Granted }

/** Комната в списке Обители. */
data class RoomItem(val name: String, val outer: Boolean)

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
enum class Aspect { Words, Threshold, Dwellers, Birth }

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
     * Комната. [aspect] — что она отражает: слова (Келья, Узы), порог (Врата) или
     * обитателей (Горница). [writable] — здесь пишут: своя Келья; Ведение — у Тренера.
     * Во Вратах — [gatesOpen], [keeping] (я привратник: хозяин или обитатель, чьему роду
     * открыта их часть) и, привратнику, [waiting]; в Горнице — [people]; в Родильной —
     * метки нерождённых [unborn].
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
        /** В комнате Уз — подход, который Послушник выполняет сейчас. */
        val underway: UnderwayItem? = null,
        /** В своей Горнице — все комнаты Обители с их частями: здесь их переносят. */
        val rooms: List<RoomItem> = emptyList(),
    ) : View
}

data class WillState(
    val connection: Connection = Connection.Connecting,
    val ownName: String = "",
    val view: View? = null,
    /** Слова и списки нового вида ещё идут. */
    val loading: Boolean = true,
    val dwellers: List<Person> = emptyList(),
    val dwellings: List<Person> = emptyList(),
    val supplications: List<String> = emptyList(),
    /** Тело ещё не рождено: метка, по которой его видят в Родильных. */
    val unbornMark: Long? = null,
    /** Моя духовная линия, поколение за поколением. */
    val lineage: List<Descent> = emptyList(),
    /** Что с тренировкой, отправленной из редактора. */
    val willing: Reply = Reply.None,
    /** Что с заданием, отправленным на выполнение. */
    val fulfilling: Reply = Reply.None,
    /** Подход, к которому приступили: `Pending`, пока сервер не сказал, что он идёт, или почему нет. */
    val beginning: Reply = Reply.None,
)


/**
 * Сессия приложения: соединение, переподключение и одно состояние [state], из которого
 * рисуются экраны. Действия уходят на сервер; ответы сервера собираются в состояние.
 * Вид меняется только по слову сервера (`Turned` … `HistoryEnd`): пока новый вид идёт,
 * на экране остаётся прежний.
 */
class WillSession(context: Context) {

    private val appContext = context.applicationContext
    private val bridge = WillBridge()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(WillState())
    val state: StateFlow<WillState> = _state

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Короткие уведомления сервера — для снекбара. */
    val notices: SharedFlow<String> = _notices

    /** Вид, который сейчас собирается из событий сервера. */
    private var gathering: View? = null

    /** Куда смотреть после переподключения: хозяин Обители и комната. */
    var target: Pair<String, String> = "" to ""
        private set

    private val reconnect = Runnable { connect() }

    private var started = false

    /**
     * Начать сессию один раз на всё приложение (экран при повороте пересоздаётся, сессия — нет).
     * [resumed] — куда смотреть после входа: где был экран, когда система выгрузила процесс.
     */
    fun start(resumed: Pair<String, String> = "" to "") {
        if (started) return
        started = true
        target = resumed
        connect()
    }

    private fun connect() {
        mainHandler.removeCallbacks(reconnect)
        bridge.connect(
            WillBridge.HOST,
            WillBridge.PORT,
            DeviceTokenStore.loadOrCreate(appContext),
            ::onEvent,
            ::onClosed,
        )
    }

    private fun onClosed(reason: String) {
        // Ответов на отправленное уже не будет: окна остаются открытыми.
        _state.update {
            it.copy(connection = Connection.Reconnecting, willing = Reply.None, fulfilling = Reply.None, beginning = Reply.None)
        }
        android.util.Log.w(TAG, "connection lost: $reason")
        _notices.tryEmit("Связь потеряна, переподключаюсь…")
        mainHandler.removeCallbacks(reconnect)
        mainHandler.postDelayed(reconnect, RECONNECT_DELAY_MS)
    }

    // ── Действия ──────────────────────────────────────────────────────────────

    /** Своя Обитель: обзор и комнаты. */
    fun home() = look("", "")

    /** Обитель хозяина, у которого обитаешь. */
    fun visit(host: String) = look(host, "")

    /** Комната Обители, на которую смотришь. */
    fun enter(room: String) = look(_state.value.view?.host.orEmpty(), room)

    /** К Вратам хозяина, чтобы он впустил. */
    fun gates(host: String) = look(host.trim(), GATES)

    /** В свою Горницу — к своим обитателям. */
    fun upperRoom() = look("", UPPER_ROOM)

    /** В свою Родильную — к нерождённым. */
    fun birthRoom() = look("", BIRTH_ROOM)

    /** Родить нерождённого, стоя в Родильной: отцом станет её хозяин. */
    fun bear(mark: Long) = send(clientEvent { bear = bear { this.mark = mark } })

    /** Избрать отца по духу; он отказаться не может. */
    fun chooseFather(name: String) = send(clientEvent { chooseFather = chooseFather { this.name = name.trim() } })

    fun listLineage() = send(clientEvent { listLineage = listLineage {} })

    /** Из комнаты — к её Обители; из чужой Обители — домой. */
    fun back(): Boolean {
        val view = _state.value.view ?: return false
        return when {
            view is View.Room -> { look(view.host, ""); true }
            view.host.isNotEmpty() -> { home(); true }
            else -> false
        }
    }

    fun say(text: String) {
        val view = _state.value.view as? View.Room ?: return
        if (!view.writable || text.isBlank()) return
        // Своё слово приходит в подтверждении (`ReceiptAck`); прежний сервер его не несёт — тогда комната перечитывается.
        send(clientEvent { chat = chatMessage { body = text.trim() } })
    }

    /**
     * Выполнить задание; тренировку — завершить: сделанное сервер складывает из подходов.
     * До ответа сервера [WillState.fulfilling] — `Pending`.
     */
    fun fulfil(behestId: Long, report: String) {
        val view = _state.value.view as? View.Room ?: return
        if (_state.value.fulfilling == Reply.Pending) return
        val sent = send(clientEvent { fulfil = fulfil { this.behestId = behestId; this.report = report.trim() } })
        if (!sent) return
        _state.update { it.copy(fulfilling = Reply.Pending) }
        look(view.host, view.room)
    }

    /** Окно выполнения закрыто после того, как задание выполнено. */
    fun fulfillingSeen() = _state.update { it.copy(fulfilling = Reply.None) }

    /**
     * Приступить к подходу тренировки (номера с нуля). Сервер отвечает идущим подходом
     * (`Underway`) или уведомлением, почему нет; до того [WillState.beginning] — `Pending`.
     */
    fun beginApproach(behestId: Long, exercise: Int, approach: Int) {
        if (_state.value.beginning == Reply.Pending) return
        val sent = send(clientEvent {
            beginApproach = beginApproach { this.behestId = behestId; this.exercise = exercise; this.approach = approach }
        })
        if (sent) _state.update { it.copy(beginning = Reply.Pending) }
    }

    /** Завершить идущий подход: что сделано. */
    fun finishApproach(behestId: Long, weightGrams: Int, repetitions: Int) =
        send(clientEvent {
            finishApproach = finishApproach { this.behestId = behestId; this.weightGrams = weightGrams; this.repetitions = repetitions }
        })

    /**
     * Велеть тренировку в Узах, где стоишь Тренером. Сервер отвечает ровно одним
     * уведомлением — «задана» или почему нет; до него [WillState.willing] — `Pending`.
     */
    fun train(title: String, exercises: List<ExerciseItem>) {
        val view = _state.value.view as? View.Room ?: return
        if (_state.value.willing == Reply.Pending) return
        val sent = send(clientEvent {
            train = train {
                this.title = title.trim()
                this.exercises.addAll(exercises.map { it.toWire() })
            }
        })
        if (!sent) return
        _state.update { it.copy(willing = Reply.Pending) }
        look(view.host, view.room)
    }

    /** Редактор закрыт после того, как тренировка задана. */
    fun willingSeen() = _state.update { it.copy(willing = Reply.None) }

    /** Перенести комнату своей Обители в другую часть — стоя в своей Горнице. */
    fun arrange(room: String, outer: Boolean) =
        send(clientEvent { arrange = arrange { this.room = room; part = if (outer) RoomPart.OUTER else RoomPart.INNER } })

    fun admit(name: String) = send(clientEvent { admit = admit { this.name = name.trim() } })

    fun regard(name: String, kind: Kind) {
        val wire = when (kind) {
            Kind.Acquaintance -> DwellerKind.ACQUAINTANCE
            Kind.Neighbour -> DwellerKind.NEIGHBOUR
            Kind.Friend -> DwellerKind.FRIEND
        }
        send(clientEvent { regard = regard { this.name = name; this.kind = wire } })
    }

    fun ask(name: String) = send(clientEvent { supplicate = supplicate { addresseeName = name } })

    fun accept(name: String) {
        send(clientEvent { acceptSupplication = acceptSupplication { suppliantName = name } })
        listSupplications()
    }

    fun reject(name: String) {
        send(clientEvent { rejectSupplication = rejectSupplication { suppliantName = name } })
        listSupplications()
    }

    /** Сказать что-то от себя — например, «Имя скопировано». */
    fun tell(text: String) {
        _notices.tryEmit(text)
    }

    fun listDwellers() = send(clientEvent { listDwellers = listDwellers {} })
    fun listDwellings() = send(clientEvent { listDwellings = listDwellings {} })
    fun listSupplications() = send(clientEvent { listSupplications = listSupplications {} })

    private fun look(host: String, room: String) {
        target = host to room
        send(clientEvent { turn = turn { abodeOf = host; this.room = room } })
    }

    private fun send(event: ClientEvent): Boolean {
        val sent = bridge.send(event)
        if (!sent) _notices.tryEmit("Нет связи с сервером")
        return sent
    }

    // ── События сервера ───────────────────────────────────────────────────────

    private fun onEvent(event: ServerEvent) {
        when (event.eventCase) {
            ServerEvent.EventCase.AUTH_OK -> {
                if (event.authOk.unborn) {
                    // Тело ещё не рождено: только ждать.
                    _state.update { it.copy(connection = Connection.Ready, ownName = "", unbornMark = event.authOk.mark) }
                    return
                }
                _state.update {
                    it.copy(connection = Connection.Ready, ownName = event.authOk.name, unbornMark = null, loading = true)
                }
                // После входа взгляд — на своей Обители; вернуться туда, где был.
                gathering = View.Abode("")
                send(clientEvent { historyRequest = historyRequest { limit = HISTORY_LIMIT } })
                val (host, room) = target
                if (host.isNotEmpty() || room.isNotEmpty()) look(host, room)
                listDwellers()
                listDwellings()
                listSupplications()
            }
            ServerEvent.EventCase.AUTH_REQUIRED -> _notices.tryEmit("Вход отклонён")
            ServerEvent.EventCase.TURNED -> {
                val turned = event.turned
                gathering = if (turned.room.isEmpty()) {
                    View.Abode(turned.abodeOf)
                } else {
                    View.Room(turned.abodeOf, turned.room, turned.aspect.toAspect(), turned.writable)
                }
                _state.update { it.copy(loading = true) }
            }
            ServerEvent.EventCase.ROOMS -> {
                val rooms = event.rooms.roomsList.map { RoomItem(it.name, it.part == RoomPart.OUTER) }
                when (val gathered = gathering) {
                    is View.Abode -> gathering = gathered.copy(rooms = rooms)
                    // В своей Горнице — комнаты, которые здесь переносят.
                    is View.Room -> gathering = gathered.copy(rooms = rooms)
                    null -> _state.update { s ->
                        // Перенесли комнату, стоя в Горнице: список приходит заново.
                        val shown = (s.view as? View.Room)?.takeIf { it.aspect == Aspect.Dwellers } ?: return@update s
                        s.copy(view = shown.copy(rooms = rooms))
                    }
                }
            }
            ServerEvent.EventCase.OUTSTANDING -> {
                val abode = gathering as? View.Abode ?: return
                gathering = abode.copy(
                    owed = event.outstanding.owedList.map { Waiting(it.room, it.behest.id, it.behest.body) },
                    awaited = event.outstanding.awaitedList.map { Waiting(it.room, it.behest.id, it.behest.body) },
                )
            }
            ServerEvent.EventCase.WORD -> place(event.word.toItem())
            ServerEvent.EventCase.UNDERWAY -> {
                val u = event.underway
                val doing = if (u.doing) UnderwayItem(u.behestId, u.exercise, u.approach, u.begunAtNs) else null
                (gathering as? View.Room)?.let { gathering = it.copy(underway = doing) }
                _state.update { s ->
                    // Подход пошёл — к нему больше не приступают.
                    val begun = if (doing != null) s.copy(beginning = Reply.None) else s
                    val shown = begun.view as? View.Room ?: return@update begun
                    begun.copy(view = shown.copy(underway = doing))
                }
            }
            ServerEvent.EventCase.EXERTED -> {
                val e = event.exerted.effort
                val effort = EffortItem(e.exercise, e.approach, e.weightGrams, e.repetitions, e.begunAtNs, e.finishedAtNs)
                val add = { words: List<WordItem> ->
                    words.map { if (it.id == event.exerted.behestId) it.copy(efforts = it.efforts + effort) else it }
                }
                (gathering as? View.Room)?.let { gathering = it.copy(words = add(it.words)) }
                _state.update { s ->
                    val shown = s.view as? View.Room ?: return@update s
                    s.copy(view = shown.copy(words = add(shown.words)))
                }
            }
            ServerEvent.EventCase.HISTORY_END -> {
                val shown = gathering
                gathering = null
                _state.update { it.copy(view = shown ?: it.view, loading = false) }
            }
            ServerEvent.EventCase.RECEIPT_ACK -> {
                if (event.receiptAck.hasWord()) {
                    place(event.receiptAck.word.toItem())
                } else {
                    (_state.value.view as? View.Room)?.let { look(it.host, it.room) }
                }
            }
            ServerEvent.EventCase.PROTOCOL_NOTICE -> {
                val message = event.protocolNotice.message
                // Пока просьба ждёт ответа, первое уведомление — её ответ.
                _state.update {
                    when {
                        it.willing == Reply.Pending -> it.copy(willing = replyOf(TRAINING_WILLED, message))
                        it.fulfilling == Reply.Pending -> it.copy(fulfilling = replyOf(BEHEST_FULFILLED, message))
                        it.beginning == Reply.Pending -> it.copy(beginning = Reply.None)
                        else -> it
                    }
                }
                _notices.tryEmit(Notices.ru(message))
            }
            ServerEvent.EventCase.DWELLING -> {
                _notices.tryEmit("Вы обитаете у ${event.dwelling.hostName}: ${kindName(event.dwelling.kind.toKind())}")
                listDwellings()
            }
            ServerEvent.EventCase.DWELLERS -> {
                val people = event.dwellers.dwellersList.map {
                    val standings = buildSet {
                        if (it.trainer) add(Standing.Trainer)
                        if (it.novice) add(Standing.Novice)
                        if (it.asks) add(Standing.Asks)
                        if (it.asked) add(Standing.Asked)
                    }
                    Person(it.name, it.kind.toKind(), standings)
                }
                // В Горнице — её обитатели; своя Горница — это и мои обитатели.
                val gatheringUpperRoom = (gathering as? View.Room)?.takeIf { it.aspect == Aspect.Dwellers }
                if (gatheringUpperRoom != null) gathering = gatheringUpperRoom.copy(people = people)
                _state.update { s ->
                    val shown = (s.view as? View.Room)?.takeIf { it.aspect == Aspect.Dwellers }
                    val upperRoom = gatheringUpperRoom ?: shown
                    s.copy(
                        dwellers = if (upperRoom == null || upperRoom.host.isEmpty()) people else s.dwellers,
                        view = if (shown != null) shown.copy(people = people) else s.view,
                    )
                }
            }
            ServerEvent.EventCase.THRESHOLD -> {
                val open = event.threshold.open
                val keeping = event.threshold.keeping
                val waiting = event.threshold.waitingList
                val gatheringGates = (gathering as? View.Room)?.takeIf { it.aspect == Aspect.Threshold }
                if (gatheringGates != null) {
                    gathering = gatheringGates.copy(gatesOpen = open, keeping = keeping, waiting = waiting)
                } else {
                    _state.update { s ->
                        val shown = (s.view as? View.Room)?.takeIf { it.aspect == Aspect.Threshold } ?: return@update s
                        s.copy(view = shown.copy(gatesOpen = open, keeping = keeping, waiting = waiting))
                    }
                }
            }
            ServerEvent.EventCase.UNBORN -> {
                val marks = event.unborn.marksList
                val gatheringBirthRoom = (gathering as? View.Room)?.takeIf { it.aspect == Aspect.Birth }
                if (gatheringBirthRoom != null) {
                    gathering = gatheringBirthRoom.copy(unborn = marks)
                } else {
                    _state.update { s ->
                        val shown = (s.view as? View.Room)?.takeIf { it.aspect == Aspect.Birth } ?: return@update s
                        s.copy(view = shown.copy(unborn = marks))
                    }
                }
            }
            ServerEvent.EventCase.LINEAGE -> _state.update { s ->
                s.copy(lineage = event.lineage.descentsList.map { Descent(it.name, it.fatherName) })
            }
            ServerEvent.EventCase.DWELLINGS -> _state.update { s ->
                s.copy(dwellings = event.dwellings.dwellingsList.map { Person(it.hostName, it.kind.toKind()) })
            }
            ServerEvent.EventCase.SUPPLICATIONS -> _state.update { s ->
                s.copy(supplications = event.supplications.suppliantNamesList)
            }
            ServerEvent.EventCase.SUPPLICATION_OFFER -> {
                _notices.tryEmit("${event.supplicationOffer.suppliantName} просит вас стать его Тренером")
                listSupplications()
            }
            ServerEvent.EventCase.TIE_FORMED -> {
                val other = event.tieFormed.counterpartName
                _notices.tryEmit(if (event.tieFormed.asNovice) "$other — теперь ваш Тренер" else "Вы — теперь Тренер $other")
                refreshIfHome()
            }
            ServerEvent.EventCase.STIRRED -> {
                _notices.tryEmit("${event.stirred.authorName} написал в комнате ${event.stirred.room}")
                refreshIfHome()
            }
            else -> Unit
        }
    }

    /** Слово в комнату: в собираемую, иначе — живое, в ту, на которую смотрим. */
    private fun place(word: WordItem) {
        val room = gathering as? View.Room
        if (room != null) {
            gathering = room.copy(words = room.words + word)
        } else {
            _state.update { s ->
                val shown = s.view as? View.Room ?: return@update s
                s.copy(view = shown.copy(words = shown.words + word))
            }
        }
    }

    /** Обзор своей Обители меняется от Уз: перечитать, если он перед глазами. */
    private fun refreshIfHome() {
        val view = _state.value.view
        if (view is View.Abode && view.host.isEmpty()) home()
    }

    private fun Word.toItem() = WordItem(
        id, name, body, isMine, kind, behestId,
        exercisesList.map { e ->
            ExerciseItem(e.name, e.approachesList.map { ApproachItem(it.weightGrams, it.repetitions, it.restSeconds) })
        },
        effortsList.map { EffortItem(it.exercise, it.approach, it.weightGrams, it.repetitions, it.begunAtNs, it.finishedAtNs) },
    )

    private fun ExerciseItem.toWire() = exercise {
        name = this@toWire.name
        approaches.addAll(this@toWire.approaches.map { a ->
            approach { weightGrams = a.weightGrams; repetitions = a.repetitions; restSeconds = a.restSeconds }
        })
    }

    companion object {
        private const val TAG = "WillSession"
        private const val RECONNECT_DELAY_MS = 3_000L
        private const val HISTORY_LIMIT = 200
        private val TRAINING_WILLED = Regex("training \\d+ willed")
        private val BEHEST_FULFILLED = Regex("behest \\d+ fulfilled")

        private fun replyOf(granted: Regex, notice: String) = if (granted.matches(notice)) Reply.Granted else Reply.None

        /** Имена стандартных комнат, как их называет сервер. */
        const val CELL = "Келья"
        const val GATES = "Врата"
        const val UPPER_ROOM = "Горница"
        const val BIRTH_ROOM = "Родильная"

        fun kindName(kind: Kind): String = when (kind) {
            Kind.Acquaintance -> "знакомый"
            Kind.Neighbour -> "ближний"
            Kind.Friend -> "друг"
        }

        private fun RoomAspect.toAspect(): Aspect = when (this) {
            RoomAspect.THRESHOLD -> Aspect.Threshold
            RoomAspect.DWELLERS -> Aspect.Dwellers
            RoomAspect.BIRTH -> Aspect.Birth
            else -> Aspect.Words
        }

        private fun DwellerKind.toKind(): Kind = when (this) {
            DwellerKind.NEIGHBOUR -> Kind.Neighbour
            DwellerKind.FRIEND -> Kind.Friend
            else -> Kind.Acquaintance
        }
    }
}
