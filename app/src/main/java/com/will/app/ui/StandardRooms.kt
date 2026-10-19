package com.will.app.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.Kind
import com.will.app.Standing
import com.will.app.View
import com.will.app.WillSession
import com.will.app.WillState

/**
 * Врата — порог Обители: через них входят рождённые и рождаются. Хозяин, стоя в них, держит
 * их открытыми: видит, кто ждёт, и впускает знакомым; видит нерождённых и рождает их своими
 * чадами. Пришедший видит, открыты ли Врата, и ждёт сколько хочет.
 */
@Composable
fun GatesScreen(state: WillState, view: View.Room, session: WillSession) {
    val own = view.host.isEmpty()
    Column(Modifier.fillMaxSize()) {
        Header(
            title = "Врата",
            subtitle = if (own) "Моя Обитель" else "Обитель ${view.host}",
            onBack = { session.back() },
        )

        if (view.keeping) {
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
            }
        } else {
            Text(
                if (view.gatesOpen) "Врата открыты." else "Врата закрыты: хозяина нет у Врат.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            Hint(
                if (view.gatesOpen) {
                    "Ждите, пока вас впустят."
                } else {
                    "Можно ждать здесь: когда хозяин подойдёт к Вратам, он увидит вас."
                },
            )
        }
    }
}

/**
 * Горница: обитатели Обители и её комнаты. Видит их всякий, кто стоит здесь; меняет статусы,
 * переносит комнаты и просит стать Тренером только хозяин.
 */
@Composable
fun UpperRoomScreen(state: WillState, view: View.Room, session: WillSession) {
    val own = view.host.isEmpty()
    LaunchedEffect(own) { if (own) session.listDwellings() }
    val mutual = state.dwellings.map { it.name }.toSet()

    Column(Modifier.fillMaxSize()) {
        Header(
            title = "Горница",
            subtitle = if (own) "Мои обитатели" else "Обитатели ${view.host}",
            onBack = { session.back() },
        )
        if (view.people.isEmpty()) {
            Hint(if (own) "В вашей Обители никто не обитает. Впускают у Врат." else "Здесь никто не обитает")
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(view.people, key = { it.name }) { dweller ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(dweller.name, fontSize = 16.sp)
                    if (own) {
                        Row {
                            for (kind in Kind.entries) {
                                val chosen = kind == dweller.kind
                                TextButton(onClick = { if (!chosen) session.regard(dweller.name, kind) }) {
                                    Text(
                                        WillSession.kindName(kind),
                                        color = if (chosen) WillColors.Accent else WillColors.Muted,
                                        fontWeight = if (chosen) FontWeight.Medium else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    } else {
                        Text(WillSession.kindName(dweller.kind), fontSize = 13.sp, color = WillColors.Muted)
                    }
                    if (own) {
                        // Как он стоит ко мне в тренерстве; просить — только где есть смысл.
                        val standings = dweller.standings
                        if (Standing.Trainer in standings) Text("Ваш Тренер", fontSize = 13.sp, color = WillColors.Accent)
                        if (Standing.Novice in standings) Text("Ваш ученик", fontSize = 13.sp, color = WillColors.Accent)
                        if (Standing.Asked in standings) {
                            Text("Просьба отправлена, ждёт ответа", fontSize = 13.sp, color = WillColors.Muted)
                        }
                        if (Standing.Asks in standings) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Просит вас стать его Тренером", fontSize = 13.sp, color = WillColors.Accent, modifier = Modifier.weight(1f))
                                TextButton(onClick = { session.accept(dweller.name) }) { Text("Принять") }
                                TextButton(onClick = { session.reject(dweller.name) }) { Text("Отклонить", color = WillColors.Muted) }
                            }
                        }
                        if (dweller.name in mutual && Standing.Trainer !in standings && Standing.Asked !in standings) {
                            TextButton(onClick = { session.ask(dweller.name) }) { Text("Просить стать Тренером") }
                        }
                    }
                }
                HorizontalDivider(color = WillColors.Divider)
            }
            if (view.rooms.isNotEmpty()) {
                item { SectionTitle("Комнаты") }
                item { Hint("Внутренние видят только друзья; внешние — ещё и ближние.") }
                items(view.rooms, key = { "room:" + it.name }) { room ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(room.name, fontSize = 16.sp)
                            Text(
                                if (room.outer) "внешняя часть" else "внутренняя часть",
                                fontSize = 13.sp,
                                color = WillColors.Muted,
                            )
                        }
                        if (own) {
                            TextButton(onClick = { session.arrange(room.name, outer = !room.outer) }) {
                                Text(if (room.outer) "Во внутреннюю" else "Во внешнюю")
                            }
                        }
                    }
                    HorizontalDivider(color = WillColors.Divider)
                }
            }
        }
    }
}
