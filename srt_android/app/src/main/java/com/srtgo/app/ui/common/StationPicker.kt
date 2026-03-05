package com.srtgo.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.srtgo.app.core.model.RailType
import com.srtgo.app.core.model.Station

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationPicker(
    isVisible: Boolean,
    railType: RailType,
    favoriteStations: List<String> = emptyList(),
    onStationSelected: (String, String) -> Unit,
    onToggleFavorite: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val allStations = when (railType) {
        RailType.SRT -> Station.SRT_STATIONS
        RailType.KTX -> Station.KTX_STATIONS
    }

    val filteredStations = if (searchQuery.isBlank()) {
        allStations.toList()
    } else {
        allStations.filter { (name, _) ->
            name.contains(searchQuery, ignoreCase = true)
        }.toList()
    }

    val favoriteList = filteredStations.filter { (name, _) -> name in favoriteStations }
    val otherList = filteredStations.filter { (name, _) -> name !in favoriteStations }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "역 선택",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("역 이름 검색") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.height(400.dp)) {
                if (favoriteList.isNotEmpty()) {
                    item {
                        Text(
                            text = "즐겨찾기",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(favoriteList) { (name, code) ->
                        StationItem(
                            name = name,
                            isFavorite = true,
                            onSelect = { onStationSelected(name, code) },
                            onToggleFavorite = { onToggleFavorite(name) }
                        )
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                }

                items(otherList) { (name, code) ->
                    StationItem(
                        name = name,
                        isFavorite = false,
                        onSelect = { onStationSelected(name, code) },
                        onToggleFavorite = { onToggleFavorite(name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StationItem(
    name: String,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (isFavorite) "즐겨찾기 해제" else "즐겨찾기",
                tint = if (isFavorite) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
