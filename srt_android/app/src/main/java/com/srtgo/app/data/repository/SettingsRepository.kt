package com.srtgo.app.data.repository

import com.srtgo.app.core.crypto.SecureStorage
import com.srtgo.app.data.local.dao.SettingsDao
import com.srtgo.app.data.local.entity.SettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao,
    private val secureStorage: SecureStorage
) {

    fun getByCategory(category: String): Flow<List<SettingsEntity>> {
        return settingsDao.getByCategory(category)
    }

    suspend fun get(category: String, key: String): String? = withContext(Dispatchers.IO) {
        val entity = settingsDao.getByCategoryAndKey(category, key) ?: return@withContext null
        if (entity.encrypted) {
            secureStorage.get("settings_${category}_${key}")
        } else {
            entity.value
        }
    }

    suspend fun save(
        category: String,
        key: String,
        value: String,
        encrypt: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val storedValue = if (encrypt) {
            secureStorage.save("settings_${category}_${key}", value)
            "encrypted"
        } else value
        settingsDao.upsert(
            SettingsEntity(
                category = category,
                key = key,
                value = storedValue,
                encrypted = encrypt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(category: String, key: String) = withContext(Dispatchers.IO) {
        settingsDao.delete(category, key)
    }

    suspend fun deleteByCategory(category: String) = withContext(Dispatchers.IO) {
        settingsDao.deleteByCategory(category)
    }

    // Station favorites
    suspend fun getFavoriteStations(railType: String): List<String> = withContext(Dispatchers.IO) {
        val value = get("favorites", "${railType}_stations")
        value?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun saveFavoriteStations(railType: String, stations: List<String>) {
        save("favorites", "${railType}_stations", stations.joinToString(","))
    }

    // Search defaults
    suspend fun getLastDeparture(railType: String): String? = get("defaults", "${railType}_departure")
    suspend fun getLastArrival(railType: String): String? = get("defaults", "${railType}_arrival")

    suspend fun saveSearchDefaults(
        railType: String,
        departure: String,
        arrival: String
    ) = withContext(Dispatchers.IO) {
        save("defaults", "${railType}_departure", departure)
        save("defaults", "${railType}_arrival", arrival)
    }

    // Card info
    suspend fun saveCardInfo(
        cardNumber: String,
        cardPassword: String,
        birthday: String,
        expireDate: String
    ) = withContext(Dispatchers.IO) {
        save("card", "number", cardNumber, encrypt = true)
        save("card", "password", cardPassword, encrypt = true)
        save("card", "birthday", birthday, encrypt = true)
        save("card", "expireDate", expireDate, encrypt = true)
    }

    suspend fun getCardNumber(): String? = get("card", "number")
    suspend fun getCardPassword(): String? = get("card", "password")
    suspend fun getCardBirthday(): String? = get("card", "birthday")
    suspend fun getCardExpireDate(): String? = get("card", "expireDate")

    // Telegram
    suspend fun saveTelegramConfig(token: String, chatId: String) = withContext(Dispatchers.IO) {
        save("telegram", "token", token, encrypt = true)
        save("telegram", "chatId", chatId)
    }

    suspend fun getTelegramToken(): String? = get("telegram", "token")
    suspend fun getTelegramChatId(): String? = get("telegram", "chatId")
}
