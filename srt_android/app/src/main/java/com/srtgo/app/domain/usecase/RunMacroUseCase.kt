package com.srtgo.app.domain.usecase

import com.srtgo.app.core.model.Passenger
import com.srtgo.app.core.model.RailType
import com.srtgo.app.core.model.Reservation
import com.srtgo.app.core.model.SeatType
import com.srtgo.app.core.model.Train
import com.srtgo.app.data.local.entity.MacroHistoryEntity
import com.srtgo.app.data.repository.MacroRepository
import com.srtgo.app.data.repository.ReservationRepository
import com.srtgo.app.data.repository.SettingsRepository
import com.srtgo.app.data.repository.TrainRepository
import com.srtgo.app.util.TelegramNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.math.ln
import kotlin.random.Random

enum class MacroStatus {
    SEARCHING,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class MacroState(
    val status: MacroStatus = MacroStatus.SEARCHING,
    val attempts: Int = 0,
    val elapsedMs: Long = 0,
    val lastError: String? = null,
    val reservation: Reservation? = null,
    val historyId: Int = 0
)

data class MacroConfig(
    val railType: RailType,
    val departure: String,
    val arrival: String,
    val date: String,
    val time: String,
    val passengers: List<Passenger>,
    val seatType: SeatType,
    val selectedTrainIndices: List<Int>,
    val autoPay: Boolean
)

class RunMacroUseCase @Inject constructor(
    private val trainRepository: TrainRepository,
    private val reservationRepository: ReservationRepository,
    private val settingsRepository: SettingsRepository,
    private val macroRepository: MacroRepository,
    private val telegramNotifier: TelegramNotifier
) {

    operator fun invoke(config: MacroConfig): Flow<MacroState> = flow {
        val startTime = System.currentTimeMillis()
        var attempts = 0
        var historyId = 0

        try {
            // Create macro history record
            historyId = macroRepository.create(
                MacroHistoryEntity(
                    railType = config.railType.name,
                    departure = config.departure,
                    arrival = config.arrival,
                    date = config.date,
                    time = config.time,
                    passengers = Json.encodeToString(config.passengers.map {
                        mapOf("type" to it.type.code, "count" to it.count.toString())
                    }),
                    seatType = config.seatType.name,
                    autoPay = config.autoPay,
                    selectedTrains = config.selectedTrainIndices.joinToString(","),
                    status = "running"
                )
            ).toInt()

            emit(MacroState(MacroStatus.SEARCHING, 0, 0, historyId = historyId))

            // Login with saved credentials
            val userId = settingsRepository.get(config.railType.name, "userId")
            val password = settingsRepository.get(config.railType.name, "password")
            if (userId.isNullOrEmpty() || password.isNullOrEmpty()) {
                throw IllegalStateException("${config.railType.name} 로그인 정보가 설정되지 않았습니다")
            }
            trainRepository.login(config.railType, userId, password)

            // Main macro loop
            while (true) {
                currentCoroutineContext().ensureActive()

                attempts++
                val elapsed = System.currentTimeMillis() - startTime
                emit(MacroState(MacroStatus.SEARCHING, attempts, elapsed, historyId = historyId))

                try {
                    // Search trains
                    val trains = trainRepository.searchTrains(
                        config.railType, config.departure, config.arrival,
                        config.date, config.time, config.passengers, config.seatType
                    )

                    // Check selected trains for availability
                    for (index in config.selectedTrainIndices) {
                        currentCoroutineContext().ensureActive()

                        if (index >= trains.size) continue
                        val train = trains[index]

                        if (train.isSeatAvailable(config.seatType)) {
                            // Reserve
                            val reservation = reservationRepository.reserve(
                                train, config.passengers, config.seatType
                            )

                            // Auto-pay if enabled
                            if (config.autoPay) {
                                tryAutoPay(reservation)
                            }

                            // Send Telegram notification
                            sendTelegramNotification(reservation)

                            val finalElapsed = System.currentTimeMillis() - startTime
                            macroRepository.updateStatus(
                                historyId, "success", attempts,
                                finalElapsed / 1000.0,
                                reservation.displayString
                            )

                            emit(
                                MacroState(
                                    MacroStatus.SUCCESS, attempts, finalElapsed,
                                    reservation = reservation, historyId = historyId
                                )
                            )
                            return@flow
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val errorMsg = e.message ?: "Unknown error"
                    val elapsed2 = System.currentTimeMillis() - startTime
                    emit(MacroState(MacroStatus.SEARCHING, attempts, elapsed2, errorMsg, historyId = historyId))

                    // Re-login on session/auth errors
                    if (isSessionError(e)) {
                        try {
                            trainRepository.login(config.railType, userId, password)
                        } catch (_: Exception) {
                            // Will retry on next iteration
                        }
                    }

                    // Clear NetFunnel on queue errors
                    if (isNetFunnelError(e)) {
                        trainRepository.clearNetFunnel(config.railType)
                    }
                }

                // Random delay using gamma distribution approximation
                val delayMs = gammaDelay()
                delay(delayMs)
            }
        } catch (e: CancellationException) {
            val elapsed = System.currentTimeMillis() - startTime
            if (historyId > 0) {
                macroRepository.updateStatus(historyId, "cancelled", attempts, elapsed / 1000.0)
            }
            emit(MacroState(MacroStatus.CANCELLED, attempts, elapsed, historyId = historyId))
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            if (historyId > 0) {
                macroRepository.updateStatus(
                    historyId, "failed", attempts, elapsed / 1000.0, e.message
                )
            }
            emit(
                MacroState(
                    MacroStatus.FAILED, attempts, elapsed,
                    e.message, historyId = historyId
                )
            )
        }
    }

    private suspend fun tryAutoPay(reservation: Reservation) {
        val cardNumber = settingsRepository.getCardNumber() ?: return
        val cardPassword = settingsRepository.getCardPassword() ?: return
        val birthday = settingsRepository.getCardBirthday() ?: return
        val expireDate = settingsRepository.getCardExpireDate() ?: return

        try {
            reservationRepository.payWithCard(
                reservation, cardNumber, cardPassword, birthday, expireDate
            )
        } catch (_: Exception) {
            // Payment failure is not fatal; reservation still holds
        }
    }

    private suspend fun sendTelegramNotification(reservation: Reservation) {
        val token = settingsRepository.getTelegramToken() ?: return
        val chatId = settingsRepository.getTelegramChatId() ?: return

        telegramNotifier.sendMessage(
            token, chatId,
            "[SRTgo] 예매 성공!\n${reservation.displayString}"
        )
    }

    private fun isSessionError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: return false
        return "login" in msg || "session" in msg || "not logged in" in msg || "로그인" in msg
    }

    private fun isNetFunnelError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: return false
        return "netfunnel" in msg || "queue" in msg || "대기" in msg
    }

    /**
     * Gamma distribution approximation for random delay (250ms ~ 1000ms).
     * Uses Marsaglia and Tsang's method simplified:
     * shape=9, scale=0.08 -> mean ~720ms
     */
    private fun gammaDelay(): Long {
        // Simple approach: sum of exponentials for gamma(shape, scale)
        val shape = 9
        val scale = 80.0 // ms
        var sum = 0.0
        repeat(shape) {
            sum += -scale * ln(1.0 - Random.nextDouble())
        }
        return sum.toLong().coerceIn(250L, 1500L)
    }
}
