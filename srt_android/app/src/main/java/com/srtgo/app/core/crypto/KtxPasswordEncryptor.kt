package com.srtgo.app.core.crypto

import android.util.Base64
import com.srtgo.app.core.exception.KtxLoginException
import com.srtgo.app.core.network.SessionManager
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KtxPasswordEncryptor @Inject constructor(
    private val sessionManager: SessionManager
) {

    companion object {
        private const val CODE_URL =
            "https://smart.letskorail.com:443/classes/com.korail.mobile.common.code.do"
    }

    suspend fun encrypt(password: String): EncryptResult {
        val json = sessionManager.ktxPostForm(CODE_URL, mapOf("code" to "app.login.cphd"))

        if (json.optString("strResult") != "SUCC" || !json.has("app.login.cphd")) {
            throw KtxLoginException("Failed to fetch encryption key")
        }

        val keyData = json.getJSONObject("app.login.cphd")
        val idx = keyData.getInt("idx")
        val hexKey = keyData.getString("key")

        val encryptKey = hexKey.toByteArray(Charsets.UTF_8)
        val iv = hexKey.substring(0, 16).toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(encryptKey, "AES"),
            IvParameterSpec(iv)
        )
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))

        val firstBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val doubleBase64 = Base64.encodeToString(firstBase64.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        return EncryptResult(
            encryptedPassword = doubleBase64,
            idx = idx
        )
    }
}

data class EncryptResult(
    val encryptedPassword: String,
    val idx: Int
)
