package com.will.app

import android.app.Application

/** Сессия живёт, пока живёт приложение: поворот экрана её не рвёт. */
class WillApplication : Application() {
    val session: WillSession by lazy { WillSession(this) }
}
