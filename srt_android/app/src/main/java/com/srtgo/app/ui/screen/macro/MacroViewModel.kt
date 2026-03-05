package com.srtgo.app.ui.screen.macro

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srtgo.app.core.model.Passenger
import com.srtgo.app.core.model.PassengerType
import com.srtgo.app.core.model.RailType
import com.srtgo.app.core.model.Reservation
import com.srtgo.app.core.model.SeatType
import com.srtgo.app.domain.usecase.MacroConfig
import com.srtgo.app.domain.usecase.MacroState
import com.srtgo.app.domain.usecase.MacroStatus
import com.srtgo.app.domain.usecase.RunMacroUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class MacroUiState(
    val status: MacroStatus = MacroStatus.SEARCHING,
    val attempts: Int = 0,
    val elapsedMs: Long = 0,
    val errorLog: List<String> = emptyList(),
    val reservation: Reservation? = null,
    val isRunning: Boolean = false,
    val departureStation: String = "",
    val arrivalStation: String = "",
    val date: String = "",
    val railType: RailType = RailType.SRT
)

@HiltViewModel
class MacroViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val runMacroUseCase: RunMacroUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MacroUiState())
    val uiState: StateFlow<MacroUiState> = _uiState.asStateFlow()

    private var macroJob: Job? = null
    private var macroConfig: MacroConfig? = null

    init {
        val macroConfigJson = java.net.URLDecoder.decode(
            savedStateHandle.get<String>("macroConfigJson") ?: "{}",
            "UTF-8"
        )
        parseMacroConfig(macroConfigJson)
        startMacro()
    }

    private fun parseMacroConfig(json: String) {
        try {
            val obj = JSONObject(json)
            val railType = RailType.valueOf(obj.optString("railType", "SRT"))
            val seatType = SeatType.fromCode(obj.optInt("seatType", 1))
            val passengers = buildList {
                val adult = obj.optInt("adultCount", 1)
                val child = obj.optInt("childCount", 0)
                val senior = obj.optInt("seniorCount", 0)
                val disability = obj.optInt("disabilityCount", 0)
                if (adult > 0) add(Passenger(PassengerType.ADULT, adult))
                if (child > 0) add(Passenger(PassengerType.CHILD, child))
                if (senior > 0) add(Passenger(PassengerType.SENIOR, senior))
                if (disability > 0) add(Passenger(PassengerType.DISABILITY_1_3, disability))
            }

            val selectedTrains = mutableListOf<Int>()
            val trainsArray = obj.optJSONArray("selectedTrains")
            if (trainsArray != null) {
                for (i in 0 until trainsArray.length()) {
                    selectedTrains.add(trainsArray.optInt(i))
                }
            }

            macroConfig = MacroConfig(
                railType = railType,
                departure = obj.optString("departureCode"),
                arrival = obj.optString("arrivalCode"),
                date = obj.optString("date"),
                time = obj.optString("time"),
                passengers = passengers,
                seatType = seatType,
                selectedTrainIndices = selectedTrains,
                autoPay = obj.optBoolean("autoPay", false)
            )

            _uiState.update {
                it.copy(
                    railType = railType,
                    departureStation = obj.optString("departureStation"),
                    arrivalStation = obj.optString("arrivalStation"),
                    date = obj.optString("date")
                )
            }
        } catch (_: Exception) {}
    }

    fun startMacro() {
        val config = macroConfig ?: return
        macroJob?.cancel()
        _uiState.update { it.copy(isRunning = true, errorLog = emptyList()) }

        macroJob = viewModelScope.launch {
            runMacroUseCase(config).collect { state ->
                handleMacroState(state)
            }
        }
    }

    private fun handleMacroState(state: MacroState) {
        _uiState.update { current ->
            val newErrorLog = if (state.lastError != null) {
                (current.errorLog + state.lastError).takeLast(20)
            } else {
                current.errorLog
            }

            current.copy(
                status = state.status,
                attempts = state.attempts,
                elapsedMs = state.elapsedMs,
                errorLog = newErrorLog,
                reservation = state.reservation,
                isRunning = state.status == MacroStatus.SEARCHING
            )
        }
    }

    fun cancelMacro() {
        macroJob?.cancel()
        _uiState.update {
            it.copy(
                status = MacroStatus.CANCELLED,
                isRunning = false
            )
        }
    }

    override fun onCleared() {
        macroJob?.cancel()
        super.onCleared()
    }
}
