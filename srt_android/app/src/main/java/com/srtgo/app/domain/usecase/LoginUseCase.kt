package com.srtgo.app.domain.usecase

import com.srtgo.app.core.model.RailType
import com.srtgo.app.data.repository.TrainRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val trainRepository: TrainRepository
) {

    suspend operator fun invoke(
        railType: RailType,
        id: String,
        password: String
    ): Result<Unit> {
        return try {
            trainRepository.login(railType, id, password)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
