package com.srtgo.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramNotifier @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun sendMessage(token: String, chatId: String, text: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$token/sendMessage"
                val body = FormBody.Builder()
                    .add("chat_id", chatId)
                    .add("text", text)
                    .add("parse_mode", "HTML")
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
