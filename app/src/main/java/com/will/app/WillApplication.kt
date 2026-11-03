package com.will.app

import android.app.Application
import android.content.Context

class WillApplication : Application() {
    val session: WillSession by lazy {
        WillSession(AndroidPhone(this), WillBridge(if (WillBridge.TLS) serverCertificate(this) else null))
    }
}


private fun serverCertificate(context: Context): String =
    try {
        context.assets.open("will_server.pem").bufferedReader().use { it.readText() }
    } catch (e: java.io.IOException) {
        throw IllegalStateException("В сборке нет сертификата сервера (assets/will_server.pem)", e)
    }
