package com.will.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.will.app.Aspect
import com.will.app.Connection
import com.will.app.View
import com.will.app.WillSession

/** [Main] — вид из сессии (Обитель или комната). */
private enum class Screen { Main, Lineage }

/**
 * Корень приложения: вид из состояния сессии, «назад» — тем путём, каким пришли. Уведомления сервера — во
 * всплывающем поле; связь не уведомление: без неё экран закрывает заставка ([Veil]).
 */
@Composable
fun WillApp(session: WillSession) {
    val state by session.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var screen by rememberSaveable { mutableStateOf(Screen.Main) }
    var abodeTab by rememberSaveable { mutableStateOf(AbodeTab.Deeds) }

    LaunchedEffect(session) {
        session.notices.collect { snackbar.showSnackbar(it) }
    }

    // Связи нет — сразу поверх всего заставка: без связи с Центром нельзя ничего. Первое
    // подключение показывает свой экран, поэтому здесь — только когда уже было что показать.
    val lost = state.connection != Connection.Ready && (state.view != null || state.unbornMark != null)
    if (lost) Veil()
    val popups: @Composable () -> Unit = { SnackbarHost(snackbar) }

    state.unbornMark?.let { mark ->
        Scaffold(snackbarHost = popups) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { UnbornScreen(mark) }
        }
        return
    }

    val view = state.view
    BackHandler(enabled = screen != Screen.Main) { screen = Screen.Main }
    BackHandler(enabled = screen == Screen.Main && (view is View.Room || (view != null && view.host.isNotEmpty()))) {
        session.back()
    }

    val back = { screen = Screen.Main }
    Scaffold(snackbarHost = popups) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Box(Modifier.weight(1f)) {
                when (screen) {
                    Screen.Lineage -> LineageScreen(state, session, onBack = back)
                    Screen.Main -> when (view) {
                        null -> ConnectingScreen()
                        is View.Abode -> AbodeScreen(state, view, session, abodeTab) { abodeTab = it }
                        is View.Room -> when (view.aspect) {
                            Aspect.Threshold -> GatesScreen(state, view, session)
                            Aspect.Dwellers -> HelmScreen(state, view, session)
                            Aspect.Words -> RoomScreen(state, view, session)
                        }
                    }
                }
            }
        }
    }
}

