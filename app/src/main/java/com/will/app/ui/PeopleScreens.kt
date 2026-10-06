package com.will.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.WillSession
import com.will.app.WillState

/** Обители, где я обитаю: хозяин и как он меня видит. */
@Composable
fun DwellingsScreen(state: WillState, session: WillSession, onBack: () -> Unit, onVisit: (String) -> Unit) {
    LaunchedEffect(Unit) { session.listDwellings() }

    Column(Modifier.fillMaxSize()) {
        Header(title = "Мои Обители", subtitle = "Где я обитаю", onBack = onBack)
        if (state.dwellings.isEmpty()) Hint("Вас ещё никто не принял")
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.dwellings, key = { it.name }) { dwelling ->
                Row(
                    Modifier.fillMaxWidth().clickable { onVisit(dwelling.name) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Обитель ${dwelling.name}", fontSize = 16.sp)
                        Text("Я здесь — ${WillSession.kindName(dwelling.kind)}", fontSize = 13.sp, color = WillColors.Muted)
                    }
                    Text("›", fontSize = 18.sp, color = WillColors.Muted)
                }
                HorizontalDivider(color = WillColors.Divider)
            }
        }
    }
}

/** Прошения, ждущие моего ответа. */
@Composable
fun SupplicationsScreen(state: WillState, session: WillSession, onBack: () -> Unit) {
    LaunchedEffect(Unit) { session.listSupplications() }

    Column(Modifier.fillMaxSize()) {
        Header(title = "Прошения", subtitle = "Просят стать их Тренером", onBack = onBack)
        if (state.supplications.isEmpty()) Hint("Прошений нет")
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.supplications, key = { it }) { suppliant ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(suppliant, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { session.accept(suppliant) }) { Text("Принять") }
                    TextButton(onClick = { session.reject(suppliant) }) { Text("Отклонить", color = WillColors.Muted) }
                }
                HorizontalDivider(color = WillColors.Divider)
            }
        }
    }
}
