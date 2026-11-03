package com.will.app

import will.v1.MessengerOuterClass.ClientEvent
import will.v1.MessengerOuterClass.ServerEvent

interface Transport {
    fun connect(deviceToken: String, onEvent: (ServerEvent) -> Unit, onClosed: (String) -> Unit)

    fun send(event: ClientEvent): Boolean

    fun disconnect()
}
