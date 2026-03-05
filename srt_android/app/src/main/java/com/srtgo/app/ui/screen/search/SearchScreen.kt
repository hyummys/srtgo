package com.srtgo.app.ui.screen.search

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.srtgo.app.core.model.PassengerType
import com.srtgo.app.core.model.RailType
import com.srtgo.app.core.model.SeatType
import com.srtgo.app.ui.common.StationPicker
import com.srtgo.app.ui.common.WheelTimePicker
import com.srtgo.app.ui.theme.KtxPurple
import com.srtgo.app.ui.theme.SrtOrange
import java.util.Calendar

@Composable
fun SearchScreen(
    onNavigateToResult: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeparturePicker by remember { mutableStateOf(false) }
    var showArrivalPicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // SRT/KTX Tab
            RailTypeSelector(
                selectedRailType = uiState.railType,
                onRailTypeSelected = { viewModel.setRailType(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Station Selection
            StationSelectionCard(
                departureStation = uiState.departureStation,
                arrivalStation = uiState.arrivalStation,
                onDepartureClick = { showDeparturePicker = true },
                onArrivalClick = { showArrivalPicker = true },
                onSwap = { viewModel.swapStations() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Date & Time
            DateTimeCard(
                date = uiState.formattedDate,
                time = uiState.formattedTime,
                onDateSelected = { viewModel.setDate(it) },
                onTimeClick = { showTimePicker = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Passenger Counts
            PassengerCard(
                adultCount = uiState.adultCount,
                childCount = uiState.childCount,
                seniorCount = uiState.seniorCount,
                disabilityCount = uiState.disabilityCount,
                onAdjust = { type, delta -> viewModel.adjustPassenger(type, delta) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Seat Type
            SeatTypeSelector(
                selectedSeatType = uiState.seatType,
                onSeatTypeSelected = { viewModel.setSeatType(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Search Button
            Button(
                onClick = {
                    viewModel.validateAndGetParams()?.let { params ->
                        onNavigateToResult(params)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "열차 조회",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Station Pickers
    StationPicker(
        isVisible = showDeparturePicker,
        railType = uiState.railType,
        favoriteStations = uiState.favoriteStations,
        onStationSelected = { name, code ->
            viewModel.setDepartureStation(name, code)
            showDeparturePicker = false
        },
        onToggleFavorite = { viewModel.toggleFavorite(it) },
        onDismiss = { showDeparturePicker = false }
    )

    StationPicker(
        isVisible = showArrivalPicker,
        railType = uiState.railType,
        favoriteStations = uiState.favoriteStations,
        onStationSelected = { name, code ->
            viewModel.setArrivalStation(name, code)
            showArrivalPicker = false
        },
        onToggleFavorite = { viewModel.toggleFavorite(it) },
        onDismiss = { showArrivalPicker = false }
    )

    // Wheel Time Picker
    val currentHour = if (uiState.time.length >= 2) uiState.time.substring(0, 2).toIntOrNull() ?: 0 else 0
    val currentMinute = if (uiState.time.length >= 4) uiState.time.substring(2, 4).toIntOrNull() ?: 0 else 0
    WheelTimePicker(
        isVisible = showTimePicker,
        initialHour = currentHour,
        initialMinute = currentMinute,
        onTimeSelected = { hour, minute ->
            viewModel.setTime(String.format("%02d%02d00", hour, minute))
        },
        onDismiss = { showTimePicker = false }
    )
}

@Composable
private fun RailTypeSelector(
    selectedRailType: RailType,
    onRailTypeSelected: (RailType) -> Unit
) {
    val selectedIndex = if (selectedRailType == RailType.KTX) 0 else 1
    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Tab(
            selected = selectedIndex == 0,
            onClick = { onRailTypeSelected(RailType.KTX) },
            text = {
                Text(
                    "KTX",
                    fontWeight = if (selectedIndex == 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedIndex == 0) KtxPurple else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        Tab(
            selected = selectedIndex == 1,
            onClick = { onRailTypeSelected(RailType.SRT) },
            text = {
                Text(
                    "SRT",
                    fontWeight = if (selectedIndex == 1) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedIndex == 1) SrtOrange else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}

@Composable
private fun StationSelectionCard(
    departureStation: String,
    arrivalStation: String,
    onDepartureClick: () -> Unit,
    onArrivalClick: () -> Unit,
    onSwap: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onDepartureClick),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "출발",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = departureStation.ifBlank { "선택" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (departureStation.isBlank()) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(onClick = onSwap) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "역 교환",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onArrivalClick),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "도착",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = arrivalStation.ifBlank { "선택" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (arrivalStation.isBlank()) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DateTimeCard(
    date: String,
    time: String,
    onDateSelected: (String) -> Unit,
    onTimeClick: () -> Unit
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val dateStr = String.format("%04d%02d%02d", year, month + 1, dayOfMonth)
                                onDateSelected(dateStr)
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "날짜",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = date,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            OutlinedCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTimeClick() },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "시간",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = time,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun PassengerCard(
    adultCount: Int,
    childCount: Int,
    seniorCount: Int,
    disabilityCount: Int,
    onAdjust: (PassengerType, Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "승객",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            PassengerCounter("어른/청소년", adultCount, { onAdjust(PassengerType.ADULT, it) })
            PassengerCounter("어린이", childCount, { onAdjust(PassengerType.CHILD, it) })
            PassengerCounter("경로", seniorCount, { onAdjust(PassengerType.SENIOR, it) })
            PassengerCounter("장애", disabilityCount, { onAdjust(PassengerType.DISABILITY_1_3, it) })
        }
    }
}

@Composable
private fun PassengerCounter(
    label: String,
    count: Int,
    onAdjust: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onAdjust(-1) }, enabled = count > 0) {
            Icon(Icons.Default.Remove, contentDescription = "감소")
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        IconButton(onClick = { onAdjust(1) }, enabled = count < 9) {
            Icon(Icons.Default.Add, contentDescription = "증가")
        }
    }
}

@Composable
private fun SeatTypeSelector(
    selectedSeatType: SeatType,
    onSeatTypeSelected: (SeatType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "좌석 유형",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Box {
                FilledTonalButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedSeatType.displayName)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    SeatType.entries.forEach { seatType ->
                        DropdownMenuItem(
                            text = { Text(seatType.displayName) },
                            onClick = {
                                onSeatTypeSelected(seatType)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
