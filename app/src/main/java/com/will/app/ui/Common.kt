package com.will.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.will.app.Connection

/** Шапка экрана: «назад», заголовок с подписью и действия справа. */
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
            if (onBack != null) {
                TextButton(onClick = onBack) { Text("←", fontSize = 20.sp) }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack == null) 8.dp else 0.dp)
                    .let { if (onTitleClick != null) it.clickable(onClick = onTitleClick) else it },
            ) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 13.sp, color = WillColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.End) { actions() }
        }
        HorizontalDivider(color = WillColors.Divider)
    }
}

/** Полоса о связи над любым экраном, пока связь не готова; одна на всё приложение — в [WillApp]. */
@Composable
fun ConnectionBanner(connection: Connection) {
    val text = when (connection) {
        Connection.Ready -> return
        Connection.Connecting -> "Подключение к серверу…"
        Connection.Reconnecting -> "Переподключение…"
    }
    Text(
        text,
        modifier = Modifier.fillMaxWidth().background(WillColors.Row).padding(horizontal = 16.dp, vertical = 6.dp),
        fontSize = 13.sp,
        color = WillColors.Muted,
    )
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
