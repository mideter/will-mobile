package com.will.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.Aspect
import com.will.app.Kind
import com.will.app.Person
import com.will.app.Standing
import com.will.app.View
import com.will.app.WillSession
import com.will.app.WillState

/**
 * Врата — порог Обители и ядро её внешней части: через них выходят и входят, через них и
 * рождаются. В своих хозяин держит их открытыми: видит, кто ждёт, и впускает знакомым; видит
 * нерождённых и рождает их своими чадами; отсюда же уходит — в Обители, где обитает, или к
 * чужим Вратам по имени. У чужих Врат обитатель входит внутрь, а пришедший ждёт, пока впустят.
 */
@Composable
fun GatesScreen(state: WillState, view: View.Room, session: WillSession) {
    val own = view.host.isEmpty()
    Column(Modifier.fillMaxSize()) {
        Header(
            title = if (own) "Врата" else "Врата — ${view.host}",
            // Из своих Врат назад — домой, из чужих — к своим Вратам: подпись говорит, куда.
            subtitle = if (own) state.ownName else "Врата — ${state.ownName}",
            onBack = { session.back() },
        )
        if (own) OwnGates(state, view, session) else OthersGates(state, view, session)
    }
}

@Composable
private fun OwnGates(state: WillState, view: View.Room, session: WillSession) {
    LaunchedEffect(Unit) { session.listDwellings() }
    var going by remember { mutableStateOf(false) }

    Hint("Врата открыты, пока вы здесь.")
    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionTitle("У Врат") }
        if (view.waiting.isEmpty()) item { Hint("Никто не ждёт") }
        items(view.waiting, key = { "waiting $it" }) { name ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { session.admit(name) }) { Text("Впустить") }
            }
            HorizontalDivider(color = WillColors.Divider)
        }

        // Через эти же Врата рождаются: отцом по плоти станет их хозяин.
        item { SectionTitle("Ждут рождения") }
        item { Hint("Рождённый здесь станет вашим чадом по плоти.") }
        if (view.unborn.isEmpty()) item { Hint("Никто не ждёт") }
        items(view.unborn, key = { "unborn $it" }) { mark ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#$mark", fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { session.bear(mark) }) { Text("Родить") }
            }
            HorizontalDivider(color = WillColors.Divider)
        }

        // Из Обители выходят только через свои Врата.
        item { SectionTitle("Выйти") }
        if (state.dwellings.isEmpty()) item { Hint("Вас ещё никто не принял") }
        items(state.dwellings, key = { "dwelling ${it.name}" }) { dwelling ->
            Row(
                Modifier.fillMaxWidth().clickable { session.gates(dwelling.name) }.padding(horizontal = 16.dp, vertical = 14.dp),
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
        item {
            TextButton(onClick = { going = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("К чужим Вратам…")
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

@Composable
private fun OthersGates(state: WillState, view: View.Room, session: WillSession) {
    val dwelling = state.dwellings.firstOrNull { it.name == view.host }
    Text(
        if (view.gatesOpen) "Врата открыты: хозяин у Врат." else "Врата закрыты: хозяина нет у Врат.",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
    )
    if (dwelling != null) {
        Hint("Вы обитаете здесь — ${WillSession.kindName(dwelling.kind)}.")
        TextButton(onClick = { session.visit(view.host) }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Войти")
        }
    } else {
        Hint(
            if (view.gatesOpen) {
                "Хозяин видит вас: ждите, пока впустит."
            } else {
                "Можно ждать здесь: подойдя к Вратам, хозяин увидит вас."
            },
        )
    }
}

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

/**
 * Кормчая — ядро внутренней части, только хозяина: Просьбы к нему, обитатели и их статусы,
 * Кельи и их части. Здесь он отвечает на Просьбы, просит сам, меняет статусы и переносит Кельи.
 */
@Composable
fun HelmScreen(state: WillState, view: View.Room, session: WillSession) {
    LaunchedEffect(Unit) { session.listDwellings() }
    val mutual = state.dwellings.map { it.name }.toSet()

    var open by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Header(title = "Кормчая", subtitle = state.ownName, onBack = { session.back() })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (state.supplications.isNotEmpty()) {
                item { SectionTitle("Просьбы") }
                item {
                    Group(state.supplications, WillColors.SupplicationRow) { suppliant ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(suppliant, fontSize = 16.sp)
                                Text("Просит вас стать Тренером", fontSize = 13.sp, color = WillColors.Supplication)
                            }
                            TextButton(onClick = { session.accept(suppliant) }) { Text("Принять") }
                            TextButton(onClick = { session.reject(suppliant) }) { Text("Отклонить", color = WillColors.Muted) }
                        }
                    }
                }
            }

            item { SectionTitle("Обитатели") }
            if (view.people.isEmpty()) item { Hint("В вашей Обители никто не обитает. Впускают у Врат.") }
            else item {
                Group(view.people) { dweller ->
                    DwellerRow(
                        dweller = dweller,
                        open = open == dweller.name,
                        mayAsk = dweller.name in mutual,
                        onToggle = { open = if (open == dweller.name) null else dweller.name },
                        session = session,
                    )
                }
            }

            item { Hint("Во внутреннюю часть входят друзья, во внешнюю — и ближние.") }
            for ((title, outer) in listOf("Внутренняя часть" to false, "Внешняя часть" to true)) {
                // Ядро части — Врата или Кормчая — впереди её Келий, и его не переносят.
                val rooms = view.rooms.filter { it.outer == outer }.sortedBy { it.aspect == Aspect.Words }
                if (rooms.isEmpty()) continue
                item { SectionTitle(title) }
                item {
                    Group(rooms) { room ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(room.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            if (room.aspect == Aspect.Words) {
                                TextButton(onClick = { session.arrange(room.name, outer = !outer) }) {
                                    Text(if (outer) "во внутреннюю" else "во внешнюю", color = WillColors.Muted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DwellerRow(dweller: Person, open: Boolean, mayAsk: Boolean, onToggle: () -> Unit, session: WillSession) {
    val standings = dweller.standings
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(dweller.name, fontSize = 16.sp)
                val told = listOf(WillSession.kindName(dweller.kind)) + listOfNotNull(
                    "Тренер".takeIf { Standing.Trainer in standings },
                    "ученик".takeIf { Standing.Novice in standings },
                    "просит".takeIf { Standing.Asks in standings },
                    "просьба отправлена".takeIf { Standing.Asked in standings },
                )
                Text(told.joinToString(" · "), fontSize = 13.sp, color = WillColors.Muted)
            }
            Text("›", fontSize = 18.sp, color = WillColors.Muted, modifier = Modifier.rotate(if (open) 90f else 0f))
        }
        if (open) {
            Row(Modifier.padding(start = 4.dp)) {
                for (kind in Kind.entries) {
                    val chosen = kind == dweller.kind
                    Choice(WillSession.kindName(kind), if (chosen) WillColors.Accent else WillColors.Muted) {
                        if (!chosen) session.regard(dweller.name, kind)
                    }
                }
            }
            // Кто просит меня, тому отвечают, а не просят встречно.
            val asking = Standing.Trainer in standings || Standing.Asked in standings || Standing.Asks in standings
            if (mayAsk && !asking) {
                Choice("Просить стать Тренером", WillColors.Accent, Modifier.padding(start = 4.dp)) { session.ask(dweller.name) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun Choice(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 15.sp,
        color = color,
        modifier = modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
