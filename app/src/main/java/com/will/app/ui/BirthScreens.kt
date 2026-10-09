package com.will.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.Descent
import com.will.app.View
import com.will.app.WillSession
import com.will.app.WillState

/** Тело ещё не рождено: только ждать. Метку видят в Родильных. */
@Composable
fun UnbornScreen(mark: Long) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Вы ещё не рождены", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(24.dp))
        Text("#$mark", fontSize = 48.sp, fontWeight = FontWeight.Medium, color = WillColors.Accent)
        Spacer(Modifier.height(24.dp))
        Text(
            "По этой метке вас видят в Родильных. Назовите её тому, кто вас родит: " +
                "отцом по плоти станет хозяин Родильной.",
            textAlign = TextAlign.Center,
            color = WillColors.Muted,
        )
    }
}

/**
 * Родильная: нерождённые по меткам. Стоящий здесь рождает; отцом по плоти станет хозяин
 * Родильной. Здесь бывает только один.
 */
@Composable
fun BirthRoomScreen(state: WillState, view: View.Room, session: WillSession) {
    val own = view.host.isEmpty()
    Column(Modifier.fillMaxSize()) {
        Header(
            title = "Родильная",
            subtitle = if (own) "Моя Обитель" else "Обитель ${view.host}",
            onBack = { session.back() },
        )
        Hint(
            if (own) {
                "Рождённый здесь станет вашим чадом по плоти."
            } else {
                "Рождённый здесь станет чадом ${view.host}, а не вашим."
            },
        )
        SectionTitle("Ждут рождения")
        if (view.unborn.isEmpty()) Hint("Никто не ждёт")
        LazyColumn(Modifier.fillMaxSize()) {
            items(view.unborn, key = { it }) { mark ->
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
    }
}

/** Моя духовная линия: чада по духу, их чада и так далее. */
@Composable
fun LineageScreen(state: WillState, session: WillSession, onBack: () -> Unit) {
    LaunchedEffect(Unit) { session.listLineage() }

    // Поколение — по цепочке отцов до меня.
    val fathers = state.lineage.associate { it.name to it.father }
    fun depth(descent: Descent): Int {
        var level = 0
        var father: String? = descent.father
        while (father != null && father != state.ownName && level < 64) {
            father = fathers[father]
            level++
        }
        return level
    }
    // Дерево: под каждым отцом — его чада.
    val children = state.lineage.groupBy { it.father }
    val ordered = buildList {
        fun walk(father: String) {
            for (child in children[father].orEmpty()) {
                add(child)
                walk(child.name)
            }
        }
        walk(state.ownName)
    }

    Column(Modifier.fillMaxSize()) {
        Header(title = "Моя линия", subtitle = "Чада по духу", onBack = onBack)
        if (ordered.isEmpty()) Hint("Никто не избрал вас отцом по духу")
        LazyColumn(Modifier.fillMaxSize()) {
            items(ordered, key = { it.name }) { descent ->
                Text(
                    descent.name,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = (16 + 20 * depth(descent)).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                )
                HorizontalDivider(color = WillColors.Divider)
            }
        }
    }
}
