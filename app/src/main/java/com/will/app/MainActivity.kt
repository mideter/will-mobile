package com.will.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.will.app.ui.WillApp
import com.will.app.ui.WillTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = (application as WillApplication).session
        session.start()
        setContent {
            WillTheme { WillApp(session) }
        }
    }
}
