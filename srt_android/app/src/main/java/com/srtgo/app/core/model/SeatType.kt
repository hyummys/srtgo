package com.srtgo.app.core.model

enum class SeatType(val code: Int, val displayName: String) {
    GENERAL_FIRST(1, "일반실 우선"),
    GENERAL_ONLY(2, "일반실"),
    SPECIAL_FIRST(3, "특실 우선"),
    SPECIAL_ONLY(4, "특실");

    companion object {
        fun fromCode(code: Int): SeatType =
            entries.first { it.code == code }
    }
}
