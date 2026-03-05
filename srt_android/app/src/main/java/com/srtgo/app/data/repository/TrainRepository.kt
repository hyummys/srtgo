package com.srtgo.app.data.repository

import com.srtgo.app.core.client.KtxClient
import com.srtgo.app.core.client.SrtClient
import com.srtgo.app.core.model.Passenger
import com.srtgo.app.core.model.RailType
import com.srtgo.app.core.model.SeatType
import com.srtgo.app.core.model.Train
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrainRepository @Inject constructor(
    private val srtClient: SrtClient,
    private val ktxClient: KtxClient
) {

    suspend fun searchTrains(
        railType: RailType,
        departure: String,
        arrival: String,
        date: String,
        time: String,
        passengers: List<Passenger>,
        seatType: SeatType = SeatType.GENERAL_FIRST
    ): List<Train> = withContext(Dispatchers.IO) {
        when (railType) {
            RailType.SRT -> srtClient.searchTrains(departure, arrival, date, time, passengers, seatType)
            RailType.KTX -> ktxClient.searchTrains(departure, arrival, date, time, passengers, seatType)
        }
    }

    suspend fun login(railType: RailType, id: String, password: String) = withContext(Dispatchers.IO) {
        when (railType) {
            RailType.SRT -> srtClient.login(id, password)
            RailType.KTX -> ktxClient.login(id, password)
        }
    }

    suspend fun logout(railType: RailType) = withContext(Dispatchers.IO) {
        when (railType) {
            RailType.SRT -> srtClient.logout()
            RailType.KTX -> ktxClient.logout()
        }
    }

    fun clearNetFunnel(railType: RailType) {
        when (railType) {
            RailType.SRT -> srtClient.clearNetFunnel()
            RailType.KTX -> ktxClient.clearNetFunnel()
        }
    }

    fun getClient(railType: RailType) = when (railType) {
        RailType.SRT -> srtClient
        RailType.KTX -> ktxClient
    }
}
