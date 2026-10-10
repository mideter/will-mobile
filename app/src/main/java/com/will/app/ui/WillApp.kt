package com.will.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.will.app.Aspect
import com.will.app.Connection
import com.will.app.View
import com.will.app.WillSession

/** Экраны из меню Обители; [Main] — вид из сессии (Обитель или комната). */
private enum class Screen { Main, Dwellings, Supplications, Lineage }

/**
 * Корень приложения: вид из состояния сессии, «назад» — к Обители. Уведомления сервера — во
 * всплывающем поле; связь не уведомление: без неё экран закрывает заставка ([Veil]).
 */
@Composable
fun WillApp(session: WillSession) {
    val state by session.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var screen by rememberSaveable { mutableStateOf(Screen.Main) }
    var going by remember { mutableStateOf(false) }

    LaunchedEffect(session) {
        session.notices.collect { snackbar.showSnackbar(it) }
    }

    // Связи нет — сразу поверх всего заставка: без связи с Центром нельзя ничего. Первое
    // подключение показывает свой экран, поэтому здесь — только когда уже было что показать.
    val lost = state.connection != Connection.Ready && (state.view != null || state.unbornMark != null)
    if (lost) Veil()
    val popups: @Composable () -> Unit = { SnackbarHost(snackbar) }

    // Нерождённый только ждёт.
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
                    Screen.Dwellings -> DwellingsScreen(state, session, onBack = back, onVisit = { host ->
                        session.visit(host)
                        screen = Screen.Main
                    })
                    Screen.Supplications -> SupplicationsScreen(state, session, onBack = back)
                    Screen.Lineage -> LineageScreen(state, session, onBack = back)
                    Screen.Main -> when (view) {
                        null -> ConnectingScreen()
                        is View.Abode -> AbodeScreen(state, view, session, menu = {
                            AbodeMenu(
                                waiting = state.supplications.size,
                                onOpen = { screen = it },
                                onGates = { going = true },
                            )
                        })
                        is View.Room -> when (view.aspect) {
                            Aspect.Threshold -> GatesScreen(state, view, session)
                            Aspect.Dwellers -> UpperRoomScreen(state, view, session)
                            Aspect.Words -> RoomScreen(state, view, session)
                        }
                    }
                }
            }
        }
    }

    if (going) {
        GoToGatesDialog(
            onGo = { host ->
                going = false
                session.gates(host)
            },
            onDismiss = { going = false },
        )
    }
}


/** Пойти к чужим Вратам по имени хозяина. */
@Composable
private fun GoToGatesDialog(onGo: (String) -> Unit, onDismiss: () -> Unit) {
    var host by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("К чужим Вратам") },
        text = {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                singleLine = true,
                placeholder = { Text("Имя хозяина") },
            )
        },
        confirmButton = { TextButton(onClick = { onGo(host) }, enabled = host.isNotBlank()) { Text("Пойти") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}


@Composable
private fun AbodeMenu(
    waiting: Int,
    onOpen: (Screen) -> Unit,
    onGates: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Text(if (waiting > 0) "⋯ $waiting" else "⋯", fontSize = 20.sp)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        // Свои комнаты — в списке Обители; здесь только то, чего там нет.
        DropdownMenuItem(text = { Text("К чужим Вратам…") }, onClick = { open = false; onGates() })
        DropdownMenuItem(text = { Text("Мои Обители") }, onClick = { open = false; onOpen(Screen.Dwellings) })
        DropdownMenuItem(
            text = { Text(if (waiting > 0) "Просьбы ($waiting)" else "Просьбы") },
            onClick = { open = false; onOpen(Screen.Supplications) },
        )
        // Отец по духу и линия — пока не открыты.
    }
}
