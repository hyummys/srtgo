package com.srtgo.app.data.repository

import com.srtgo.app.data.local.dao.MacroHistoryDao
import com.srtgo.app.data.local.entity.MacroHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MacroRepository @Inject constructor(
    private val macroHistoryDao: MacroHistoryDao
) {

    fun getHistory(): Flow<List<MacroHistoryEntity>> {
        return macroHistoryDao.getAll()
    }

    suspend fun getById(id: Int): MacroHistoryEntity? = withContext(Dispatchers.IO) {
        macroHistoryDao.getById(id)
    }

    suspend fun getRunning(): List<MacroHistoryEntity> = withContext(Dispatchers.IO) {
        macroHistoryDao.getByStatus("running")
    }

    suspend fun create(entity: MacroHistoryEntity): Long = withContext(Dispatchers.IO) {
        macroHistoryDao.insert(entity)
    }

    suspend fun updateStatus(
        id: Int,
        status: String,
        attempts: Int,
        elapsedSeconds: Double,
        resultJson: String? = null,
        finishedAt: Long? = if (status != "running") System.currentTimeMillis() else null
    ) = withContext(Dispatchers.IO) {
        macroHistoryDao.updateStatus(id, status, attempts, elapsedSeconds, resultJson, finishedAt)
    }

    suspend fun deleteOlderThan(daysAgo: Int) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - daysAgo.toLong() * 24 * 60 * 60 * 1000
        macroHistoryDao.deleteOlderThan(cutoff)
    }
}
