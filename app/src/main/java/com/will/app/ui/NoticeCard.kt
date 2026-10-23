package com.will.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.Notice

/** Всплывающее — светлая скруглённая карточка, как группы обзора; если ведёт куда-то — со «›». */
@Composable
fun NoticeCard(notice: Notice, leads: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, shape)
            .background(WillColors.Background, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(notice.text, fontSize = 15.sp, color = WillColors.Ink, modifier = Modifier.weight(1f))
        if (leads) Text("›", fontSize = 18.sp, color = WillColors.Muted, modifier = Modifier.padding(start = 8.dp))
    }
}
