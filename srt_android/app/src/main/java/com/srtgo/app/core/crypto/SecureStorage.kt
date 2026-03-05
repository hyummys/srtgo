package com.srtgo.app.core.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.srtgo.app.core.model.RailType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "srtgo_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun get(key: String): String? {
        return prefs.getString(key, null)
    }

    fun delete(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun has(key: String): Boolean {
        return prefs.contains(key)
    }

    // Credential helpers
    fun saveCredential(railType: RailType, id: String, password: String) {
        val prefix = railType.name.lowercase()
        save("${prefix}_id", id)
        save("${prefix}_pw", password)
    }

    fun getCredential(railType: RailType): Pair<String, String>? {
        val prefix = railType.name.lowercase()
        val id = get("${prefix}_id") ?: return null
        val pw = get("${prefix}_pw") ?: return null
        return Pair(id, pw)
    }

    fun deleteCredential(railType: RailType) {
        val prefix = railType.name.lowercase()
        delete("${prefix}_id")
        delete("${prefix}_pw")
    }

    // Card info helpers
    fun saveCardInfo(
        cardNumber: String,
        cardPassword: String,
        birthday: String,
        expireDate: String
    ) {
        save("card_number", cardNumber)
        save("card_password", cardPassword)
        save("card_birthday", birthday)
        save("card_expire", expireDate)
    }

    fun getCardInfo(): CardInfo? {
        val number = get("card_number") ?: return null
        val password = get("card_password") ?: return null
        val birthday = get("card_birthday") ?: return null
        val expire = get("card_expire") ?: return null
        return CardInfo(number, password, birthday, expire)
    }

    fun deleteCardInfo() {
        delete("card_number")
        delete("card_password")
        delete("card_birthday")
        delete("card_expire")
    }

    // Telegram config helpers
    fun saveTelegramConfig(botToken: String, chatId: String) {
        save("tg_bot_token", botToken)
        save("tg_chat_id", chatId)
    }

    fun getTelegramConfig(): Pair<String, String>? {
        val token = get("tg_bot_token") ?: return null
        val chatId = get("tg_chat_id") ?: return null
        return Pair(token, chatId)
    }

    fun deleteTelegramConfig() {
        delete("tg_bot_token")
        delete("tg_chat_id")
    }
}

data class CardInfo(
    val cardNumber: String,
    val cardPassword: String,
    val birthday: String,
    val expireDate: String
)
