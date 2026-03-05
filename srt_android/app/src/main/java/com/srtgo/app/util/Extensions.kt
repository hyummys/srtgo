package com.srtgo.app.util

import android.content.Context
import android.widget.Toast
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

fun Int.toCurrencyString(): String {
    val formatted = NumberFormat.getNumberInstance(Locale.KOREA).format(this)
    return "${formatted}원"
}

fun String.maskCardNumber(): String {
    if (this.length < 4) return this
    val last4 = this.takeLast(4)
    return "****-****-****-$last4"
}

fun String.maskPassword(): String {
    return "•".repeat(this.length.coerceAtLeast(6))
}

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key, "")
    return value.ifEmpty { null }
}
