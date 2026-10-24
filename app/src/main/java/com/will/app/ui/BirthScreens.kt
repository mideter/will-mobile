package com.will.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.Descent
import com.will.app.WillSession
import com.will.app.WillState

/**
 * Тело ещё не рождено: только ждать. Метку видит хозяин в своих Вратах и рождает его своим
 * чадом; здесь об этом не говорится — только что делать: без имени дальше нельзя, а номер, по
 * которому его дадут, — на той же плашке, что у всех «где я».
 */
@Composable
fun UnbornScreen(mark: Long) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Чтобы начать, нужно имя",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(WillColors.Row)
                .border(1.dp, WillColors.Outline, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("#$mark", fontSize = 40.sp, fontWeight = FontWeight.Medium, color = WillColors.Accent)
            Text("ваш номер", fontSize = 13.sp, color = WillColors.Muted)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Назовите этот номер тому, у кого имя уже есть.",
            fontSize = 15.sp,
            color = WillColors.Muted,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
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
