package com.will.app

import will.v1.MessengerOuterClass.ClientEvent
import will.v1.MessengerOuterClass.ServerEvent
import will.v1.authOk
import will.v1.historyEnd
import will.v1.reply
import will.v1.serverEvent
import will.v1.turned

/** Телефон для тестов: время идёт рукой ([pass]), и отложенное выполняется, когда его время пришло. */
class FakePhone : Phone {
    override val deviceToken = "token"
    override var network: Any? = WIFI

    var nowMs = 1_000L
        private set
    var trainingsLed = 0
        private set
    val logged = mutableListOf<String>()

    private var available: (Any) -> Unit = {}
    private var lost: (Any) -> Unit = {}
    private val timers = mutableListOf<Pair<Long, Runnable>>()

    override fun watchNetwork(onAvailable: (Any) -> Unit, onLost: (Any) -> Unit) {
        available = onAvailable
        lost = onLost
    }

    fun becomeAvailable(network: Any) = available(network)

    fun lose(network: Any) = lost(network)

    override fun uptimeMs() = nowMs

    override fun postDelayed(action: Runnable, delayMs: Long) {
        timers += nowMs + delayMs to action
    }

    override fun cancel(action: Runnable) {
        timers.removeAll { it.second === action }
    }

    fun pass(ms: Long) {
        val until = nowMs + ms
        while (true) {
            val due = timers.filter { it.first <= until }.minByOrNull { it.first } ?: break
            timers.remove(due)
            nowMs = due.first
            due.second.run()
        }
        nowMs = until
    }

    override fun leadTraining() {
        trainingsLed++
    }

    override fun log(message: String) {
        logged += message
    }

    companion object {
        const val WIFI = "wifi"
        const val MOBILE = "mobile"
    }
}


/** Связь для тестов: помнит отправленное, а сервер в ней говорит и рвёт её рукой. */
class FakeTransport : Transport {
    var connections = 0
        private set
    var disconnections = 0
        private set
    val sent = mutableListOf<ClientEvent>()

    private var open = false
    private var onEvent: (ServerEvent) -> Unit = {}
    private var onClosed: (String) -> Unit = {}

    override fun connect(deviceToken: String, onEvent: (ServerEvent) -> Unit, onClosed: (String) -> Unit) {
        connections++
        open = true
        this.onEvent = onEvent
        this.onClosed = onClosed
    }

    override fun send(event: ClientEvent): Boolean {
        if (open) sent += event
        return open
    }

    override fun disconnect() {
        open = false
        disconnections++
    }

    fun tell(event: ServerEvent) = onEvent(event)

    fun close(reason: String) {
        open = false
        onClosed(reason)
    }

    fun welcome(name: String = "anna") = tell(serverEvent { authOk = authOk { this.name = name } })

    fun answer(event: ClientEvent) = tell(serverEvent { reply = reply { requestId = event.requestId; granted = true } })

    fun refuse(event: ClientEvent) = tell(serverEvent { reply = reply { requestId = event.requestId; granted = false } })

    fun show(host: String, room: String) {
        tell(serverEvent { turned = turned { abodeOf = host; this.room = room } })
        tell(serverEvent { historyEnd = historyEnd {} })
    }

    fun beats() = sent.filter { it.hasHeartbeat() }
}


fun session(phone: FakePhone, transport: FakeTransport) =
    WillSession(phone, transport, ServerClock(elapsedNs = { phone.nowMs * 1_000_000 }, wallNs = { phone.nowMs * 1_000_000 }))
