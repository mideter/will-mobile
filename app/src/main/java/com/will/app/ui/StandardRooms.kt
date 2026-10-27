package com.will.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.Kind
import com.will.app.Person
import com.will.app.Standing
import com.will.app.View
import com.will.app.WillSession
import com.will.app.WillState

/**
 * Врата — порог Обители, самое внешнее её: через них выходят и входят, через них и рождаются.
 * В своих хозяин держит их открытыми: видит, кто стучит, и впускает знакомым; видит
 * нерождённых и рождает их своими чадами; отсюда же уходит к другим по имени — к тем, кто
 * знает его, сразу внутрь, к прочим — к их Вратам. У чужих Врат знакомый входит внутрь, а
 * пришедший — ему это просто место по имени хозяина — может постучать, раз, пока стоит здесь.
 * Всё — группами, как в обзоре; пустое не показывается.
 */
@Composable
fun GatesScreen(state: WillState, view: View.Room, session: WillSession) {
    val own = view.host.isEmpty()
    Column(Modifier.fillMaxSize()) {
        Header(
            title = if (own) "Врата" else view.host,
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

    // Пустая секция не говорит о себе: пока никого нет, нет и её.
    val listState = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().goneUnder(listState), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
        if (view.waiting.isNotEmpty()) {
            item { SectionTitle("Стучат") }
            // Кто стучит, тот ждёт ответа — как Просьба, красным.
            item {
                Group(view.waiting, WillColors.SupplicationRow) { name ->
                    Acting(name, "Впустить", { Text("Стучит", fontSize = 13.sp, color = WillColors.Supplication) }) {
                        session.admit(name)
                    }
                }
            }
        }

        // Через эти же Врата рождаются: отцом по плоти станет их хозяин.
        if (view.unborn.isNotEmpty()) {
            item { SectionTitle("Дать имя") }
            item { Group(view.unborn) { mark -> Acting("#$mark", "Дать") { session.bear(mark) } } }
            item { Hint("Кому вы дадите имя, у того вы всегда будете хотя бы ближним.") }
        }

        // К другим ходят только из своих Врат.
        item { SectionTitle("Посетить") }
        item {
            Group(state.dwellings + null) { dwelling ->
                if (dwelling == null) {
                    if (!going) {
                        Line("по имени…") { going = true }
                    } else {
                        ByName { host ->
                            going = false
                            session.visit(host)
                        }
                    }
                } else {
                    val known = listOfNotNull("знает вас", WillSession.kindSaid(dwelling.kind)).joinToString(" · ")
                    Line(dwelling.name, { Text(known, fontSize = 13.sp, color = WillColors.Muted) }) { session.visit(dwelling.name) }
                }
            }
        }
    }

    BackHandler(enabled = going) { going = false }
}

@Composable
private fun Acting(title: String, action: String, below: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            below?.invoke()
        }
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun OthersGates(state: WillState, view: View.Room, session: WillSession) {
    // Кого здесь знают, тот входит сам — хозяин ему не нужен; кого не знают, тот стучит — раз:
    // стук слышен, пока он стоит здесь.
    val known = state.dwellings.any { it.name == view.host }
    Column(Modifier.padding(top = 12.dp)) {
        Group(listOf(known)) {
            when {
                known -> Line("Войти") { session.visit(view.host) }
                !view.knocking -> Line("Стук") { session.knock() }
                else -> Text(
                    if (view.gatesOpen) "Хозяин слышит ваш стук." else "Вы постучали.",
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun ByName(onGo: (String) -> Unit) {
    var host by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val go = { if (host.isNotBlank()) onGo(host.trim()) }
    Row(
        Modifier.fillMaxWidth().keptInSight().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = host,
            onValueChange = { host = it },
            textStyle = TextStyle(fontSize = 16.sp, color = WillColors.Ink),
            cursorBrush = SolidColor(WillColors.Accent),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { go() }),
            singleLine = true,
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { inner ->
                if (host.isEmpty()) Text("Имя", fontSize = 16.sp, color = WillColors.Muted)
                inner()
            },
        )
        TextButton(onClick = go, enabled = host.isNotBlank()) { Text("Посетить") }
    }
}

/**
 * Кормчая — самое внутреннее Обители, только хозяина: Просьбы к нему, обитатели и их статусы,
 * комнаты, внутренние и внешние. Здесь он отвечает на Просьбы, просит сам, меняет статусы и
 * переносит комнаты внутрь и наружу.
 */
@Composable
fun HelmScreen(state: WillState, view: View.Room, session: WillSession) {
    LaunchedEffect(Unit) { session.listDwellings() }
    val mutual = state.dwellings.map { it.name }.toSet()

    var open by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Header(title = "Кормчая", subtitle = state.ownName, onBack = { session.back() })
        val listState = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize().goneUnder(listState), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
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

            item { SectionTitle("Знаю") }
            if (view.people.isEmpty()) item { Hint("Вы пока никого не знаете. Впускают у Врат.") }
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

            item { Hint("Внутреннее видят друзья, внешнее — и ближние.") }
            for ((title, outer) in listOf("Внутреннее" to false, "Внешнее" to true)) {
                val rooms = view.rooms.filter { it.outer == outer }
                if (rooms.isEmpty()) continue
                item { SectionTitle(title) }
                item {
                    Group(rooms) { room ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(room.said, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { session.arrange(room.name, outer = !outer) }) {
                                Text(if (outer) "внутрь" else "наружу", color = WillColors.Muted)
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
                val told = listOfNotNull(
                    WillSession.kindSaid(dweller.kind),
                    "Тренер".takeIf { Standing.Trainer in standings },
                    "ученик".takeIf { Standing.Novice in standings },
                    "просит".takeIf { Standing.Asks in standings },
                    "просьба отправлена".takeIf { Standing.Asked in standings },
                )
                if (told.isNotEmpty()) Text(told.joinToString(" · "), fontSize = 13.sp, color = WillColors.Muted)
            }
            Text("›", fontSize = 18.sp, color = WillColors.Muted, modifier = Modifier.rotate(if (open) 90f else 0f))
        }
        if (open) {
            Row(Modifier.padding(start = 4.dp)) {
                for (kind in Kind.entries) {
                    val chosen = kind == dweller.kind
                    Choice(WillSession.kindSaid(kind) ?: "знаю", if (chosen) WillColors.Accent else WillColors.Muted) {
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
