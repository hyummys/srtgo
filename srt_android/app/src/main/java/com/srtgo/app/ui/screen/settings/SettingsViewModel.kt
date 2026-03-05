package com.srtgo.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srtgo.app.core.model.RailType
import com.srtgo.app.data.repository.SettingsRepository
import com.srtgo.app.domain.usecase.LoginUseCase
import com.srtgo.app.util.TelegramNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoginStatus { NONE, TESTING, SUCCESS, FAILED }

data class SettingsUiState(
    val srtId: String = "",
    val srtPw: String = "",
    val srtLoginStatus: LoginStatus = LoginStatus.NONE,
    val ktxId: String = "",
    val ktxPw: String = "",
    val ktxLoginStatus: LoginStatus = LoginStatus.NONE,
    val cardNumber: String = "",
    val cardPassword: String = "",
    val cardBirthday: String = "",
    val cardExpire: String = "",
    val cardSaved: Boolean = false,
    val tgToken: String = "",
    val tgChatId: String = "",
    val tgStatus: LoginStatus = LoginStatus.NONE,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val loginUseCase: LoginUseCase,
    private val telegramNotifier: TelegramNotifier
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSavedSettings()
    }

    private fun loadSavedSettings() {
        viewModelScope.launch {
            val srtId = settingsRepository.get("SRT", "userId") ?: ""
            val srtPw = settingsRepository.get("SRT", "password") ?: ""
            val ktxId = settingsRepository.get("KTX", "userId") ?: ""
            val ktxPw = settingsRepository.get("KTX", "password") ?: ""

            val cardNumber = settingsRepository.getCardNumber() ?: ""
            val cardPassword = settingsRepository.getCardPassword() ?: ""
            val cardBirthday = settingsRepository.getCardBirthday() ?: ""
            val cardExpire = settingsRepository.getCardExpireDate() ?: ""

            val tgToken = settingsRepository.getTelegramToken() ?: ""
            val tgChatId = settingsRepository.getTelegramChatId() ?: ""

            _uiState.update {
                it.copy(
                    srtId = srtId,
                    srtPw = srtPw,
                    ktxId = ktxId,
                    ktxPw = ktxPw,
                    cardNumber = cardNumber,
                    cardPassword = cardPassword,
                    cardBirthday = cardBirthday,
                    cardExpire = cardExpire,
                    cardSaved = cardNumber.isNotEmpty(),
                    tgToken = tgToken,
                    tgChatId = tgChatId
                )
            }
        }
    }

    // SRT
    fun updateSrtId(value: String) {
        _uiState.update { it.copy(srtId = value, srtLoginStatus = LoginStatus.NONE) }
    }

    fun updateSrtPw(value: String) {
        _uiState.update { it.copy(srtPw = value, srtLoginStatus = LoginStatus.NONE) }
    }

    fun testSrtLogin() {
        val state = _uiState.value
        if (state.srtId.isBlank() || state.srtPw.isBlank()) {
            _uiState.update { it.copy(message = "SRT 아이디와 비밀번호를 입력하세요") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(srtLoginStatus = LoginStatus.TESTING) }
            val result = loginUseCase(RailType.SRT, state.srtId, state.srtPw)
            if (result.isSuccess) {
                _uiState.update { it.copy(srtLoginStatus = LoginStatus.SUCCESS, message = "SRT 로그인 성공") }
            } else {
                _uiState.update {
                    it.copy(
                        srtLoginStatus = LoginStatus.FAILED,
                        message = "SRT 로그인 실패: ${result.exceptionOrNull()?.message ?: "알 수 없는 오류"}"
                    )
                }
            }
        }
    }

    fun saveSrtCredential() {
        val state = _uiState.value
        if (state.srtId.isBlank() || state.srtPw.isBlank()) {
            _uiState.update { it.copy(message = "SRT 아이디와 비밀번호를 입력하세요") }
            return
        }
        viewModelScope.launch {
            settingsRepository.save("SRT", "userId", state.srtId, encrypt = true)
            settingsRepository.save("SRT", "password", state.srtPw, encrypt = true)
            _uiState.update { it.copy(message = "SRT 로그인 정보가 저장되었습니다") }
        }
    }

    // KTX
    fun updateKtxId(value: String) {
        _uiState.update { it.copy(ktxId = value, ktxLoginStatus = LoginStatus.NONE) }
    }

    fun updateKtxPw(value: String) {
        _uiState.update { it.copy(ktxPw = value, ktxLoginStatus = LoginStatus.NONE) }
    }

    fun testKtxLogin() {
        val state = _uiState.value
        if (state.ktxId.isBlank() || state.ktxPw.isBlank()) {
            _uiState.update { it.copy(message = "KTX 아이디와 비밀번호를 입력하세요") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(ktxLoginStatus = LoginStatus.TESTING) }
            val result = loginUseCase(RailType.KTX, state.ktxId, state.ktxPw)
            if (result.isSuccess) {
                _uiState.update { it.copy(ktxLoginStatus = LoginStatus.SUCCESS, message = "KTX 로그인 성공") }
            } else {
                _uiState.update {
                    it.copy(
                        ktxLoginStatus = LoginStatus.FAILED,
                        message = "KTX 로그인 실패: ${result.exceptionOrNull()?.message ?: "알 수 없는 오류"}"
                    )
                }
            }
        }
    }

    fun saveKtxCredential() {
        val state = _uiState.value
        if (state.ktxId.isBlank() || state.ktxPw.isBlank()) {
            _uiState.update { it.copy(message = "KTX 아이디와 비밀번호를 입력하세요") }
            return
        }
        viewModelScope.launch {
            settingsRepository.save("KTX", "userId", state.ktxId, encrypt = true)
            settingsRepository.save("KTX", "password", state.ktxPw, encrypt = true)
            _uiState.update { it.copy(message = "KTX 로그인 정보가 저장되었습니다") }
        }
    }

    // Card
    fun updateCardNumber(value: String) {
        _uiState.update { it.copy(cardNumber = value) }
    }

    fun updateCardPassword(value: String) {
        if (value.length <= 2) {
            _uiState.update { it.copy(cardPassword = value) }
        }
    }

    fun updateCardBirthday(value: String) {
        if (value.length <= 6) {
            _uiState.update { it.copy(cardBirthday = value) }
        }
    }

    fun updateCardExpire(value: String) {
        if (value.length <= 4) {
            _uiState.update { it.copy(cardExpire = value) }
        }
    }

    fun saveCard() {
        val state = _uiState.value
        if (state.cardNumber.isBlank()) {
            _uiState.update { it.copy(message = "카드번호를 입력하세요") }
            return
        }
        viewModelScope.launch {
            settingsRepository.saveCardInfo(
                cardNumber = state.cardNumber,
                cardPassword = state.cardPassword,
                birthday = state.cardBirthday,
                expireDate = state.cardExpire
            )
            _uiState.update { it.copy(cardSaved = true, message = "카드 정보가 저장되었습니다") }
        }
    }

    // Telegram
    fun updateTgToken(value: String) {
        _uiState.update { it.copy(tgToken = value, tgStatus = LoginStatus.NONE) }
    }

    fun updateTgChatId(value: String) {
        _uiState.update { it.copy(tgChatId = value, tgStatus = LoginStatus.NONE) }
    }

    fun testTelegram() {
        val state = _uiState.value
        if (state.tgToken.isBlank() || state.tgChatId.isBlank()) {
            _uiState.update { it.copy(message = "봇 토큰과 채팅 ID를 입력하세요") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(tgStatus = LoginStatus.TESTING) }
            val success = telegramNotifier.sendMessage(
                state.tgToken, state.tgChatId, "[SRTgo] 테스트 메시지입니다."
            )
            if (success) {
                _uiState.update { it.copy(tgStatus = LoginStatus.SUCCESS, message = "테스트 메시지 전송 성공") }
            } else {
                _uiState.update { it.copy(tgStatus = LoginStatus.FAILED, message = "테스트 메시지 전송 실패") }
            }
        }
    }

    fun saveTelegram() {
        val state = _uiState.value
        if (state.tgToken.isBlank() || state.tgChatId.isBlank()) {
            _uiState.update { it.copy(message = "봇 토큰과 채팅 ID를 입력하세요") }
            return
        }
        viewModelScope.launch {
            settingsRepository.saveTelegramConfig(state.tgToken, state.tgChatId)
            _uiState.update { it.copy(message = "텔레그램 설정이 저장되었습니다") }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
