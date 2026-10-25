package com.will.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Тело ещё не рождено: только ждать. Метку видит хозяин в своих Вратах и рождает его своим
 * чадом; здесь об этом не говорится — только что без имени не начать, номер, по которому его
 * дадут, и что имя даёт тот, у кого оно есть. Что делать с номером, не велится: сюда приходят,
 * позванные тем, у кого имя есть, — он и спросит номер.
 */
@Composable
fun UnbornScreen(mark: Long) {
    NameFrame("Чтобы начать, нужно имя", below = "Имя даёт тот, у кого оно есть.") {
        Column(
            Modifier
                .fillMaxWidth()
                .plaque()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("#$mark", fontSize = 40.sp, fontWeight = FontWeight.Medium, color = WillColors.Accent)
            Text("ваш номер", fontSize = 13.sp, color = WillColors.Muted)
        }
    }
}

/**
 * Имя только что дано: номер на той же плашке стал именем, и сказано, кто его дал. Начинают,
 * коснувшись имени: экран ожидания сказал «чтобы начать, нужно имя», а «›» на плашке — тот же
 * знак входа, что у комнат. Словами об этом не говорится.
 */
@Composable
fun NamedScreen(name: String, father: String, onBegin: () -> Unit) {
    NameFrame("Теперь у вас есть имя", below = "Его дал $father.") {
        Row(
            Modifier
                .fillMaxWidth()
                .plaque()
                .clickable(onClick = onBegin)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 40.sp, fontWeight = FontWeight.Medium, color = WillColors.Accent)
                Text("ваше имя", fontSize = 13.sp, color = WillColors.Muted)
            }
            Text("›", fontSize = 18.sp, color = WillColors.Muted)
        }
    }
}

@Composable
private fun NameFrame(title: String, below: String, plaque: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        // Ожидание и имя — одно место: меняются только слова, всё стоит, где стояло.
        Spacer(Modifier.weight(1f))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(16.dp))
        plaque()
        Text(
            below,
            fontSize = 15.sp,
            color = WillColors.Muted,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        Spacer(Modifier.weight(1f))
    }
}
