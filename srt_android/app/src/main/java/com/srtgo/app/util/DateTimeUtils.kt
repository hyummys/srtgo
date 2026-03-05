package com.srtgo.app.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object DateTimeUtils {

    private val KOREAN_DAY_NAMES = mapOf(
        DayOfWeek.MONDAY to "월",
        DayOfWeek.TUESDAY to "화",
        DayOfWeek.WEDNESDAY to "수",
        DayOfWeek.THURSDAY to "목",
        DayOfWeek.FRIDAY to "금",
        DayOfWeek.SATURDAY to "토",
        DayOfWeek.SUNDAY to "일"
    )

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * "120000" -> "12:00"
     */
    fun formatDisplayTime(timeStr: String): String {
        if (timeStr.length < 4) return timeStr
        return "${timeStr.substring(0, 2)}:${timeStr.substring(2, 4)}"
    }

    /**
     * "20260305" -> "2026.03.05 (목)"
     */
    fun formatDisplayDate(dateStr: String): String {
        if (dateStr.length < 8) return dateStr
        val date = LocalDate.parse(dateStr, DATE_FORMAT)
        val dayName = KOREAN_DAY_NAMES[date.dayOfWeek] ?: ""
        return "${dateStr.substring(0, 4)}.${dateStr.substring(4, 6)}.${dateStr.substring(6, 8)} ($dayName)"
    }

    /**
     * "20260305" -> "03.05 (목)"
     */
    fun formatShortDate(dateStr: String): String {
        if (dateStr.length < 8) return dateStr
        val date = LocalDate.parse(dateStr, DATE_FORMAT)
        val dayName = KOREAN_DAY_NAMES[date.dayOfWeek] ?: ""
        return "${dateStr.substring(4, 6)}.${dateStr.substring(6, 8)} ($dayName)"
    }

    /**
     * Returns current date as "YYYYMMDD"
     */
    fun getCurrentDate(): String {
        return LocalDate.now().format(DATE_FORMAT)
    }

    /**
     * Returns current time as "HHMMSS"
     */
    fun getCurrentTime(): String {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
    }

    /**
     * Checks if dateStr is today
     */
    fun isToday(dateStr: String): Boolean {
        return dateStr == getCurrentDate()
    }

    /**
     * Calculates travel duration in minutes from departure/arrival time strings ("HHMMSS" or "HHMM")
     */
    fun durationMinutes(depTime: String, arrTime: String): Int {
        val depMinutes = depTime.substring(0, 2).toInt() * 60 + depTime.substring(2, 4).toInt()
        val arrMinutes = arrTime.substring(0, 2).toInt() * 60 + arrTime.substring(2, 4).toInt()
        val duration = arrMinutes - depMinutes
        return if (duration < 0) duration + 24 * 60 else duration
    }

    /**
     * Returns Korean day-of-week name for a date string
     */
    fun getKoreanDayOfWeek(dateStr: String): String {
        if (dateStr.length < 8) return ""
        val date = LocalDate.parse(dateStr, DATE_FORMAT)
        return KOREAN_DAY_NAMES[date.dayOfWeek] ?: ""
    }

    /**
     * Formats elapsed milliseconds as "MM:SS" or "HH:MM:SS"
     */
    fun formatElapsed(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
