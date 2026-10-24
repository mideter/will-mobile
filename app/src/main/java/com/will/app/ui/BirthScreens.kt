package com.will.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                .plaque()
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
