package com.srtgo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

private const val VISIBLE_ITEMS = 5
private val ITEM_HEIGHT = 44.dp

@Composable
fun WheelTimePicker(
    isVisible: Boolean,
    initialHour: Int = 0,
    initialMinute: Int = 0,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    val hourState = rememberLazyListState()
    val minuteState = rememberLazyListState()

    // Large number for "infinite" scroll illusion
    val hourCount = 24
    val minuteCount = 60
    val multiplier = 500
    val hourTotal = hourCount * multiplier
    val minuteTotal = minuteCount * multiplier
    val hourCenter = (hourTotal / 2) - ((hourTotal / 2) % hourCount) + initialHour
    val minuteCenter = (minuteTotal / 2) - ((minuteTotal / 2) % minuteCount) + initialMinute

    val padding = (VISIBLE_ITEMS / 2)

    val selectedHour by remember {
        derivedStateOf {
            val idx = hourState.firstVisibleItemIndex + padding
            idx % hourCount
        }
    }
    val selectedMinute by remember {
        derivedStateOf {
            val idx = minuteState.firstVisibleItemIndex + padding
            idx % minuteCount
        }
    }

    LaunchedEffect(Unit) {
        hourState.scrollToItem(hourCenter - padding)
        minuteState.scrollToItem(minuteCenter - padding)
    }

    // Snap to nearest item after scroll stops
    LaunchedEffect(hourState) {
        snapshotFlow { hourState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling) {
                    val target = hourState.firstVisibleItemIndex + padding
                    hourState.animateScrollToItem(target - padding)
                }
            }
    }
    LaunchedEffect(minuteState) {
        snapshotFlow { minuteState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling) {
                    val target = minuteState.firstVisibleItemIndex + padding
                    minuteState.animateScrollToItem(target - padding)
                }
            }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Scrim tap = dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* consume click so it doesn't pass to scrim */ },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with cancel/confirm only
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("취소", fontSize = 17.sp)
                        }
                        Text(
                            "시간 선택",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = {
                            onTimeSelected(selectedHour, selectedMinute)
                            onDismiss()
                        }) {
                            Text("확인", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Wheel area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ITEM_HEIGHT * VISIBLE_ITEMS)
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Selection highlight
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ITEM_HEIGHT)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(10.dp)
                                )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hour wheel
                            Box(modifier = Modifier.width(80.dp)) {
                                LazyColumn(
                                    state = hourState,
                                    modifier = Modifier.height(ITEM_HEIGHT * VISIBLE_ITEMS)
                                ) {
                                    items(hourTotal) { index ->
                                        val hour = index % hourCount
                                        val distFromCenter = abs(index - (hourState.firstVisibleItemIndex + padding))
                                        val alpha = when (distFromCenter) {
                                            0 -> 1f
                                            1 -> 0.5f
                                            else -> 0.2f
                                        }
                                        Box(
                                            modifier = Modifier
                                                .height(ITEM_HEIGHT)
                                                .fillMaxWidth()
                                                .alpha(alpha),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = String.format("%02d", hour),
                                                fontSize = if (distFromCenter == 0) 22.sp else 18.sp,
                                                fontWeight = if (distFromCenter == 0) FontWeight.Bold else FontWeight.Normal,
                                                color = if (distFromCenter == 0) MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            // Separator
                            Text(
                                text = ":",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            // Minute wheel
                            Box(modifier = Modifier.width(80.dp)) {
                                LazyColumn(
                                    state = minuteState,
                                    modifier = Modifier.height(ITEM_HEIGHT * VISIBLE_ITEMS)
                                ) {
                                    items(minuteTotal) { index ->
                                        val minute = index % minuteCount
                                        val distFromCenter = abs(index - (minuteState.firstVisibleItemIndex + padding))
                                        val alpha = when (distFromCenter) {
                                            0 -> 1f
                                            1 -> 0.5f
                                            else -> 0.2f
                                        }
                                        Box(
                                            modifier = Modifier
                                                .height(ITEM_HEIGHT)
                                                .fillMaxWidth()
                                                .alpha(alpha),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = String.format("%02d", minute),
                                                fontSize = if (distFromCenter == 0) 22.sp else 18.sp,
                                                fontWeight = if (distFromCenter == 0) FontWeight.Bold else FontWeight.Normal,
                                                color = if (distFromCenter == 0) MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
