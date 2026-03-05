package com.srtgo.app.core.model

data class Reservation(
    val railType: RailType,
    val reservationNumber: String,
    val totalCost: Int,
    val seatCount: Int,
    val trainCode: String = "",
    val trainName: String,
    val trainNumber: String,
    val depDate: String,
    val depTime: String,
    val depStationCode: String = "",
    val depStationName: String,
    val arrTime: String,
    val arrStationCode: String = "",
    val arrStationName: String,
    val paymentDate: String? = null,
    val paymentTime: String? = null,
    val isPaid: Boolean = false,
    val isRunning: Boolean = false,
    val isWaiting: Boolean = false,
    val tickets: List<Ticket> = emptyList(),
    // KTX-specific fields
    val journeyNo: String = "001",
    val journeyCnt: String = "01",
    val rsvChgNo: String = "00000",
    val wctNo: String = "",
    val buyLimitDate: String? = null,
    val buyLimitTime: String? = null
) {
    val formattedDepTime: String
        get() = "${depTime.substring(0, 2)}:${depTime.substring(2, 4)}"

    val formattedArrTime: String
        get() = "${arrTime.substring(0, 2)}:${arrTime.substring(2, 4)}"

    val formattedDate: String
        get() = "${depDate.substring(4, 6)}월 ${depDate.substring(6, 8)}일"

    val displayString: String
        get() {
            var base = "[$trainName] $formattedDate, " +
                    "$depStationName~$arrStationName" +
                    "($formattedDepTime~$formattedArrTime) " +
                    "${totalCost}원(${seatCount}석)"

            if (!isPaid) {
                if (!isWaiting && paymentDate != null && paymentTime != null) {
                    val payMonth = paymentDate.substring(4, 6)
                    val payDay = paymentDate.substring(6, 8)
                    val payHour = paymentTime.substring(0, 2)
                    val payMin = paymentTime.substring(2, 4)
                    base += ", 구입기한 ${payMonth}월 ${payDay}일 ${payHour}:${payMin}"
                } else if (isWaiting && !isRunning) {
                    base += ", 예약대기"
                }
            }

            if (isRunning) {
                base += " (운행중)"
            }

            return base
        }

    companion object {
        fun fromSrtData(
            trainData: Map<String, Any?>,
            payData: Map<String, Any?>,
            tickets: List<Ticket>
        ): Reservation {
            val trainCode = payData["stlbTrnClsfCd"] as? String ?: ""
            val depStationCode = payData["dptRsStnCd"] as? String ?: ""
            val arrStationCode = payData["arvRsStnCd"] as? String ?: ""
            val payDate = payData["iseLmtDt"] as? String
            val payTime = payData["iseLmtTm"] as? String
            val paid = payData["stlFlg"] as? String == "Y"
            val running = "tkSpecNum" !in trainData
            val waiting = !paid && payDate.isNullOrEmpty() && payTime.isNullOrEmpty()

            return Reservation(
                railType = RailType.SRT,
                reservationNumber = trainData["pnrNo"] as? String ?: "",
                totalCost = (trainData["rcvdAmt"] as? String)?.toIntOrNull() ?: 0,
                seatCount = (trainData["tkSpecNum"] as? String)?.toIntOrNull()
                    ?: (trainData["seatNum"] as? String)?.toIntOrNull() ?: 0,
                trainCode = trainCode,
                trainName = Train.SRT_TRAIN_NAMES[trainCode] ?: trainCode,
                trainNumber = payData["trnNo"] as? String ?: "",
                depDate = payData["dptDt"] as? String ?: "",
                depTime = payData["dptTm"] as? String ?: "",
                depStationCode = depStationCode,
                depStationName = Station.getName(RailType.SRT, depStationCode),
                arrTime = payData["arvTm"] as? String ?: "",
                arrStationCode = arrStationCode,
                arrStationName = Station.getName(RailType.SRT, arrStationCode),
                paymentDate = payDate,
                paymentTime = payTime,
                isPaid = paid,
                isRunning = running,
                isWaiting = waiting,
                tickets = tickets
            )
        }

        fun fromKtxData(
            data: Map<String, Any?>,
            tickets: List<Ticket> = emptyList(),
            wctNo: String = ""
        ): Reservation {
            val buyLimitDate = data["h_ntisu_lmt_dt"] as? String ?: "00000000"
            val buyLimitTime = data["h_ntisu_lmt_tm"] as? String ?: "235959"
            val isWaiting = buyLimitDate == "00000000" || buyLimitTime == "235959"

            return Reservation(
                railType = RailType.KTX,
                reservationNumber = data["h_pnr_no"] as? String ?: "",
                totalCost = (data["h_rsv_amt"] as? String)?.toIntOrNull() ?: 0,
                seatCount = (data["h_tot_seat_cnt"] as? String)?.toIntOrNull() ?: 0,
                trainCode = data["h_trn_clsf_cd"] as? String ?: "",
                trainName = data["h_trn_clsf_nm"] as? String ?: "",
                trainNumber = data["h_trn_no"] as? String ?: "",
                depDate = data["h_run_dt"] as? String ?: "",
                depTime = data["h_dpt_tm"] as? String ?: "",
                depStationCode = data["h_dpt_rs_stn_cd"] as? String ?: "",
                depStationName = data["h_dpt_rs_stn_nm"] as? String ?: "",
                arrTime = data["h_arv_tm"] as? String ?: "",
                arrStationCode = data["h_arv_rs_stn_cd"] as? String ?: "",
                arrStationName = data["h_arv_rs_stn_nm"] as? String ?: "",
                isWaiting = isWaiting,
                buyLimitDate = buyLimitDate,
                buyLimitTime = buyLimitTime,
                journeyNo = data["txtJrnySqno"] as? String ?: "001",
                journeyCnt = data["txtJrnyCnt"] as? String ?: "01",
                rsvChgNo = data["hidRsvChgNo"] as? String ?: "00000",
                tickets = tickets,
                wctNo = wctNo
            )
        }
    }
}
