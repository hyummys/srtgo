package com.srtgo.app.domain.usecase

import com.srtgo.app.core.model.Passenger
import com.srtgo.app.core.model.RailType
import com.srtgo.app.core.model.SeatType
import com.srtgo.app.core.model.Train
import com.srtgo.app.data.repository.SettingsRepository
import com.srtgo.app.data.repository.TrainRepository
import javax.inject.Inject

class SearchTrainsUseCase @Inject constructor(
    private val trainRepository: TrainRepository,
    private val settingsRepository: SettingsRepository
) {

    suspend operator fun invoke(
        railType: RailType,
        departure: String,
        arrival: String,
        date: String,
        time: String,
        passengers: List<Passenger>,
        seatType: SeatType = SeatType.GENERAL_FIRST
    ): Result<List<Train>> {
        return try {
            // Save search defaults for next time
            settingsRepository.saveSearchDefaults(railType.name, departure, arrival)

            val trains = trainRepository.searchTrains(
                railType, departure, arrival, date, time, passengers, seatType
            )
            Result.success(trains)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
