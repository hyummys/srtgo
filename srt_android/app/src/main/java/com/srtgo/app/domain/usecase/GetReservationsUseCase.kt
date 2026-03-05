package com.srtgo.app.domain.usecase

import com.srtgo.app.core.model.RailType
import com.srtgo.app.core.model.Reservation
import com.srtgo.app.data.repository.ReservationRepository
import javax.inject.Inject

class GetReservationsUseCase @Inject constructor(
    private val reservationRepository: ReservationRepository
) {

    suspend operator fun invoke(railType: RailType): Result<List<Reservation>> {
        return try {
            val reservations = reservationRepository.getReservations(railType)
            Result.success(reservations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
