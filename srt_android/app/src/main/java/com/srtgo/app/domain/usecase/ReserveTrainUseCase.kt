package com.srtgo.app.domain.usecase

import com.srtgo.app.core.model.Passenger
import com.srtgo.app.core.model.Reservation
import com.srtgo.app.core.model.SeatType
import com.srtgo.app.core.model.Train
import com.srtgo.app.data.repository.ReservationRepository
import javax.inject.Inject

class ReserveTrainUseCase @Inject constructor(
    private val reservationRepository: ReservationRepository
) {

    suspend operator fun invoke(
        train: Train,
        passengers: List<Passenger>,
        seatType: SeatType,
        windowSeat: Boolean? = null
    ): Result<Reservation> {
        return try {
            val reservation = reservationRepository.reserve(train, passengers, seatType, windowSeat)
            Result.success(reservation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
