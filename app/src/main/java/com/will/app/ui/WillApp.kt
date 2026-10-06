package com.will.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.will.app.View
import com.will.app.WillSession

/** Корень приложения: вид из состояния сессии, уведомления — снекбаром, «назад» — к Обители. */
@Composable
fun WillApp(session: WillSession) {
    val state by session.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(session) {
        session.notices.collect { snackbar.showSnackbar(it) }
    }

    val view = state.view
    BackHandler(enabled = view is View.Room || (view != null && view.host.isNotEmpty())) {
        session.back()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when (view) {
                null -> Centered("Подключение к серверу…")
                is View.Abode -> AbodeScreen(state, view, session, menu = {})
                is View.Room -> RoomScreen(state, view, session)
            }
        }
    }
}
