package com.srtgo.app.core.client

import com.srtgo.app.core.crypto.KtxPasswordEncryptor
import com.srtgo.app.core.exception.*
import com.srtgo.app.core.model.*
import com.srtgo.app.core.network.NetFunnelHelper
import com.srtgo.app.core.network.SessionManager
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KtxClient @Inject constructor(
    private val sessionManager: SessionManager,
    private val netFunnelHelper: NetFunnelHelper,
    private val passwordEncryptor: KtxPasswordEncryptor
) : RailClient {

    companion object {
        private const val KORAIL_MOBILE =
            "https://smart.letskorail.com:443/classes/com.korail.mobile"

        private val ENDPOINTS = mapOf(
            "login" to "$KORAIL_MOBILE.login.Login",
            "logout" to "$KORAIL_MOBILE.common.logout",
            "search_schedule" to "$KORAIL_MOBILE.seatMovie.ScheduleView",
            "reserve" to "$KORAIL_MOBILE.certification.TicketReservation",
            "cancel" to "$KORAIL_MOBILE.reservationCancel.ReservationCancelChk",
            "myticketseat" to "$KORAIL_MOBILE.refunds.SelTicketInfo",
            "myticketlist" to "$KORAIL_MOBILE.myTicket.MyTicketList",
            "myreservationview" to "$KORAIL_MOBILE.reservation.ReservationView",
            "myreservationlist" to "$KORAIL_MOBILE.certification.ReservationList",
            "pay" to "$KORAIL_MOBILE.payment.ReservationPayment",
            "refund" to "$KORAIL_MOBILE.refunds.RefundsRequest",
            "code" to "$KORAIL_MOBILE.common.code.do"
        )

        private const val DEVICE = "AD"
        private const val VERSION = "260225001"
        private const val DEFAULT_KEY = "korail1234567890"

        private val EMAIL_REGEX = Regex("[^@]+@[^@]+\\.[^@]+")
        private val PHONE_REGEX = Regex("(\\d{3})-(\\d{3,4})-(\\d{4})")
    }

    var isLoggedIn = false
        private set
    var membershipNumber: String? = null
        private set
    var name: String? = null
        private set
    var email: String? = null
        private set
    var phoneNumber: String? = null
        private set

    private var key: String = DEFAULT_KEY
    private var idx: Int? = null

    override suspend fun login(id: String, password: String) {
        val txtInputFlg = when {
            EMAIL_REGEX.matches(id) -> "5"
            PHONE_REGEX.matches(id) -> "4"
            else -> "2"
        }

        val encResult = passwordEncryptor.encrypt(password)

        idx = encResult.idx

        val data = mapOf(
            "Device" to DEVICE,
            "Version" to VERSION,
            "Key" to key,
            "txtMemberNo" to id,
            "txtPwd" to encResult.encryptedPassword,
            "txtInputFlg" to txtInputFlg,
            "idx" to encResult.idx.toString()
        )

        val json = sessionManager.ktxPostForm(ENDPOINTS["login"]!!, data)

        val strResult = json.optString("strResult", "")
        if (strResult == "SUCC" && json.has("strMbCrdNo")) {
            membershipNumber = json.getString("strMbCrdNo")
            name = json.optString("strCustNm", "")
            email = json.optString("strEmailAdr", "")
            phoneNumber = json.optString("strCpNo", "")
            isLoggedIn = true
        } else {
            isLoggedIn = false
            throw KtxLoginException(
                json.optString("h_msg_txt", json.optString("strResult", "로그인 실패"))
            )
        }
    }

    override suspend fun logout() {
        sessionManager.ktxGet(ENDPOINTS["logout"]!!)
        isLoggedIn = false
    }

    override suspend fun searchTrains(
        departure: String,
        arrival: String,
        date: String,
        time: String,
        passengers: List<Passenger>,
        seatType: SeatType
    ): List<Train> {
        val combined = Passenger.combine(passengers)
        val counts = passengerCounts(combined)

        val data = mapOf(
            "Device" to DEVICE,
            "Version" to VERSION,
            "Sid" to "",
            "txtMenuId" to "11",
            "radJobId" to "1",
            "selGoTrain" to "109",
            "txtTrnGpCd" to "109",
            "txtGoStart" to departure,
            "txtGoEnd" to arrival,
            "txtGoAbrdDt" to date,
            "txtGoHour" to time,
            "txtPsgFlg_1" to counts["adult"].toString(),
            "txtPsgFlg_2" to counts["child"].toString(),
            "txtPsgFlg_3" to counts["senior"].toString(),
            "txtPsgFlg_4" to counts["disability1to3"].toString(),
            "txtPsgFlg_5" to counts["disability4to6"].toString(),
            "txtSeatAttCd_2" to "000",
            "txtSeatAttCd_3" to "000",
            "txtSeatAttCd_4" to "015",
            "ebizCrossCheck" to "N",
            "srtCheckYn" to "N",
            "rtYn" to "N",
            "adjStnScdlOfrFlg" to "N",
            "mbCrdNo" to (membershipNumber ?: "")
        )

        val json = sessionManager.ktxGet(ENDPOINTS["search_schedule"]!!, data)
        ResponseParser.checkKtxResult(json)

        val trnInfos = json.optJSONObject("trn_infos")
            ?.optJSONArray("trn_info")
            ?: throw KtxNoResultsException()

        val trains = mutableListOf<Train>()
        for (i in 0 until trnInfos.length()) {
            val trainData = ResponseParser.jsonObjectToMap(trnInfos.getJSONObject(i))
            val train = Train.fromKtxData(trainData)
            if (train.hasSeat() || train.isStandbyAvailable) {
                trains.add(train)
            }
        }

        if (trains.isEmpty()) {
            throw KtxNoResultsException()
        }

        return trains
    }

    override suspend fun reserve(
        train: Train,
        passengers: List<Passenger>,
        seatType: SeatType,
        windowSeat: Boolean?
    ): Reservation {
        val combined = Passenger.combine(passengers)
        val cnt = Passenger.totalCount(combined)

        val reservingSeat = train.hasSeat() || train.reserveWaitCode < 0
        val isSpecialSeat = if (reservingSeat) {
            when (seatType) {
                SeatType.GENERAL_ONLY -> false
                SeatType.SPECIAL_ONLY -> true
                SeatType.GENERAL_FIRST -> !train.isGeneralAvailable
                SeatType.SPECIAL_FIRST -> train.isSpecialAvailable
            }
        } else {
            when (seatType) {
                SeatType.GENERAL_ONLY -> false
                SeatType.SPECIAL_ONLY -> true
                SeatType.GENERAL_FIRST -> false
                SeatType.SPECIAL_FIRST -> true
            }
        }

        val data = mutableMapOf(
            "Device" to DEVICE,
            "Version" to VERSION,
            "Key" to key,
            "txtMenuId" to "11",
            "txtJobId" to if (reservingSeat) "1101" else "1102",
            "txtGdNo" to "",
            "hidFreeFlg" to "N",
            "txtTotPsgCnt" to cnt.toString(),
            "txtSeatAttCd1" to "000",
            "txtSeatAttCd2" to "000",
            "txtSeatAttCd3" to "000",
            "txtSeatAttCd4" to "015",
            "txtSeatAttCd5" to "000",
            "txtStndFlg" to "N",
            "txtSrcarCnt" to "0",
            "txtJrnyCnt" to "1",
            "txtJrnySqno1" to "001",
            "txtJrnyTpCd1" to "11",
            "txtDptDt1" to train.depDate,
            "txtDptRsStnCd1" to train.depStationCode,
            "txtDptTm1" to train.depTime,
            "txtArvRsStnCd1" to train.arrStationCode,
            "txtTrnNo1" to train.trainNumber,
            "txtRunDt1" to train.runDate,
            "txtTrnClsfCd1" to train.trainCode,
            "txtTrnGpCd1" to train.trainGroup,
            "txtPsrmClCd1" to if (isSpecialSeat) "2" else "1",
            "txtChgFlg1" to "",
            "txtJrnySqno2" to "",
            "txtJrnyTpCd2" to "",
            "txtDptDt2" to "",
            "txtDptRsStnCd2" to "",
            "txtDptTm2" to "",
            "txtArvRsStnCd2" to "",
            "txtTrnNo2" to "",
            "txtRunDt2" to "",
            "txtTrnClsfCd2" to "",
            "txtPsrmClCd2" to "",
            "txtChgFlg2" to ""
        )

        combined.forEachIndexed { index, passenger ->
            val i = index + 1
            data.putAll(Passenger.getKtxPassengerDict(listOf(passenger), i))
        }

        val json = sessionManager.ktxGet(ENDPOINTS["reserve"]!!, data)
        ResponseParser.checkKtxResult(json)

        val rsvId = json.optString("h_pnr_no", "")
        if (rsvId.isEmpty()) {
            throw KtxSoldOutException()
        }

        return getReservationById(rsvId)
            ?: throw KtxResponseException("Reservation not found after creation")
    }

    override suspend fun getReservations(): List<Reservation> {
        val data = mapOf(
            "Device" to DEVICE,
            "Version" to VERSION,
            "Key" to key
        )

        val json = sessionManager.ktxGet(ENDPOINTS["myreservationview"]!!, data)

        try {
            ResponseParser.checkKtxResult(json)
        } catch (e: KtxNoResultsException) {
            return emptyList()
        }

        val jrnyInfos = json.optJSONObject("jrny_infos")
            ?.optJSONArray("jrny_info")
            ?: return emptyList()

        val reservations = mutableListOf<Reservation>()
        for (i in 0 until jrnyInfos.length()) {
            val info = jrnyInfos.getJSONObject(i)
            val trainInfos = info.optJSONObject("train_infos")
                ?.optJSONArray("train_info")
                ?: continue

            for (j in 0 until trainInfos.length()) {
                val trainData = ResponseParser.jsonObjectToMap(trainInfos.getJSONObject(j))
                val rsvId = trainData["h_pnr_no"] as? String ?: continue
                val (tickets, wctNo) = getTicketInfoInternal(rsvId)
                reservations.add(
                    Reservation.fromKtxData(trainData, tickets, wctNo)
                )
            }
        }

        return reservations
    }

    private suspend fun getReservationById(rsvId: String): Reservation? {
        val reservations = getReservations()
        return reservations.find { it.reservationNumber == rsvId }
    }

    override suspend fun getTicketInfo(reservation: Reservation): List<Ticket> {
        val (tickets, _) = getTicketInfoInternal(reservation.reservationNumber)
        return tickets
    }

    private suspend fun getTicketInfoInternal(rsvId: String): Pair<List<Ticket>, String> {
        val data = mapOf(
            "Device" to DEVICE,
            "Version" to VERSION,
            "Key" to key,
            "hidPnrNo" to rsvId
        )

        val json = sessionManager.ktxGet(ENDPOINTS["myreservationlist"]!!, data)

        try {
            ResponseParser.checkKtxResult(json)
        } catch (e: KtxNoResultsException) {
            return Pair(emptyList(), "")
        }

        val wctNo = json.optString("h_wct_no", "")
        val jrnyInfos = json.optJSONObject("jrny_infos")
            ?.optJSONArray("jrny_info")

        if (jrnyInfos != null && jrnyInfos.length() > 0) {
            val seatInfos = jrnyInfos.getJSONObject(0)
                .optJSONObject("seat_infos")
                ?.optJSONArray("seat_info")

            if (seatInfos != null) {
                val tickets = mutableListOf<Ticket>()
                for (i in 0 until seatInfos.length()) {
                    val seatData = ResponseParser.jsonObjectToMap(seatInfos.getJSONObject(i))
                    tickets.add(Ticket.fromKtxSeatData(seatData))
                }
                return Pair(tickets, wctNo)
            }
        }

        return Pair(emptyList(), wctNo)
    }

    override suspend fun cancel(reservation: Reservation) {
        val data = mapOf(
            "Device" to DEVICE,
            "Version" to VERSION,
            "Key" to key,
            "txtPnrNo" to reservation.reservationNumber,
            "txtJrnySqno" to reservation.journeyNo,
            "txtJrnyCnt" to reservation.journeyCnt,
            "hidRsvChgNo" to reservation.rsvChgNo
        )

        val json = sessionManager.ktxPostForm(ENDPOINTS["cancel"]!!, data)
        ResponseParser.checkKtxResult(json)
    }

    override suspend fun payWithCard(
        reservation: Reservation,
        cardNumber: String,
        cardPassword: String,
        birthday: String,
        expireDate: String,
        installment: Int
    ) {
        val data = mapOf(
            "Device" to DEVICE,
            "Version" to VERSION,
            "Key" to key,
            "hidPnrNo" to reservation.reservationNumber,
            "hidWctNo" to reservation.wctNo,
            "hidTmpJobSqno1" to "000000",
            "hidTmpJobSqno2" to "000000",
            "hidRsvChgNo" to "000",
            "hidInrecmnsGridcnt" to "1",
            "hidStlMnsSqno1" to "1",
            "hidStlMnsCd1" to "02",
            "hidMnsStlAmt1" to reservation.totalCost.toString(),
            "hidCrdInpWayCd1" to "@",
            "hidStlCrCrdNo1" to cardNumber,
            "hidVanPwd1" to cardPassword,
            "hidCrdVlidTrm1" to expireDate,
            "hidIsmtMnthNum1" to installment.toString(),
            "hidAthnDvCd1" to "J",
            "hidAthnVal1" to birthday,
            "hiduserYn" to "Y"
        )

        val json = sessionManager.ktxPostForm(ENDPOINTS["pay"]!!, data)
        ResponseParser.checkKtxResult(json)
    }

    override suspend fun refund(reservation: Reservation) {
        val tickets = getTickets()
        val ticket = tickets.find { it.pnrNo == reservation.reservationNumber }
            ?: throw KtxResponseException("Ticket not found for refund")

        val data = mapOf(
            "Device" to DEVICE,
            "Version" to VERSION,
            "Key" to key,
            "txtPrnNo" to (ticket.pnrNo ?: ""),
            "h_orgtk_sale_dt" to (ticket.saleInfo2 ?: ""),
            "h_orgtk_sale_wct_no" to (ticket.saleInfo1 ?: ""),
            "h_orgtk_sale_sqno" to (ticket.saleInfo3 ?: ""),
            "h_orgtk_ret_pwd" to (ticket.saleInfo4 ?: ""),
            "h_mlg_stl" to "N",
            "tk_ret_tms_dv_cd" to "21",
            "trnNo" to reservation.trainNumber,
            "pbpAcepTgtFlg" to "N",
            "latitude" to "",
            "longitude" to ""
        )

        val json = sessionManager.ktxPostForm(ENDPOINTS["refund"]!!, data)
        ResponseParser.checkKtxResult(json)
    }

    private suspend fun getTickets(): List<Ticket> {
        val data = mapOf(
            "Device" to DEVICE,
            "Version" to VERSION,
            "Key" to key,
            "txtDeviceId" to "",
            "txtIndex" to "1",
            "h_page_no" to "1",
            "h_abrd_dt_from" to "",
            "h_abrd_dt_to" to "",
            "hiduserYn" to "Y"
        )

        val json = sessionManager.ktxGet(ENDPOINTS["myticketlist"]!!, data)

        try {
            ResponseParser.checkKtxResult(json)
        } catch (e: KtxNoResultsException) {
            return emptyList()
        }

        val reservationList = json.optJSONArray("reservation_list") ?: return emptyList()
        val tickets = mutableListOf<Ticket>()

        for (i in 0 until reservationList.length()) {
            val ticketData = ResponseParser.jsonObjectToMap(reservationList.getJSONObject(i))
            val ticket = Ticket.fromKtxTicketData(ticketData)

            // Fetch seat info
            val seatData = mapOf(
                "Device" to DEVICE,
                "Version" to VERSION,
                "Key" to key,
                "h_orgtk_wct_no" to (ticket.saleInfo1 ?: ""),
                "h_orgtk_ret_sale_dt" to (ticket.saleInfo2 ?: ""),
                "h_orgtk_sale_sqno" to (ticket.saleInfo3 ?: ""),
                "h_orgtk_ret_pwd" to (ticket.saleInfo4 ?: "")
            )

            try {
                val seatJson = sessionManager.ktxGet(ENDPOINTS["myticketseat"]!!, seatData)
                ResponseParser.checkKtxResult(seatJson)
                val seatNo = seatJson.optJSONObject("ticket_infos")
                    ?.optJSONArray("ticket_info")
                    ?.optJSONObject(0)
                    ?.optJSONArray("tk_seat_info")
                    ?.optJSONObject(0)
                    ?.optString("h_seat_no")

                tickets.add(ticket.copy(seat = seatNo ?: ticket.seat))
            } catch (_: Exception) {
                tickets.add(ticket)
            }
        }

        return tickets
    }

    override fun clearNetFunnel() {
        netFunnelHelper.clear(RailType.KTX)
    }

    private fun passengerCounts(passengers: List<Passenger>): Map<String, Int> {
        return mapOf(
            "adult" to passengers.filter { it.type == PassengerType.ADULT }.sumOf { it.count },
            "child" to passengers.filter { it.type == PassengerType.CHILD }.sumOf { it.count },
            "senior" to passengers.filter { it.type == PassengerType.SENIOR }.sumOf { it.count },
            "disability1to3" to passengers.filter { it.type == PassengerType.DISABILITY_1_3 }.sumOf { it.count },
            "disability4to6" to passengers.filter { it.type == PassengerType.DISABILITY_4_6 }.sumOf { it.count }
        )
    }
}
