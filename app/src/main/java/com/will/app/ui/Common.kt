package com.will.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Header(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Заголовок всегда у края; путь назад — сама подпись: она говорит, где я, туда и ведёт.
            val onClick = onBack ?: onTitleClick
            Column(
                modifier = Modifier
                    .weight(1f)
                    .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                    .padding(horizontal = 8.dp),
            ) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        if (onBack != null) "‹ $subtitle" else subtitle,
                        fontSize = 13.sp,
                        color = WillColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.End) { actions() }
        }
        HorizontalDivider(color = WillColors.Divider)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = WillColors.Muted,
    )
}

@Composable
fun Hint(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 14.sp, color = WillColors.Muted)
}

@Composable
fun <T> Group(items: List<T>, background: Color = WillColors.Row, row: @Composable (T) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = WillColors.Divider)
            row(item)
        }
    }
}
