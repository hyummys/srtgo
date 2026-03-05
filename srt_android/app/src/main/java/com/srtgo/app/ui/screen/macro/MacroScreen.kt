package com.srtgo.app.ui.screen.macro

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srtgo.app.core.model.Reservation
import com.srtgo.app.domain.usecase.MacroStatus
import com.srtgo.app.ui.theme.Gray
import com.srtgo.app.ui.theme.Green
import com.srtgo.app.ui.theme.Red

@Composable
fun MacroScreen(
    onNavigateBack: () -> Unit,
    viewModel: MacroViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val elapsedSeconds = uiState.elapsedMs / 1000
    val formattedElapsed = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)

    Scaffold(
        floatingActionButton = {
            if (uiState.isRunning) {
                FloatingActionButton(
                    onClick = { viewModel.cancelMacro() },
                    containerColor = Red
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "취소",
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "[${uiState.railType.name}] ${uiState.departureStation} → ${uiState.arrivalStation}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val date = uiState.date
                    if (date.length == 8) {
                        Text(
                            text = "${date.substring(4, 6)}/${date.substring(6, 8)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Counter
            AnimatedContent(
                targetState = uiState.attempts,
                label = "attempts"
            ) { attempts ->
                Text(
                    text = attempts.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                    fontWeight = FontWeight.Bold,
                    color = when (uiState.status) {
                        MacroStatus.SUCCESS -> Green
                        MacroStatus.FAILED -> Red
                        MacroStatus.CANCELLED -> Gray
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }

            Text(
                text = "시도 횟수",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Elapsed Time
            Text(
                text = formattedElapsed,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status Indicator
            StatusIndicator(status = uiState.status)

            Spacer(modifier = Modifier.height(16.dp))

            // Success Reservation Card
            if (uiState.status == MacroStatus.SUCCESS && uiState.reservation != null) {
                ReservationResultCard(reservation = uiState.reservation!!)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Done button for terminal states
            if (!uiState.isRunning && uiState.status != MacroStatus.SEARCHING) {
                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("돌아가기")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Error Log
            if (uiState.errorLog.isNotEmpty()) {
                Text(
                    text = "오류 로그",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                ) {
                    items(uiState.errorLog) { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = Red,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(status: MacroStatus) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        when (status) {
            MacroStatus.SEARCHING -> {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .alpha(alpha)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "좌석 검색 중...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            MacroStatus.SUCCESS -> {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "예매 성공!",
                    style = MaterialTheme.typography.titleMedium,
                    color = Green,
                    fontWeight = FontWeight.Bold
                )
            }
            MacroStatus.FAILED -> {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = Red,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "예매 실패",
                    style = MaterialTheme.typography.titleMedium,
                    color = Red
                )
            }
            MacroStatus.CANCELLED -> {
                Icon(
                    Icons.Default.Cancel,
                    contentDescription = null,
                    tint = Gray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "취소됨",
                    style = MaterialTheme.typography.titleMedium,
                    color = Gray
                )
            }
        }
    }
}

@Composable
private fun ReservationResultCard(reservation: Reservation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "예약 완료",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${reservation.trainName} ${reservation.trainNumber}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "${reservation.depStationName} ${reservation.formattedDepTime} → " +
                        "${reservation.arrStationName} ${reservation.formattedArrTime}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "예약번호: ${reservation.reservationNumber}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${reservation.totalCost}원 (${reservation.seatCount}석)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
