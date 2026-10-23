package com.will.app

import will.v1.MessengerOuterClass.ClientEvent
import will.v1.MessengerOuterClass.ServerEvent

/**
 * Связь сессии с сервером: подключиться телом с этим токеном — события сервера и обрыв
 * приходят на главный поток, — отправить событие, разорвать. Разорванная сама об обрыве уже
 * не говорит. На деле это [WillBridge]; в тестах связь подделывают.
 */
interface Transport {
    fun connect(deviceToken: String, onEvent: (ServerEvent) -> Unit, onClosed: (String) -> Unit)

    fun send(event: ClientEvent): Boolean

    fun disconnect()
}
