package com.will.app

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import will.v1.MessengerOuterClass.ClientEvent
import will.v1.MessengerOuterClass.OutstandingBehest
import will.v1.MessengerOuterClass.Underway
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
import will.v1.endApproach
import will.v1.chatMessage
import will.v1.clientEvent
import will.v1.exercise
import will.v1.finishApproach
import will.v1.fulfil
import will.v1.heartbeat
import will.v1.historyRequest
import will.v1.listDwellers
import will.v1.listDwellings
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
 * Живёт она в телефоне ([phone]) и говорит с сервером через связь ([transport]); больше
 * ничего от Android не знает — в тестах оба подделывают.
 *
 * Связь: появилась сеть — переподключаемся сразу, не дожидаясь конца паузы. Соединение
 * живёт в сети, по которой открыто: пропала она или телефон перешёл на другую — соединение
 * оборвано, не дожидаясь, пока это заметит пинг. Связь жива, пока слышен сервер: сердце
 * ([heart]) бьётся к нему — во время тренировки часто, иначе спокойно, — и, не услышав
 * ответа, сессия рвёт соединение сама. Пока связи нет, отправить нельзя, и
 * говорить об этом незачем: экран закрывает заставка. После входа возвращаемся только в
 * свою комнату, где был экран, — проснувшись, всякий дома.
 *
 * Часы: метки времени тренировки сравнивают с «сейчас» только по часам сервера ([clock]).
 * Они сверяются при входе (`AuthOk`) и по ответам на «Приступить» и «Завершить подход».
 *
 * Запросы: свои запросы нумеруются, ответ (`Reply`) узнаётся по номеру, а не по словам
 * уведомления; номер растёт на всё время жизни сессии. Задание и тренировку, отправленные
 * на выполнение и из редактора, подход, к которому приступили, рассказ о подходе и заметку
 * ждут как `Pending` до ответа.
 *
 * Путь: ходят путём мира — из Обители через её Врата, к Вратам той, куда идут, и внутрь;
 * «назад» — тем же путём. От своих Врат идут к чужим; у чужих входят, если там обитают.
 * Рождают, стоя в своих Вратах, — рождённый станет чадом по плоти. Переносят Кельи, стоя в
 * своей Кормчей. Отец по духу отказаться не может. Приступить к подходу из уведомления,
 * стоя в другом месте, — значит сначала дойти до комнаты Уз; с первым подходом тренировку
 * ведёт [TrainingService], пока её не завершат или не забросят. Свою тренировку, где идёт
 * подход или отдых ([TrainingCourse.resting]), ведут, где бы и когда к ней ни приступили:
 * увидев её в своей комнате или в обзоре своей Обители, — и после перезапуска, и когда
 * завершили другую; идущий подход — прежде отдыха.
 *
 * Слова: новое слово идёт в собираемый вид или, живое, в тот, на который смотрят. Обзор
 * своей Обители меняется от Уз — его перечитывают, если он перед глазами. Своё
 * уведомление ([tell]) идёт туда же, куда и уведомления сервера.
 */
class WillSession(
    private val phone: Phone,
    private val transport: Transport,
    val clock: ServerClock = ServerClock(),
) {

    private var connectedOn: Any? = null

    private var bindSentAt: Long? = null

    private var lastRequest = 0L
    private var willingRequest: Long? = null
    private var fulfillingRequest: Long? = null
    private var fulfillingBehest: Long? = null
    private var beginningRequest: Long? = null
    private var returningRequest: Long? = null
    private var tellingRequest: Long? = null
    private var remarkingRequest: Long? = null

    private var beginSentAt: Long? = null
    private var endSentAt: Long? = null
    private var finishSentAt: Long? = null

    private val _state = MutableStateFlow(WillState())
    val state: StateFlow<WillState> = _state

    private val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val notices: SharedFlow<Notice> = _notices

    private var gathering: View? = null

    var target: Pair<String, String> = "" to ""
        private set

    private val reconnect = Runnable { connect() }

    private val heart = Heart()
    private val beating = Runnable { beat() }

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
        phone.watchNetwork(
            onAvailable = { network ->
                // Перешли на другую сеть: прежнее соединение осталось в старой и мертво.
                if (network != connectedOn) abandon("сеть сменилась")
                if (_state.value.connection == Connection.Reconnecting) {
                    backoff.reset()
                    connect()
                }
            },
            onLost = { network -> if (network == connectedOn) abandon("сеть пропала") },
        )
    }

    private fun connect() {
        phone.cancel(reconnect)
        connectedOn = phone.network
        bindSentAt = clock.phoneNowNs()
        transport.connect(phone.deviceToken, ::onEvent, ::onClosed)
    }

    private fun onClosed(reason: String) {
        // Ответов на отправленное уже не будет: окна остаются открытыми.
        _state.update {
            it.copy(
                connection = Connection.Reconnecting,
                willing = Reply.None,
                fulfilling = Reply.None,
                beginning = Reply.None,
                telling = Reply.None,
                remarking = Reply.None,
            )
        }
        beginSentAt = null
        endSentAt = null
        finishSentAt = null
        willingRequest = null
        fulfillingRequest = null
        fulfillingBehest = null
        beginningRequest = null
        returningRequest = null
        tellingRequest = null
        remarkingRequest = null
        phone.cancel(beating)
        val delay = backoff.nextPauseMs()
        phone.log("connection lost: $reason; again in $delay ms")
        // Сказать об этом — дело заставки: пока связи нет, она закрывает экран.
        phone.cancel(reconnect)
        phone.postDelayed(reconnect, delay)
    }

    private fun abandon(reason: String) {
        if (_state.value.connection == Connection.Reconnecting) return
        // Прежнее соединение больше не скажет, что закрылось: закрываем его сами.
        transport.disconnect()
        onClosed(reason)
    }

    // ── Действия ──────────────────────────────────────────────────────────────

    fun home() = look("", "")

    fun leads(notice: Notice): Boolean {
        // Только по своей Обители, стоя в ней: из чужой домой идут через Врата.
        return notice.room != null && _state.value.view?.host?.isEmpty() == true
    }

    fun follow(notice: Notice) {
        val room = notice.room ?: return
        if (leads(notice)) look("", room)
    }

    fun visit(host: String) = look(host, "")

    fun enter(room: String) = look(_state.value.view?.host.orEmpty(), room)

    fun gates(host: String) = look(host.trim(), GATES)

    fun bear(mark: Long) = send(clientEvent { bear = bear { this.mark = mark } })

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
        phone.leadTraining()
        beat()
    }

    fun begin() = _state.update { it.copy(namedBy = null) }

    fun stopWorkout() = _state.update { s -> s.copy(workout = goingIn(s.view, except = s.workout?.behestId)) }

    fun endApproach(behestId: Long) {
        val sentAt = clock.phoneNowNs()
        if (send(clientEvent { endApproach = endApproach { this.behestId = behestId } })) endSentAt = sentAt
    }

    fun finishApproach(behestId: Long, weightGrams: Int, repetitions: Int, strain: Int) {
        if (_state.value.telling == Reply.Pending) return
        // Подход, законченный раньше, кончился не сейчас: по ответу часы не сверить.
        val ended = (_state.value.view as? View.Room)?.underways?.get(behestId)?.endedAtNs != null
        val sentAt = clock.phoneNowNs().takeIf { !ended }
        val asked = ask(clientEvent {
            finishApproach = finishApproach {
                this.behestId = behestId; this.weightGrams = weightGrams; this.repetitions = repetitions; this.strain = strain
            }
        }) ?: return
        tellingRequest = asked
        if (sentAt != null) finishSentAt = sentAt
        _state.update { it.copy(telling = Reply.Pending) }
    }

    fun sayRemark(behestId: Long, exercise: Int, text: String) {
        if (text.isBlank() || _state.value.remarking == Reply.Pending) return
        val asked = ask(clientEvent {
            sayRemark = sayRemark { this.behestId = behestId; this.exercise = exercise; body = text.trim() }
        }) ?: return
        remarkingRequest = asked
        _state.update { it.copy(remarking = Reply.Pending) }
    }

    fun remarkingSeen() = _state.update { it.copy(remarking = Reply.None) }

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

    fun tell(text: String, room: String? = null) {
        _notices.tryEmit(Notice(text, room))
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

    private fun leadGoing() {
        if (_state.value.workout != null) return
        val workout = goingIn(_state.value.view) ?: return
        _state.update { it.copy(workout = workout) }
        phone.leadTraining()
        beat()
    }

    private fun goingIn(view: View?, except: Long? = null): Workout? {
        val nowNs = clock.nowNs()
        val choice = _state.value.choice
        // Обзор своей Обители знает все её тренировки, где я Послушник, с подходами.
        if (view is View.Abode && view.host.isEmpty()) {
            val owed = view.owed.filter { it.word.id != except }
            val going = owed.firstOrNull { it.underway != null }
                ?: owed.firstOrNull { TrainingCourse.resting(it.word, choice, nowNs) }
                ?: return null
            return Workout("", going.room, going.word.id)
        }
        // Своя тренировка — заданная мне в своей Обители и не исполненная; в Ведении подходы
        // чужие, их не ведут.
        val room = (view as? View.Room)?.takeIf { it.host.isEmpty() } ?: return null
        val mine = room.words.filter { w ->
            w.id != except && !w.mine && w.kind == Word.Kind.BEHEST &&
                room.words.none { it.kind == Word.Kind.DEED && it.behestId == w.id }
        }
        val going = mine.firstOrNull { room.underways.containsKey(it.id) }
            ?: mine.firstOrNull { TrainingCourse.resting(it, choice, nowNs) }
            ?: return null
        return Workout("", room.room, going.id)
    }

    private fun beat() {
        phone.cancel(beating)
        if (_state.value.connection != Connection.Ready) return
        val pace = if (_state.value.workout != null) TRAINING_PACE_MS else CALM_PACE_MS
        val pulse = heart.pulse(phone.uptimeMs(), pace) ?: return abandon("сервер не слышен")
        if (pulse.beat) ask(clientEvent { heartbeat = heartbeat {} })
        phone.postDelayed(beating, pulse.nextInMs)
    }

    private fun look(host: String, room: String) {
        target = host to room
        send(clientEvent { turn = turn { abodeOf = host; this.room = room } })
    }

    private fun send(event: ClientEvent): Boolean = transport.send(event)

    private fun ask(event: ClientEvent): Long? {
        val id = ++lastRequest
        return id.takeIf { send(event.toBuilder().setRequestId(it).build()) }
    }

    // ── События сервера ───────────────────────────────────────────────────────

    private fun onEvent(event: ServerEvent) {
        heart.hear()
        when (event.eventCase) {
            ServerEvent.EventCase.AUTH_OK -> {
                backoff.reset()
                heart.wake()
                // Сервер говорит своё время, принимая тело: часы сверяются сразу при входе.
                val sent = bindSentAt
                bindSentAt = null
                if (sent != null && event.authOk.serverTimeNs != 0L) clock.observe(event.authOk.serverTimeNs, sent, clock.phoneNowNs())
                if (event.authOk.unborn) {
                    _state.update { it.copy(connection = Connection.Ready, ownName = "", unbornMark = event.authOk.mark) }
                    beat()
                    return
                }
                _state.update {
                    it.copy(
                        connection = Connection.Ready,
                        ownName = event.authOk.name,
                        unbornMark = null,
                        namedBy = event.authOk.father.ifEmpty { it.namedBy },
                        loading = true,
                    )
                }
                beat()
                // Проснувшись, всякий дома: вернуться можно только в свою комнату, где был. Туда —
                // сразу, мимо обзора Обители: на миг показанный, он закрыл бы открытое в комнате.
                val (host, room) = target
                if (host.isEmpty() && room.isNotEmpty()) {
                    returningRequest = ask(clientEvent { turn = turn { abodeOf = host; this.room = room } })
                } else {
                    target = "" to ""
                    gathering = View.Abode("")
                    send(clientEvent { historyRequest = historyRequest { limit = HISTORY_LIMIT } })
                }
                listDwellers()
                listDwellings()
                listSupplications()
            }
            ServerEvent.EventCase.AUTH_REQUIRED -> tell("Вход отклонён")
            ServerEvent.EventCase.TURNED -> {
                val turned = event.turned
                gathering = if (turned.room.isEmpty()) {
                    View.Abode(turned.abodeOf)
                } else {
                    View.Room(turned.abodeOf, turned.room, turned.aspect.toAspect(), turned.writable, testator = turned.testator)
                }
                _state.update { it.copy(loading = true) }
            }
            ServerEvent.EventCase.ROOMS -> {
                val rooms = event.rooms.roomsList.map { RoomItem(it.name, it.part == RoomPart.OUTER, it.aspect.toAspect(), it.testator) }
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
                val told = { abode: View.Abode ->
                    abode.copy(
                        owed = event.outstanding.owedList.map { it.toWaiting() },
                        awaited = event.outstanding.awaitedList.map { it.toWaiting() },
                        unreviewed = event.outstanding.unreviewedList.map { it.toWaiting() },
                        waiting = event.outstanding.waiting,
                    )
                }
                when (val gathered = gathering) {
                    is View.Abode -> gathering = told(gathered)
                    null -> _state.update { s ->
                        // Узы изменились, пока смотрю на свою Обитель: обзор приходит заново.
                        val shown = (s.view as? View.Abode)?.takeIf { it.host.isEmpty() } ?: return@update s
                        s.copy(view = told(shown))
                    }
                    is View.Room -> Unit
                }
            }
            ServerEvent.EventCase.WORD -> place(event.word.toItem())
            ServerEvent.EventCase.UNDERWAY -> {
                val u = event.underway
                val doing = u.toItem()
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
                leadGoing()
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
                leadGoing()
            }
            ServerEvent.EventCase.RECEIPT_ACK -> {
                if (event.receiptAck.hasWord()) {
                    place(event.receiptAck.word.toItem())
                } else {
                    (_state.value.view as? View.Room)?.let { look(it.host, it.room) }
                }
            }
            ServerEvent.EventCase.PROTOCOL_NOTICE -> event.protocolNotice.let {
                Notices.say(it.code, it.argsList, it.message)?.let { tell(it) }
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
                    returningRequest -> {
                        returningRequest = null
                        // Своей комнаты, где был, больше нет: проснувшийся — дома.
                        if (!reply.granted) home()
                    }
                    tellingRequest -> {
                        // Сказанное уже пришло подходом (`Exerted`): пульт закрывает он, а не ответ.
                        tellingRequest = null
                        _state.update { it.copy(telling = Reply.None) }
                    }
                    remarkingRequest -> {
                        remarkingRequest = null
                        _state.update { it.copy(remarking = outcome) }
                    }
                }
                if (reply.notice.isNotEmpty()) Notices.say(reply.code, reply.argsList, reply.notice)?.let { tell(it) }
            }
            ServerEvent.EventCase.DWELLING -> {
                // Кто дал имя, того и знаешь первым: экран имени уже говорит о нём.
                if (event.dwelling.hostName != _state.value.namedBy) {
                    tell(listOfNotNull("${event.dwelling.hostName} знает вас", kindSaid(event.dwelling.kind.toKind())).joinToString(" · "))
                }
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
            ServerEvent.EventCase.DWELLINGS -> _state.update { s ->
                s.copy(dwellings = event.dwellings.dwellingsList.map { Person(it.hostName, it.kind.toKind()) })
            }
            ServerEvent.EventCase.SUPPLICATIONS -> _state.update { s ->
                s.copy(supplications = event.supplications.suppliantNamesList)
            }
            ServerEvent.EventCase.SUPPLICATION_OFFER -> {
                // Свои Дела и Кормчая показывают Просьбу сами.
                val suppliant = event.supplicationOffer.suppliantName
                if (!seesOwn(Aspect.Dwellers)) _notices.tryEmit(Notice("$suppliant · Просьба стать Тренером", HELM, suppliant))
                listSupplications()
            }
            ServerEvent.EventCase.TIE_FORMED -> {
                // Принявшему Узы видны в его же Кормчей; о них говорится ученику.
                if (event.tieFormed.asNovice) tell("${event.tieFormed.counterpartName} — теперь ваш Тренер")
                refreshIfHome()
            }
            ServerEvent.EventCase.STIRRED -> {
                // Свои Дела сервер рассказывает заново сам.
                if (!seesOwn()) {
                    val word = event.stirred.word
                    val what = when {
                        word.kind == Word.Kind.DEED -> "выполнено"
                        word.exercisesCount > 0 -> "новая тренировка"
                        else -> "новое задание"
                    }
                    tell("${event.stirred.authorName} · $what", event.stirred.room)
                }
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

    private fun seesOwn(room: Aspect? = null): Boolean = when (val view = _state.value.view) {
        is View.Abode -> view.host.isEmpty()
        is View.Room -> room != null && view.host.isEmpty() && view.aspect == room
        null -> false
    }

    private fun refreshIfHome() {
        val view = _state.value.view
        if (view is View.Abode && view.host.isEmpty()) home()
    }

    private fun Underway.toItem() =
        if (doing) UnderwayItem(behestId, exercise, approach, begunAtNs, endedAtNs.takeIf { it != 0L }) else null

    private fun OutstandingBehest.toWaiting() = Waiting(room, behest.toItem(), counterpart, underway.toItem())

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
        private const val HISTORY_LIMIT = 200
        private const val TRAINING_PACE_MS = 500L
        private const val CALM_PACE_MS = 30_000L
        const val CELL = "Слово"
        const val GATES = "Врата"
        const val HELM = "Кормчая"

        fun kindSaid(kind: Kind): String? = when (kind) {
            // Знать человека и есть нижняя ступень: о ней не говорится, говорится только выше.
            Kind.Acquaintance -> null
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
