package com.srtgo.app.ui.screen.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srtgo.app.core.model.RailType
import com.srtgo.app.core.model.Reservation
import com.srtgo.app.data.repository.ReservationRepository
import com.srtgo.app.data.repository.SettingsRepository
import com.srtgo.app.domain.usecase.GetReservationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReservationsUiState(
    val reservations: List<Reservation> = emptyList(),
    val selectedRailType: RailType = RailType.SRT,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ReservationsViewModel @Inject constructor(
    private val getReservationsUseCase: GetReservationsUseCase,
    private val reservationRepository: ReservationRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReservationsUiState())
    val uiState: StateFlow<ReservationsUiState> = _uiState.asStateFlow()

    init {
        loadReservations()
    }

    fun setRailType(railType: RailType) {
        _uiState.update { it.copy(selectedRailType = railType) }
        loadReservations()
    }

    fun loadReservations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = getReservationsUseCase(_uiState.value.selectedRailType)
            result.onSuccess { reservations ->
                _uiState.update { it.copy(isLoading = false, reservations = reservations) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "예약 조회 실패")
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val result = getReservationsUseCase(_uiState.value.selectedRailType)
            result.onSuccess { reservations ->
                _uiState.update { it.copy(isRefreshing = false, reservations = reservations) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isRefreshing = false, error = e.message ?: "새로고침 실패")
                }
            }
        }
    }

    fun pay(reservation: Reservation) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val cardNumber = settingsRepository.getCardNumber()
                val cardPassword = settingsRepository.getCardPassword()
                val birthday = settingsRepository.getCardBirthday()
                val expireDate = settingsRepository.getCardExpireDate()

                if (cardNumber.isNullOrEmpty() || cardPassword.isNullOrEmpty() ||
                    birthday.isNullOrEmpty() || expireDate.isNullOrEmpty()
                ) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "카드 정보가 설정되지 않았습니다. 설정에서 카드 정보를 입력하세요.")
                    }
                    return@launch
                }

                reservationRepository.payWithCard(
                    reservation, cardNumber, cardPassword, birthday, expireDate
                )
                _uiState.update {
                    it.copy(isLoading = false, successMessage = "결제가 완료되었습니다")
                }
                loadReservations()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "결제 실패")
                }
            }
        }
    }

    fun cancel(reservation: Reservation) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                if (reservation.isPaid) {
                    reservationRepository.refund(reservation)
                    _uiState.update {
                        it.copy(isLoading = false, successMessage = "환불이 완료되었습니다")
                    }
                } else {
                    reservationRepository.cancel(reservation)
                    _uiState.update {
                        it.copy(isLoading = false, successMessage = "예약이 취소되었습니다")
                    }
                }
                loadReservations()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "취소 실패")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }
}
