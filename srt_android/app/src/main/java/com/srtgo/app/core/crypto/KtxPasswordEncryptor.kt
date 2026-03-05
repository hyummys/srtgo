package com.srtgo.app.core.crypto

import com.srtgo.app.core.exception.KtxLoginException
import com.srtgo.app.core.network.SessionManager
import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KtxPasswordEncryptor @Inject constructor(
    private val sessionManager: SessionManager
) {

    companion object {
        private const val PBLK_URL =
            "https://www.korail.com/ebizweb/tour/mypage/pwd_action/pblk"
    }

    suspend fun encrypt(password: String): EncryptResult {
        val json = sessionManager.ktxGet(PBLK_URL)

        val modulus = json.optString("publicKeyModulus", "")
        val exponent = json.optString("publicKeyExponent", "")
        val keyname = json.optString("keyname", "")

        if (modulus.isEmpty() || exponent.isEmpty() || keyname.isEmpty()) {
            throw KtxLoginException("Failed to fetch RSA public key")
        }

        val rsaSpec = RSAPublicKeySpec(
            BigInteger(modulus, 16),
            BigInteger(exponent, 16)
        )
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(rsaSpec)

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))

        val hexString = encrypted.joinToString("") { "%02x".format(it) }

        return EncryptResult(
            encryptedPassword = hexString,
            idx = keyname
        )
    }
}

data class EncryptResult(
    val encryptedPassword: String,
    val idx: String
)
