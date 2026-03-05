package com.srtgo.app.core.model

data class Train(
    val railType: RailType,
    val trainCode: String,
    val trainName: String,
    val trainNumber: String,
    val depDate: String,
    val depTime: String,
    val depStationCode: String,
    val depStationName: String,
    val depStationRunOrder: String = "",
    val depStationConsOrder: String = "",
    val arrDate: String,
    val arrTime: String,
    val arrStationCode: String,
    val arrStationName: String,
    val arrStationRunOrder: String = "",
    val arrStationConsOrder: String = "",
    val generalSeatState: String,
    val specialSeatState: String,
    val reserveWaitCode: Int = -1,
    val reserveWaitName: String = "",
    // KTX-specific fields
    val trainGroup: String = "",
    val runDate: String = "",
    val delayTime: String = ""
) {
    val isGeneralAvailable: Boolean
        get() = when (railType) {
            RailType.SRT -> "예약가능" in generalSeatState
            RailType.KTX -> generalSeatState == "11"
        }

    val isSpecialAvailable: Boolean
        get() = when (railType) {
            RailType.SRT -> "예약가능" in specialSeatState
            RailType.KTX -> specialSeatState == "11"
        }

    val isStandbyAvailable: Boolean
        get() = reserveWaitCode == 9

    fun isSeatAvailable(seatType: SeatType): Boolean = when (seatType) {
        SeatType.GENERAL_FIRST -> isGeneralAvailable || isSpecialAvailable
        SeatType.GENERAL_ONLY -> isGeneralAvailable
        SeatType.SPECIAL_FIRST -> isSpecialAvailable || isGeneralAvailable
        SeatType.SPECIAL_ONLY -> isSpecialAvailable
    }

    fun hasSeat(): Boolean = isGeneralAvailable || isSpecialAvailable

    val formattedDepTime: String
        get() = "${depTime.substring(0, 2)}:${depTime.substring(2, 4)}"

    val formattedArrTime: String
        get() = "${arrTime.substring(0, 2)}:${arrTime.substring(2, 4)}"

    val formattedDate: String
        get() = "${depDate.substring(4, 6)}/${depDate.substring(6, 8)}"

    val durationMinutes: Int
        get() {
            val depMinutes = depTime.substring(0, 2).toInt() * 60 + depTime.substring(2, 4).toInt()
            val arrMinutes = arrTime.substring(0, 2).toInt() * 60 + arrTime.substring(2, 4).toInt()
            val duration = arrMinutes - depMinutes
            return if (duration < 0) duration + 24 * 60 else duration
        }

    val displayString: String
        get() {
            val trainLine = "[$trainName $trainNumber]"
            val seatInfo = when (railType) {
                RailType.SRT -> "특실 $specialSeatState, 일반실 $generalSeatState"
                RailType.KTX -> "특실 ${if (isSpecialAvailable) "가능" else "매진"}, " +
                        "일반실 ${if (isGeneralAvailable) "가능" else "매진"}"
            }
            var msg = "$trainLine $formattedDate $formattedDepTime~$formattedArrTime " +
                    "$depStationName~$arrStationName $seatInfo"
            if (reserveWaitCode >= 0) {
                msg += ", 예약대기 ${if (isStandbyAvailable) "가능" else "매진"}"
            }
            msg += " (${durationMinutes}분)"
            return msg
        }

    companion object {
        val SRT_TRAIN_NAMES = mapOf(
            "00" to "KTX",
            "02" to "무궁화",
            "03" to "통근열차",
            "04" to "누리로",
            "05" to "전체",
            "07" to "KTX-산천",
            "08" to "ITX-새마을",
            "09" to "ITX-청춘",
            "10" to "KTX-산천",
            "17" to "SRT",
            "18" to "ITX-마음"
        )

        fun fromSrtData(data: Map<String, Any?>): Train {
            val trainCode = data["stlbTrnClsfCd"] as String
            val depStationCode = data["dptRsStnCd"] as String
            val arrStationCode = data["arvRsStnCd"] as String
            return Train(
                railType = RailType.SRT,
                trainCode = trainCode,
                trainName = SRT_TRAIN_NAMES[trainCode] ?: trainCode,
                trainNumber = data["trnNo"] as String,
                depDate = data["dptDt"] as String,
                depTime = data["dptTm"] as String,
                depStationCode = depStationCode,
                depStationName = Station.getName(RailType.SRT, depStationCode),
                depStationRunOrder = data["dptStnRunOrdr"] as? String ?: "",
                depStationConsOrder = data["dptStnConsOrdr"] as? String ?: "",
                arrDate = data["arvDt"] as String,
                arrTime = data["arvTm"] as String,
                arrStationCode = arrStationCode,
                arrStationName = Station.getName(RailType.SRT, arrStationCode),
                arrStationRunOrder = data["arvStnRunOrdr"] as? String ?: "",
                arrStationConsOrder = data["arvStnConsOrdr"] as? String ?: "",
                generalSeatState = data["gnrmRsvPsbStr"] as? String ?: "",
                specialSeatState = data["sprmRsvPsbStr"] as? String ?: "",
                reserveWaitCode = (data["rsvWaitPsbCd"] as? String)?.toIntOrNull() ?: -1,
                reserveWaitName = data["rsvWaitPsbCdNm"] as? String ?: ""
            )
        }

        fun fromKtxData(data: Map<String, Any?>): Train {
            val trainType = data["h_trn_clsf_cd"] as? String ?: ""
            val trainTypeName = data["h_trn_clsf_nm"] as? String ?: ""
            return Train(
                railType = RailType.KTX,
                trainCode = trainType,
                trainName = trainTypeName,
                trainNumber = data["h_trn_no"] as? String ?: "",
                depDate = data["h_dpt_dt"] as? String ?: "",
                depTime = data["h_dpt_tm"] as? String ?: "",
                depStationCode = data["h_dpt_rs_stn_cd"] as? String ?: "",
                depStationName = data["h_dpt_rs_stn_nm"] as? String ?: "",
                arrDate = data["h_arv_dt"] as? String ?: "",
                arrTime = data["h_arv_tm"] as? String ?: "",
                arrStationCode = data["h_arv_rs_stn_cd"] as? String ?: "",
                arrStationName = data["h_arv_rs_stn_nm"] as? String ?: "",
                generalSeatState = data["h_gen_rsv_cd"] as? String ?: "",
                specialSeatState = data["h_spe_rsv_cd"] as? String ?: "",
                reserveWaitCode = (data["h_wait_rsv_flg"] as? String)?.toIntOrNull() ?: -1,
                reserveWaitName = "",
                trainGroup = data["h_trn_gp_cd"] as? String ?: "",
                runDate = data["h_run_dt"] as? String ?: "",
                delayTime = data["h_expct_dlay_hr"] as? String ?: ""
            )
        }
    }
}
