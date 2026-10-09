package com.will.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.will.app.ui.WillApp
import com.will.app.ui.WillTheme

class MainActivity : ComponentActivity() {

    private val session get() = (application as WillApplication).session

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Система выгрузила процесс, пока экран был в фоне: вернуться туда, где он был.
        val resumed = savedInstanceState?.let { it.getString(KEY_HOST).orEmpty() to it.getString(KEY_ROOM).orEmpty() }
        session.start(resumed ?: ("" to ""))
        setContent {
            WillTheme { WillApp(session) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val (host, room) = session.target
        outState.putString(KEY_HOST, host)
        outState.putString(KEY_ROOM, room)
    }

    private companion object {
        const val KEY_HOST = "will.host"
        const val KEY_ROOM = "will.room"
    }
}
