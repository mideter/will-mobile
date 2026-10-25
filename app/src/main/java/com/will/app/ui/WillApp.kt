package com.will.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.will.app.Aspect
import com.will.app.Connection
import com.will.app.Notice
import com.will.app.View
import com.will.app.WillSession

private const val NOTICE_MILLIS = 3_000L

/**
 * Корень приложения: вид из состояния сессии, «назад» — тем путём, каким пришли. Уведомления сервера — во
 * всплывающем поле; связь не уведомление: без неё экран закрывает заставка ([Veil]).
 */
@Composable
fun WillApp(session: WillSession) {
    val state by session.state.collectAsState()
    var notice by remember { mutableStateOf<Notice?>(null) }
    var lastNotice by remember { mutableStateOf<Notice?>(null) }

    LaunchedEffect(session) {
        // Новое сменяет прежнее и держится своё время заново.
        session.notices.collectLatest {
            notice = it
            lastNotice = it
            delay(NOTICE_MILLIS)
            notice = null
        }
    }

    // Связи нет — сразу поверх всего заставка: без связи с Центром нельзя ничего. Первое
    // подключение показывает свой экран, поэтому здесь — только когда уже было что показать.
    val lost = state.connection != Connection.Ready && (state.view != null || state.unbornMark != null)
    if (lost) Veil()
    // Сверху, под шапкой: внизу поле ввода и пульт, их всплывающее не закрывает.
    val popups: @Composable () -> Unit = {
        AnimatedVisibility(
            visible = notice != null,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut(),
            modifier = Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 64.dp),
        ) {
            lastNotice?.let { shown ->
                NoticeCard(shown, session.leads(shown), onClick = {
                    session.follow(shown)
                    notice = null
                })
            }
        }
    }

    state.unbornMark?.let { mark ->
        Box(Modifier.fillMaxSize()) {
            Scaffold { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) { UnbornScreen(mark) }
            }
            popups()
        }
        return
    }

    state.namedBy?.let { father ->
        Box(Modifier.fillMaxSize()) {
            Scaffold { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) { NamedScreen(state.ownName, father, session::begin) }
            }
            popups()
        }
        return
    }

    val view = state.view
    BackHandler(enabled = view is View.Room || (view != null && view.host.isNotEmpty())) {
        session.back()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                Box(Modifier.weight(1f)) {
                    when (view) {
                        null -> ConnectingScreen()
                        is View.Abode -> AbodeScreen(state, view, session)
                        is View.Room -> when (view.aspect) {
                            Aspect.Threshold -> GatesScreen(state, view, session)
                            Aspect.Dwellers -> HelmScreen(state, view, session)
                            Aspect.Words -> RoomScreen(state, view, session)
                        }
                    }
                }
            }
        }
        popups()
    }
}

