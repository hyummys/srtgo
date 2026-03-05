package com.srtgo.app.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srtgo.app.core.model.Passenger
import com.srtgo.app.core.model.PassengerType
import com.srtgo.app.core.model.RailType
import com.srtgo.app.core.model.SeatType
import com.srtgo.app.core.model.Station
import com.srtgo.app.core.model.Train
import com.srtgo.app.domain.usecase.SearchTrainsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class SearchUiState(
    val railType: RailType = RailType.SRT,
    val departureStation: String = "",
    val departureCode: String = "",
    val arrivalStation: String = "",
    val arrivalCode: String = "",
    val date: String = "",
    val time: String = "",
    val adultCount: Int = 1,
    val childCount: Int = 0,
    val seniorCount: Int = 0,
    val disabilityCount: Int = 0,
    val seatType: SeatType = SeatType.GENERAL_FIRST,
    val isLoading: Boolean = false,
    val error: String? = null,
    val trains: List<Train>? = null,
    val favoriteStations: List<String> = emptyList()
) {
    val formattedDate: String
        get() {
            if (date.length != 8) return date
            return "${date.substring(0, 4)}.${date.substring(4, 6)}.${date.substring(6, 8)}"
        }

    val formattedTime: String
        get() {
            if (time.length != 6) return time
            return "${time.substring(0, 2)}:${time.substring(2, 4)}"
        }

    val totalPassengers: Int
        get() = adultCount + childCount + seniorCount + disabilityCount

    fun toPassengerList(): List<Passenger> {
        return buildList {
            if (adultCount > 0) add(Passenger(PassengerType.ADULT, adultCount))
            if (childCount > 0) add(Passenger(PassengerType.CHILD, childCount))
            if (seniorCount > 0) add(Passenger(PassengerType.SENIOR, seniorCount))
            if (disabilityCount > 0) add(Passenger(PassengerType.DISABILITY_1_3, disabilityCount))
        }
    }

    fun toSearchParamsJson(): String {
        val json = JSONObject().apply {
            put("railType", railType.name)
            put("departureStation", departureStation)
            put("departureCode", departureCode)
            put("arrivalStation", arrivalStation)
            put("arrivalCode", arrivalCode)
            put("date", date)
            put("time", time)
            put("adultCount", adultCount)
            put("childCount", childCount)
            put("seniorCount", seniorCount)
            put("disabilityCount", disabilityCount)
            put("seatType", seatType.code)
        }
        return json.toString()
    }
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchTrainsUseCase: SearchTrainsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        setDefaultDateTime()
        setDefaultStations()
    }

    private fun setDefaultDateTime() {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREA)
        val timeFormat = SimpleDateFormat("HHmmss", Locale.KOREA)
        _uiState.update {
            it.copy(
                date = dateFormat.format(calendar.time),
                time = timeFormat.format(calendar.time)
            )
        }
    }

    private fun setDefaultStations() {
        val defaultDep = when (_uiState.value.railType) {
            RailType.SRT -> "수서" to (Station.SRT_STATIONS["수서"] ?: "")
            RailType.KTX -> "서울" to (Station.KTX_STATIONS["서울"] ?: "")
        }
        val defaultArr = when (_uiState.value.railType) {
            RailType.SRT -> "부산" to (Station.SRT_STATIONS["부산"] ?: "")
            RailType.KTX -> "부산" to (Station.KTX_STATIONS["부산"] ?: "")
        }
        _uiState.update {
            it.copy(
                departureStation = defaultDep.first,
                departureCode = defaultDep.second,
                arrivalStation = defaultArr.first,
                arrivalCode = defaultArr.second
            )
        }
    }

    fun setRailType(railType: RailType) {
        _uiState.update { it.copy(railType = railType, error = null) }
        setDefaultStations()
    }

    fun setDepartureStation(name: String, code: String) {
        _uiState.update { it.copy(departureStation = name, departureCode = code, error = null) }
    }

    fun setArrivalStation(name: String, code: String) {
        _uiState.update { it.copy(arrivalStation = name, arrivalCode = code, error = null) }
    }

    fun swapStations() {
        _uiState.update {
            it.copy(
                departureStation = it.arrivalStation,
                departureCode = it.arrivalCode,
                arrivalStation = it.departureStation,
                arrivalCode = it.departureCode,
                error = null
            )
        }
    }

    fun setDate(date: String) {
        _uiState.update { it.copy(date = date, error = null) }
    }

    fun setTime(time: String) {
        _uiState.update { it.copy(time = time, error = null) }
    }

    fun adjustPassenger(type: PassengerType, delta: Int) {
        _uiState.update { state ->
            val newState = when (type) {
                PassengerType.ADULT -> state.copy(adultCount = (state.adultCount + delta).coerceIn(0, 9))
                PassengerType.CHILD -> state.copy(childCount = (state.childCount + delta).coerceIn(0, 9))
                PassengerType.SENIOR -> state.copy(seniorCount = (state.seniorCount + delta).coerceIn(0, 9))
                PassengerType.DISABILITY_1_3, PassengerType.DISABILITY_4_6 ->
                    state.copy(disabilityCount = (state.disabilityCount + delta).coerceIn(0, 9))
            }
            if (newState.totalPassengers == 0) {
                newState.copy(adultCount = 1)
            } else {
                newState
            }
        }
    }

    fun setSeatType(seatType: SeatType) {
        _uiState.update { it.copy(seatType = seatType, error = null) }
    }

    fun search() {
        val state = _uiState.value
        if (state.departureStation.isBlank() || state.arrivalStation.isBlank()) {
            _uiState.update { it.copy(error = "출발역과 도착역을 선택하세요") }
            return
        }
        if (state.departureStation == state.arrivalStation) {
            _uiState.update { it.copy(error = "출발역과 도착역이 같습니다") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, trains = null) }
            val result = searchTrainsUseCase(
                railType = state.railType,
                departure = state.departureCode,
                arrival = state.arrivalCode,
                date = state.date,
                time = state.time,
                passengers = state.toPassengerList(),
                seatType = state.seatType
            )
            result.onSuccess { trains ->
                _uiState.update { it.copy(isLoading = false, trains = trains) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "검색 실패")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
