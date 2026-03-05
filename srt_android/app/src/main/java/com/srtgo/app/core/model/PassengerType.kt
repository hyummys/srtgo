package com.srtgo.app.core.model

enum class PassengerType(val code: String, val displayName: String) {
    ADULT("1", "어른/청소년"),
    DISABILITY_1_3("2", "장애 1~3급"),
    DISABILITY_4_6("3", "장애 4~6급"),
    SENIOR("4", "경로"),
    CHILD("5", "어린이");

    companion object {
        fun fromCode(code: String): PassengerType =
            entries.first { it.code == code }
    }
}
