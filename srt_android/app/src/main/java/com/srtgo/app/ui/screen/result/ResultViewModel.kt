package com.srtgo.app.ui.screen.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srtgo.app.core.model.Passenger
import com.srtgo.app.core.model.PassengerType
import com.srtgo.app.core.model.RailType
import com.srtgo.app.core.model.Reservation
import com.srtgo.app.core.model.SeatType
import com.srtgo.app.core.model.Train
import com.srtgo.app.data.repository.ReservationRepository
import com.srtgo.app.domain.usecase.SearchTrainsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class ResultUiState(
    val trains: List<Train> = emptyList(),
    val selectedIndices: Set<Int> = emptySet(),
    val autoPay: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val reservationResult: Reservation? = null,
    val railType: RailType = RailType.SRT,
    val departureStation: String = "",
    val departureCode: String = "",
    val arrivalStation: String = "",
    val arrivalCode: String = "",
    val date: String = "",
    val time: String = "",
    val seatType: SeatType = SeatType.GENERAL_FIRST,
    val passengers: List<Passenger> = listOf(Passenger(PassengerType.ADULT, 1))
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchTrainsUseCase: SearchTrainsUseCase,
    private val reservationRepository: ReservationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        val searchParamsJson = java.net.URLDecoder.decode(
            savedStateHandle.get<String>("searchParamsJson") ?: "{}",
            "UTF-8"
        )
        parseSearchParams(searchParamsJson)
        loadTrains()
    }

    private fun parseSearchParams(json: String) {
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

            _uiState.update {
                it.copy(
                    railType = railType,
                    departureStation = obj.optString("departureStation"),
                    departureCode = obj.optString("departureCode"),
                    arrivalStation = obj.optString("arrivalStation"),
                    arrivalCode = obj.optString("arrivalCode"),
                    date = obj.optString("date"),
                    time = obj.optString("time"),
                    seatType = seatType,
                    passengers = passengers
                )
            }
        } catch (_: Exception) {}
    }

    private fun loadTrains() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val state = _uiState.value
            val result = searchTrainsUseCase(
                railType = state.railType,
                departure = state.departureCode,
                arrival = state.arrivalCode,
                date = state.date,
                time = state.time,
                passengers = state.passengers,
                seatType = state.seatType
            )
            result.onSuccess { trains ->
                _uiState.update { it.copy(isLoading = false, trains = trains) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "열차 조회 실패")
                }
            }
        }
    }

    fun toggleTrainSelection(index: Int) {
        _uiState.update { state ->
            val newSelection = state.selectedIndices.toMutableSet()
            if (index in newSelection) {
                newSelection.remove(index)
            } else {
                newSelection.add(index)
            }
            state.copy(selectedIndices = newSelection)
        }
    }

    fun toggleAutoPay() {
        _uiState.update { it.copy(autoPay = !it.autoPay) }
    }

    fun reserveDirect(train: Train) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val state = _uiState.value
                val reservation = reservationRepository.reserve(
                    train = train,
                    passengers = state.passengers,
                    seatType = state.seatType
                )
                _uiState.update {
                    it.copy(isLoading = false, reservationResult = reservation)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "예약 실패")
                }
            }
        }
    }

    fun toMacroConfigJson(): String {
        val state = _uiState.value
        return JSONObject().apply {
            put("railType", state.railType.name)
            put("departureCode", state.departureCode)
            put("departureStation", state.departureStation)
            put("arrivalCode", state.arrivalCode)
            put("arrivalStation", state.arrivalStation)
            put("date", state.date)
            put("time", state.time)
            put("seatType", state.seatType.code)
            put("autoPay", state.autoPay)
            put("adultCount", state.passengers.find { it.type == PassengerType.ADULT }?.count ?: 1)
            put("childCount", state.passengers.find { it.type == PassengerType.CHILD }?.count ?: 0)
            put("seniorCount", state.passengers.find { it.type == PassengerType.SENIOR }?.count ?: 0)
            put("disabilityCount", state.passengers.find { it.type == PassengerType.DISABILITY_1_3 }?.count ?: 0)
            put("selectedTrains", org.json.JSONArray(state.selectedIndices.sorted().toList()))
        }.toString()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
