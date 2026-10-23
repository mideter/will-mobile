package com.will.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
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
import will.v1.endApproach
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
import will.v1.sayRemark
import will.v1.rejectSupplication
import will.v1.supplicate
import will.v1.train
import will.v1.turn


/**
 * Сессия приложения: соединение, переподключение и одно состояние [state], из которого
 * рисуются экраны. Действия уходят на сервер; ответы сервера собираются в состояние.
 * Вид меняется только по слову сервера (`Turned` … `HistoryEnd`): пока новый вид идёт,
 * на экране остаётся прежний. Сессия одна на всё приложение: поворот экрана её не рвёт.
 *
 * Связь: появилась сеть — переподключаемся сразу, не дожидаясь конца паузы. Пока связи нет,
 * отправить нельзя, и говорить об этом незачем: экран закрывает заставка. После входа
 * возвращаемся только в свою комнату, где был экран, — проснувшись, всякий дома.
 *
 * Часы: метки времени тренировки сравнивают с «сейчас» только по часам сервера ([clock]).
 * Они сверяются при входе (`AuthOk`) и по ответам на «Приступить» и «Завершить подход».
 *
 * Запросы: свои запросы нумеруются, ответ (`Reply`) узнаётся по номеру, а не по словам
 * уведомления; номер растёт на всё время жизни сессии. Задание и тренировку, отправленные
 * на выполнение и из редактора, ждут как `Pending` до ответа.
 *
 * Путь: ходят путём мира — из Обители через её Врата, к Вратам той, куда идут, и внутрь;
 * «назад» — тем же путём. От своих Врат идут к чужим; у чужих входят, если там обитают.
 * Рождают, стоя в своих Вратах, — рождённый станет чадом по плоти. Переносят Кельи, стоя в
 * своей Кормчей. Отец по духу отказаться не может. Приступить к подходу из уведомления,
 * стоя в другом месте, — значит сначала дойти до комнаты Уз; с первым подходом тренировку
 * ведёт [TrainingService], пока её не завершат или не забросят.
 *
 * Слова: новое слово идёт в собираемый вид или, живое, в тот, на который смотрят. Обзор
 * своей Обители меняется от Уз — его перечитывают, если он перед глазами. Своё
 * уведомление ([tell]) идёт туда же, куда и уведомления сервера.
 */
class WillSession(context: Context) {

    private val appContext = context.applicationContext
    private val bridge = WillBridge(if (WillBridge.TLS) serverCertificate(context) else null)
    private val mainHandler = Handler(Looper.getMainLooper())

    val clock = ServerClock()

    private var bindSentAt: Long? = null

    private var lastRequest = 0L
    private var willingRequest: Long? = null
    private var fulfillingRequest: Long? = null
    private var fulfillingBehest: Long? = null
    private var beginningRequest: Long? = null

    private var beginSentAt: Long? = null
    private var endSentAt: Long? = null
    private var finishSentAt: Long? = null

    private val _state = MutableStateFlow(WillState())
    val state: StateFlow<WillState> = _state

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val notices: SharedFlow<String> = _notices

    private var gathering: View? = null

    var target: Pair<String, String> = "" to ""
        private set

    private val reconnect = Runnable { connect() }

    private val backoff = Backoff()

    private var started = false

    fun start(resumed: Pair<String, String> = "" to "") {
        if (started) return
        started = true
        target = resumed
        connect()
        watchNetwork()
    }

    private fun watchNetwork() {
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post {
                    if (_state.value.connection != Connection.Reconnecting) return@post
                    backoff.reset()
                    connect()
                }
            }
        })
    }

    private fun connect() {
        mainHandler.removeCallbacks(reconnect)
        bindSentAt = clock.phoneNowNs()
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
        beginSentAt = null
        endSentAt = null
        finishSentAt = null
        willingRequest = null
        fulfillingRequest = null
        fulfillingBehest = null
        beginningRequest = null
        val delay = backoff.nextPauseMs()
        android.util.Log.w(TAG, "connection lost: $reason; again in $delay ms")
        // Сказать об этом — дело заставки: пока связи нет, она закрывает экран.
        mainHandler.removeCallbacks(reconnect)
        mainHandler.postDelayed(reconnect, delay)
    }

    // ── Действия ──────────────────────────────────────────────────────────────

    fun home() = look("", "")

    fun visit(host: String) = look(host, "")

    fun enter(room: String) = look(_state.value.view?.host.orEmpty(), room)

    fun gates(host: String) = look(host.trim(), GATES)

    fun bear(mark: Long) = send(clientEvent { bear = bear { this.mark = mark } })

    fun chooseFather(name: String) = send(clientEvent { chooseFather = chooseFather { this.name = name.trim() } })

    fun listLineage() = send(clientEvent { listLineage = listLineage {} })

    fun back(): Boolean {
        val view = _state.value.view ?: return false
        val own = view.host.isEmpty()
        val atGates = view is View.Room && view.aspect == Aspect.Threshold
        when {
            own && view is View.Abode -> return false
            own -> home()
            atGates -> gates("")
            view is View.Room -> look(view.host, "")
            else -> gates(view.host)
        }
        return true
    }

    fun say(text: String) {
        val view = _state.value.view as? View.Room ?: return
        if (!view.writable || text.isBlank()) return
        // Своё слово приходит в подтверждении (`ReceiptAck`); прежний сервер его не несёт — тогда комната перечитывается.
        send(clientEvent { chat = chatMessage { body = text.trim() } })
    }

    fun fulfil(behestId: Long, report: String) {
        val view = _state.value.view as? View.Room ?: return
        if (_state.value.fulfilling == Reply.Pending) return
        val asked = ask(clientEvent { fulfil = fulfil { this.behestId = behestId; this.report = report.trim() } }) ?: return
        fulfillingRequest = asked
        fulfillingBehest = behestId
        _state.update { it.copy(fulfilling = Reply.Pending) }
        look(view.host, view.room)
    }

    fun fulfillingSeen() = _state.update { it.copy(fulfilling = Reply.None) }

    fun choose(behestId: Long, exercise: Int, extra: Int? = null) =
        _state.update { it.copy(choice = Choice(behestId, exercise, extra)) }

    fun beginApproach(behestId: Long, exercise: Int, approach: Int) {
        val s = _state.value
        if (s.beginning == Reply.Pending) return
        val shown = (s.view as? View.Room)?.takeIf { room -> room.words.any { it.id == behestId } }
        val workout = shown?.let { Workout(it.host, it.room, behestId) }
            ?: s.workout?.takeIf { it.behestId == behestId }
            ?: return
        if (shown == null) walk(workout.host, workout.room)
        // После поворота ответ ждёт ещё и слова комнаты: для сверки часов он слишком поздний.
        val sentAt = clock.phoneNowNs().takeIf { shown != null }
        val asked = ask(clientEvent {
            beginApproach = beginApproach { this.behestId = behestId; this.exercise = exercise; this.approach = approach }
        }) ?: return
        beginningRequest = asked
        beginSentAt = sentAt
        _state.update { it.copy(beginning = Reply.Pending, workout = workout) }
        TrainingService.start(appContext)
    }

    fun stopWorkout() = _state.update { it.copy(workout = null) }

    fun endApproach(behestId: Long) {
        val sentAt = clock.phoneNowNs()
        if (send(clientEvent { endApproach = endApproach { this.behestId = behestId } })) endSentAt = sentAt
    }

    fun finishApproach(behestId: Long, weightGrams: Int, repetitions: Int, strain: Int) {
        // Подход, законченный раньше, кончился не сейчас: по ответу часы не сверить.
        val ended = (_state.value.view as? View.Room)?.underways?.get(behestId)?.endedAtNs != null
        val sentAt = clock.phoneNowNs().takeIf { !ended }
        val sent = send(clientEvent {
            finishApproach = finishApproach {
                this.behestId = behestId; this.weightGrams = weightGrams; this.repetitions = repetitions; this.strain = strain
            }
        })
        if (sent && sentAt != null) finishSentAt = sentAt
    }

    fun sayRemark(behestId: Long, exercise: Int, text: String) {
        if (text.isBlank()) return
        send(clientEvent { sayRemark = sayRemark { this.behestId = behestId; this.exercise = exercise; body = text.trim() } })
    }

    fun train(title: String, exercises: List<ExerciseItem>) {
        val view = _state.value.view as? View.Room ?: return
        if (_state.value.willing == Reply.Pending) return
        val asked = ask(clientEvent {
            train = train {
                this.title = title.trim()
                this.exercises.addAll(exercises.map { it.toWire() })
            }
        }) ?: return
        willingRequest = asked
        _state.update { it.copy(willing = Reply.Pending) }
        look(view.host, view.room)
    }

    fun willingSeen() = _state.update { it.copy(willing = Reply.None) }

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

    fun tell(text: String) {
        _notices.tryEmit(text)
    }

    fun listDwellers() = send(clientEvent { listDwellers = listDwellers {} })
    fun listDwellings() = send(clientEvent { listDwellings = listDwellings {} })
    fun listSupplications() = send(clientEvent { listSupplications = listSupplications {} })

    private fun walk(host: String, room: String) {
        val view = _state.value.view
        if (view != null && view.host != host) {
            if (!(view is View.Room && view.aspect == Aspect.Threshold)) look(view.host, GATES)
            if (room != GATES) look(host, GATES)
        }
        look(host, room)
    }

    private fun look(host: String, room: String) {
        target = host to room
        send(clientEvent { turn = turn { abodeOf = host; this.room = room } })
    }

    private fun send(event: ClientEvent): Boolean = bridge.send(event)

    private fun ask(event: ClientEvent): Long? {
        val id = ++lastRequest
        return id.takeIf { send(event.toBuilder().setRequestId(it).build()) }
    }

    // ── События сервера ───────────────────────────────────────────────────────

    private fun onEvent(event: ServerEvent) {
        when (event.eventCase) {
            ServerEvent.EventCase.AUTH_OK -> {
                backoff.reset()
                // Сервер говорит своё время, принимая тело: часы сверяются сразу при входе.
                val sent = bindSentAt
                bindSentAt = null
                if (sent != null && event.authOk.serverTimeNs != 0L) clock.observe(event.authOk.serverTimeNs, sent, clock.phoneNowNs())
                if (event.authOk.unborn) {
                    _state.update { it.copy(connection = Connection.Ready, ownName = "", unbornMark = event.authOk.mark) }
                    return
                }
                _state.update {
                    it.copy(connection = Connection.Ready, ownName = event.authOk.name, unbornMark = null, loading = true)
                }
                // Проснувшись, всякий дома: вернуться можно только в свою комнату, где был.
                gathering = View.Abode("")
                send(clientEvent { historyRequest = historyRequest { limit = HISTORY_LIMIT } })
                val (host, room) = target
                if (host.isEmpty() && room.isNotEmpty()) look(host, room) else target = "" to ""
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
                val rooms = event.rooms.roomsList.map { RoomItem(it.name, it.part == RoomPart.OUTER, it.aspect.toAspect()) }
                when (val gathered = gathering) {
                    is View.Abode -> gathering = gathered.copy(rooms = rooms)
                    is View.Room -> gathering = gathered.copy(rooms = rooms)
                    null -> _state.update { s ->
                        // Перенесли комнату, стоя в Кормчей: список приходит заново.
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
                val doing = if (u.doing) {
                    UnderwayItem(u.behestId, u.exercise, u.approach, u.begunAtNs, u.endedAtNs.takeIf { it != 0L })
                } else {
                    null
                }
                // Ответ на своё «Приступить» или «Завершить подход»: сервер отметил миг, когда принял его.
                val ended = doing?.endedAtNs
                if (ended != null) {
                    endSentAt?.let { clock.observe(ended, it, clock.phoneNowNs()) }
                    endSentAt = null
                } else if (doing != null) {
                    beginSentAt?.let { clock.observe(doing.begunAtNs, it, clock.phoneNowNs()) }
                    beginSentAt = null
                }
                // Сервер называет тренировку и тогда, когда в ней подход завершён.
                fun Map<Long, UnderwayItem>.told() = if (doing != null) this + (u.behestId to doing) else this - u.behestId
                (gathering as? View.Room)?.let { gathering = it.copy(underways = it.underways.told()) }
                _state.update { s ->
                    // Подход пошёл — к нему больше не приступают; выбор держится его упражнения,
                    // где бы его ни начали, а «сверх заданного» не залипает.
                    val begun = if (doing != null) {
                        s.copy(beginning = Reply.None, choice = Choice(doing.behestId, doing.exercise))
                    } else {
                        s
                    }
                    val shown = begun.view as? View.Room ?: return@update begun
                    begun.copy(view = shown.copy(underways = shown.underways.told()))
                }
            }
            ServerEvent.EventCase.EXERTED -> {
                val e = event.exerted.effort
                // Ответ на своё «Завершить подход»: сервер отметил конец, когда принял его.
                finishSentAt?.let { clock.observe(e.finishedAtNs, it, clock.phoneNowNs()) }
                endSentAt = null
        finishSentAt = null
                val effort = EffortItem(e.exercise, e.approach, e.weightGrams, e.repetitions, e.begunAtNs, e.finishedAtNs, e.strain)
                val add = { words: List<WordItem> ->
                    words.map { if (it.id == event.exerted.behestId) it.copy(efforts = it.efforts + effort) else it }
                }
                (gathering as? View.Room)?.let { gathering = it.copy(words = add(it.words)) }
                _state.update { s ->
                    val shown = s.view as? View.Room ?: return@update s
                    s.copy(view = shown.copy(words = add(shown.words)))
                }
            }
            ServerEvent.EventCase.REMARKED -> {
                val r = event.remarked.remark
                val remark = RemarkItem(r.exercise, r.body, r.saidAtNs)
                val add = { words: List<WordItem> ->
                    words.map { if (it.id == event.remarked.behestId) it.copy(remarks = it.remarks + remark) else it }
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
            ServerEvent.EventCase.PROTOCOL_NOTICE -> event.protocolNotice.let {
                _notices.tryEmit(Notices.say(it.code, it.argsList, it.message))
            }
            ServerEvent.EventCase.REPLY -> {
                // Ответ на свой запрос — по его номеру; что сказано о нём, говорит всплывающее поле.
                val reply = event.reply
                val outcome = if (reply.granted) Reply.Granted else Reply.None
                when (reply.requestId) {
                    willingRequest -> {
                        willingRequest = null
                        _state.update { it.copy(willing = outcome) }
                    }
                    fulfillingRequest -> {
                        // Завершённую тренировку больше не ведут.
                        if (reply.granted && fulfillingBehest == _state.value.workout?.behestId) stopWorkout()
                        fulfillingRequest = null
                        fulfillingBehest = null
                        _state.update { it.copy(fulfilling = outcome) }
                    }
                    beginningRequest -> {
                        beginningRequest = null
                        _state.update { it.copy(beginning = Reply.None) }
                    }
                }
                if (reply.notice.isNotEmpty()) _notices.tryEmit(Notices.say(reply.code, reply.argsList, reply.notice))
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
                // В Кормчей — её обитатели; своя Кормчая — это и мои обитатели.
                val gatheringHelm = (gathering as? View.Room)?.takeIf { it.aspect == Aspect.Dwellers }
                if (gatheringHelm != null) gathering = gatheringHelm.copy(people = people)
                _state.update { s ->
                    val shown = (s.view as? View.Room)?.takeIf { it.aspect == Aspect.Dwellers }
                    val helm = gatheringHelm ?: shown
                    s.copy(
                        dwellers = if (helm == null || helm.host.isEmpty()) people else s.dwellers,
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
                // Нерождённых видит хозяин в своих Вратах.
                val gatheringGates = (gathering as? View.Room)?.takeIf { it.aspect == Aspect.Threshold }
                if (gatheringGates != null) {
                    gathering = gatheringGates.copy(unborn = marks)
                } else {
                    _state.update { s ->
                        val shown = (s.view as? View.Room)?.takeIf { it.aspect == Aspect.Threshold } ?: return@update s
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

    private fun refreshIfHome() {
        val view = _state.value.view
        if (view is View.Abode && view.host.isEmpty()) home()
    }

    private fun Word.toItem() = WordItem(
        id, name, body, isMine, kind, behestId,
        exercisesList.map { e ->
            ExerciseItem(e.name, e.approachesList.map { ApproachItem(it.weightGrams, it.repetitions, it.restSeconds) }, e.instruction)
        },
        effortsList.map { EffortItem(it.exercise, it.approach, it.weightGrams, it.repetitions, it.begunAtNs, it.finishedAtNs, it.strain) },
        remarksList.map { RemarkItem(it.exercise, it.body, it.saidAtNs) },
    )

    private fun ExerciseItem.toWire() = exercise {
        name = this@toWire.name
        instruction = this@toWire.instruction
        approaches.addAll(this@toWire.approaches.map { a ->
            approach { weightGrams = a.weightGrams; repetitions = a.repetitions; restSeconds = a.restSeconds }
        })
    }

    companion object {
        private const val TAG = "WillSession"
        private const val HISTORY_LIMIT = 200
        const val CELL = "Слово"
        const val GATES = "Врата"

        fun kindName(kind: Kind): String = when (kind) {
            Kind.Acquaintance -> "знакомый"
            Kind.Neighbour -> "ближний"
            Kind.Friend -> "друг"
        }

        private fun RoomAspect.toAspect(): Aspect = when (this) {
            RoomAspect.THRESHOLD -> Aspect.Threshold
            RoomAspect.DWELLERS -> Aspect.Dwellers
            else -> Aspect.Words
        }

        private fun DwellerKind.toKind(): Kind = when (this) {
            DwellerKind.NEIGHBOUR -> Kind.Neighbour
            DwellerKind.FRIEND -> Kind.Friend
            else -> Kind.Acquaintance
        }
    }
}


private fun serverCertificate(context: Context): String =
    try {
        context.assets.open("will_server.pem").bufferedReader().use { it.readText() }
    } catch (e: java.io.IOException) {
        throw IllegalStateException("В сборке нет сертификата сервера (assets/will_server.pem)", e)
    }
