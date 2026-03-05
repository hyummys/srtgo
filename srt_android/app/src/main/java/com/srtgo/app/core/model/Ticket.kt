package com.srtgo.app.core.model

data class Ticket(
    val car: String,
    val seat: String,
    val seatTypeCode: String,
    val seatTypeName: String,
    val passengerType: String,
    val price: Int,
    val originalPrice: Int,
    val discount: Int,
    val isWaiting: Boolean,
    // KTX ticket-specific fields
    val seatNoEnd: String? = null,
    val seatNoCount: Int = 1,
    val buyerName: String? = null,
    val saleDate: String? = null,
    val pnrNo: String? = null,
    val saleInfo1: String? = null,
    val saleInfo2: String? = null,
    val saleInfo3: String? = null,
    val saleInfo4: String? = null
) {
    val displayString: String
        get() {
            return if (isWaiting) {
                "예약대기 ($seatTypeName) $passengerType[${price}원(${discount}원 할인)]"
            } else {
                "${car}호차 $seat ($seatTypeName) $passengerType [${price}원(${discount}원 할인)]"
            }
        }

    fun getKtxTicketNo(): String? {
        if (saleInfo1 == null || saleInfo2 == null || saleInfo3 == null || saleInfo4 == null) {
            return null
        }
        return "$saleInfo1-$saleInfo2-$saleInfo3-$saleInfo4"
    }

    companion object {
        val SRT_SEAT_TYPE = mapOf("1" to "일반실", "2" to "특실")

        val SRT_DISCOUNT_TYPE = mapOf(
            "000" to "어른/청소년",
            "101" to "탄력운임기준할인",
            "105" to "자유석 할인",
            "106" to "입석 할인",
            "107" to "역방향석 할인",
            "108" to "출입구석 할인",
            "109" to "가족석 일반전환 할인",
            "111" to "구간별 특정운임",
            "112" to "열차별 특정운임",
            "113" to "구간별 비율할인(기준)",
            "114" to "열차별 비율할인(기준)",
            "121" to "공항직결 수색연결운임",
            "131" to "구간별 특별할인(기준)",
            "132" to "열차별 특별할인(기준)",
            "133" to "기본 특별할인(기준)",
            "191" to "정차역 할인",
            "192" to "매체 할인",
            "201" to "어린이",
            "202" to "동반유아 할인",
            "204" to "경로",
            "205" to "1~3급 장애인",
            "206" to "4~6급 장애인"
        )

        fun fromSrtData(data: Map<String, Any?>): Ticket {
            val seatTypeCode = data["psrmClCd"] as? String ?: "1"
            val discountCode = data["dcntKndCd"] as? String ?: "000"
            val seatStr = data["seatNo"] as? String ?: ""
            return Ticket(
                car = data["scarNo"] as? String ?: "",
                seat = seatStr,
                seatTypeCode = seatTypeCode,
                seatTypeName = SRT_SEAT_TYPE[seatTypeCode] ?: "기타",
                passengerType = SRT_DISCOUNT_TYPE[discountCode] ?: "기타 할인",
                price = (data["rcvdAmt"] as? String)?.toIntOrNull() ?: 0,
                originalPrice = (data["stdrPrc"] as? String)?.toIntOrNull() ?: 0,
                discount = (data["dcntPrc"] as? String)?.toIntOrNull() ?: 0,
                isWaiting = seatStr.isEmpty()
            )
        }

        fun fromKtxSeatData(data: Map<String, Any?>): Ticket {
            val seatStr = data["h_seat_no"] as? String ?: ""
            return Ticket(
                car = data["h_srcar_no"] as? String ?: "",
                seat = seatStr,
                seatTypeCode = "",
                seatTypeName = data["h_psrm_cl_nm"] as? String ?: "",
                passengerType = data["h_psg_tp_dv_nm"] as? String ?: "",
                price = (data["h_rcvd_amt"] as? String)?.toIntOrNull() ?: 0,
                originalPrice = (data["h_seat_prc"] as? String)?.toIntOrNull() ?: 0,
                discount = (data["h_dcnt_amt"] as? String)?.toIntOrNull() ?: 0,
                isWaiting = seatStr.isEmpty()
            )
        }

        fun fromKtxTicketData(data: Map<String, Any?>): Ticket {
            val rawData = ((data["ticket_list"] as? List<*>)
                ?.firstOrNull() as? Map<*, *>)
                ?.let { (it["train_info"] as? List<*>)?.firstOrNull() as? Map<*, *> }

            @Suppress("UNCHECKED_CAST")
            val rd = rawData as? Map<String, Any?> ?: emptyMap()
            val seatStr = rd["h_seat_no"] as? String ?: ""

            return Ticket(
                car = rd["h_srcar_no"] as? String ?: "",
                seat = seatStr,
                seatTypeCode = "",
                seatTypeName = rd["h_psrm_cl_nm"] as? String ?: "",
                passengerType = rd["h_psg_tp_dv_nm"] as? String ?: "",
                price = (rd["h_rcvd_amt"] as? String)?.toIntOrNull() ?: 0,
                originalPrice = (rd["h_seat_prc"] as? String)?.toIntOrNull() ?: 0,
                discount = (rd["h_dcnt_amt"] as? String)?.toIntOrNull() ?: 0,
                isWaiting = seatStr.isEmpty(),
                seatNoEnd = rd["h_seat_no_end"] as? String,
                seatNoCount = (rd["h_seat_cnt"] as? String)?.toIntOrNull() ?: 1,
                buyerName = rd["h_buy_ps_nm"] as? String,
                saleDate = rd["h_orgtk_sale_dt"] as? String,
                pnrNo = rd["h_pnr_no"] as? String,
                saleInfo1 = rd["h_orgtk_wct_no"] as? String,
                saleInfo2 = rd["h_orgtk_ret_sale_dt"] as? String,
                saleInfo3 = rd["h_orgtk_sale_sqno"] as? String,
                saleInfo4 = rd["h_orgtk_ret_pwd"] as? String
            )
        }
    }
}
