package com.srtgo.app.data.repository

import com.srtgo.app.core.client.KtxClient
import com.srtgo.app.core.client.SrtClient
import com.srtgo.app.core.model.Passenger
import com.srtgo.app.core.model.RailType
import com.srtgo.app.core.model.Reservation
import com.srtgo.app.core.model.SeatType
import com.srtgo.app.core.model.Train
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReservationRepository @Inject constructor(
    private val srtClient: SrtClient,
    private val ktxClient: KtxClient
) {

    suspend fun getReservations(railType: RailType): List<Reservation> = withContext(Dispatchers.IO) {
        when (railType) {
            RailType.SRT -> srtClient.getReservations()
            RailType.KTX -> ktxClient.getReservations()
        }
    }

    suspend fun reserve(
        train: Train,
        passengers: List<Passenger>,
        seatType: SeatType,
        windowSeat: Boolean? = null
    ): Reservation = withContext(Dispatchers.IO) {
        when (train.railType) {
            RailType.SRT -> srtClient.reserve(train, passengers, seatType, windowSeat)
            RailType.KTX -> ktxClient.reserve(train, passengers, seatType, windowSeat)
        }
    }

    suspend fun cancel(reservation: Reservation) = withContext(Dispatchers.IO) {
        when (reservation.railType) {
            RailType.SRT -> srtClient.cancel(reservation)
            RailType.KTX -> ktxClient.cancel(reservation)
        }
    }

    suspend fun payWithCard(
        reservation: Reservation,
        cardNumber: String,
        cardPassword: String,
        birthday: String,
        expireDate: String,
        installment: Int = 0
    ) = withContext(Dispatchers.IO) {
        when (reservation.railType) {
            RailType.SRT -> srtClient.payWithCard(
                reservation, cardNumber, cardPassword, birthday, expireDate, installment
            )
            RailType.KTX -> ktxClient.payWithCard(
                reservation, cardNumber, cardPassword, birthday, expireDate, installment
            )
        }
    }

    suspend fun refund(reservation: Reservation) = withContext(Dispatchers.IO) {
        when (reservation.railType) {
            RailType.SRT -> srtClient.refund(reservation)
            RailType.KTX -> ktxClient.refund(reservation)
        }
    }
}
