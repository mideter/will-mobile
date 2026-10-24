package com.will.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Шапка — подпись к месту, а не предмет в нём: без плашки. Назад ведёт подпись «‹ куда» —
            // синяя, как всякое действие; дома касание имени копирует его. Заголовок стоит по линии
            // текста групп и не прыгает.
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .let { if (onTitleClick != null) it.clickable(onClick = onTitleClick) else it }
                        .padding(horizontal = 16.dp),
                )
                if (subtitle != null) {
                    Text(
                        if (onBack != null) "‹ $subtitle" else subtitle,
                        fontSize = 14.sp,
                        color = if (onBack != null) WillColors.Accent else WillColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .let { if (onBack != null) it.clickable(onClick = onBack) else it }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.End) { actions() }
        }
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

@Composable
fun Line(title: String, below: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (below == null) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            below?.invoke()
        }
        Text("›", fontSize = 18.sp, color = WillColors.Muted)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.keptInSight(): Modifier {
    // Поле в ленте: клавиатура поднимается после него и сжимает экран — обрезанная краем строка
    // выходит на виду.
    val sight = remember { BringIntoViewRequester() }
    var cut by remember { mutableStateOf(false) }
    LaunchedEffect(cut) { if (cut) sight.bringIntoView() }
    return bringIntoViewRequester(sight).onGloballyPositioned { cut = it.boundsInRoot().height < it.size.height }
}

fun Modifier.plaque(): Modifier =
    clip(RoundedCornerShape(12.dp)).background(WillColors.Row)

fun Modifier.goneUnder(list: LazyListState): Modifier =
    drawWithContent {
        drawContent()
        // Лента, ушедшая под шапку, отделяется от неё чертой; в покое их разделяет воздух.
        if (list.canScrollBackward) drawLine(WillColors.Outline, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
    }

fun Modifier.hiddenBeneath(list: LazyListState): Modifier =
    drawWithContent {
        drawContent()
        // Недосмотренное, скрытое под низом экрана, отделяется от него чертой.
        if (list.canScrollForward) drawLine(WillColors.Outline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    }
