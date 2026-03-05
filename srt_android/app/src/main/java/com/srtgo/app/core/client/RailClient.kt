package com.srtgo.app.core.client

import com.srtgo.app.core.model.Passenger
import com.srtgo.app.core.model.Reservation
import com.srtgo.app.core.model.SeatType
import com.srtgo.app.core.model.Ticket
import com.srtgo.app.core.model.Train

interface RailClient {
    suspend fun login(id: String, password: String)
    suspend fun logout()
    suspend fun searchTrains(
        departure: String,
        arrival: String,
        date: String,
        time: String,
        passengers: List<Passenger> = listOf(Passenger(com.srtgo.app.core.model.PassengerType.ADULT)),
        seatType: SeatType = SeatType.GENERAL_FIRST
    ): List<Train>
    suspend fun reserve(
        train: Train,
        passengers: List<Passenger> = listOf(Passenger(com.srtgo.app.core.model.PassengerType.ADULT)),
        seatType: SeatType = SeatType.GENERAL_FIRST,
        windowSeat: Boolean? = null
    ): Reservation
    suspend fun getReservations(): List<Reservation>
    suspend fun getTicketInfo(reservation: Reservation): List<Ticket>
    suspend fun cancel(reservation: Reservation)
    suspend fun payWithCard(
        reservation: Reservation,
        cardNumber: String,
        cardPassword: String,
        birthday: String,
        expireDate: String,
        installment: Int = 0
    )
    suspend fun refund(reservation: Reservation)
    fun clearNetFunnel()
}
