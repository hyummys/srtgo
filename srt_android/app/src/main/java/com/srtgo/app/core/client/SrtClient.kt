package com.srtgo.app.core.client

import com.srtgo.app.core.exception.*
import com.srtgo.app.core.model.*
import com.srtgo.app.core.network.NetFunnelHelper
import com.srtgo.app.core.network.SessionManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SrtClient @Inject constructor(
    private val sessionManager: SessionManager,
    private val netFunnelHelper: NetFunnelHelper
) : RailClient {

    companion object {
        private const val SRT_MOBILE = "https://app.srail.or.kr:443"

        private val ENDPOINTS = mapOf(
            "main" to "$SRT_MOBILE/main/main.do",
            "login" to "$SRT_MOBILE/apb/selectListApb01080_n.do",
            "logout" to "$SRT_MOBILE/login/loginOut.do",
            "search_schedule" to "$SRT_MOBILE/ara/selectListAra10007_n.do",
            "reserve" to "$SRT_MOBILE/arc/selectListArc05013_n.do",
            "tickets" to "$SRT_MOBILE/atc/selectListAtc14016_n.do",
            "ticket_info" to "$SRT_MOBILE/ard/selectListArd02019_n.do",
            "cancel" to "$SRT_MOBILE/ard/selectListArd02045_n.do",
            "standby_option" to "$SRT_MOBILE/ata/selectListAta01135_n.do",
            "payment" to "$SRT_MOBILE/ata/selectListAta09036_n.do",
            "reserve_info" to "$SRT_MOBILE/atc/getListAtc14087.do",
            "reserve_info_referer" to "$SRT_MOBILE/common/ATC/ATC0201L/view.do?pnrNo=",
            "refund" to "$SRT_MOBILE/atc/selectListAtc02063_n.do"
        )

        private val RESERVE_JOBID = mapOf(
            "PERSONAL" to "1101",
            "STANDBY" to "1102"
        )

        private val EMAIL_REGEX = Regex("[^@]+@[^@]+\\.[^@]+")
        private val PHONE_REGEX = Regex("(\\d{3})-(\\d{3,4})-(\\d{4})")
    }

    var isLoggedIn = false
        private set
    var membershipNumber: String? = null
        private set
    var membershipName: String? = null
        private set
    var phoneNumber: String? = null
        private set

    override suspend fun login(id: String, password: String) {
        val loginType = when {
            EMAIL_REGEX.matches(id) -> "2"
            PHONE_REGEX.matches(id) -> "3"
            else -> "1"
        }

        val cleanId = if (loginType == "3") id.replace("-", "") else id

        val data = mapOf(
            "auto" to "Y",
            "check" to "Y",
            "page" to "menu",
            "deviceKey" to "-",
            "customerYn" to "",
            "login_referer" to ENDPOINTS["main"]!!,
            "srchDvCd" to loginType,
            "srchDvNm" to cleanId,
            "hmpgPwdCphd" to password
        )

        val responseText = sessionManager.srtPostFormRaw(ENDPOINTS["login"]!!, data)

        if ("존재하지않는 회원입니다" in responseText) {
            val json = JSONObject(responseText)
            throw SrtLoginException(json.optString("MSG", "존재하지않는 회원입니다"))
        }
        if ("비밀번호 오류" in responseText) {
            val json = JSONObject(responseText)
            throw SrtLoginException(json.optString("MSG", "비밀번호 오류"))
        }
        if ("Your IP Address Blocked" in responseText) {
            throw SrtLoginException(responseText.trim())
        }

        val json = JSONObject(responseText)
        isLoggedIn = true
        val userInfo = json.getJSONObject("userMap")
        membershipNumber = userInfo.getString("MB_CRD_NO")
        membershipName = userInfo.getString("CUST_NM")
        phoneNumber = userInfo.getString("MBL_PHONE")
    }

    override suspend fun logout() {
        if (!isLoggedIn) return
        sessionManager.srtPostForm(ENDPOINTS["logout"]!!, emptyMap())
        isLoggedIn = false
        membershipNumber = null
    }

    override suspend fun searchTrains(
        departure: String,
        arrival: String,
        date: String,
        time: String,
        passengers: List<Passenger>,
        seatType: SeatType
    ): List<Train> {
        // departure/arrival can be either station code or name
        val depCode = if (Station.isValidStation(RailType.SRT, departure)) {
            Station.getCode(RailType.SRT, departure)
        } else {
            departure  // already a code
        }
        val arrCode = if (Station.isValidStation(RailType.SRT, arrival)) {
            Station.getCode(RailType.SRT, arrival)
        } else {
            arrival  // already a code
        }

        val combined = Passenger.combine(passengers)
        val netfunnelKey = netFunnelHelper.run(RailType.SRT)

        val data = mapOf(
            "chtnDvCd" to "1",
            "dptDt" to date,
            "dptTm" to time,
            "dptDt1" to date,
            "dptTm1" to time.substring(0, 2) + "0000",
            "dptRsStnCd" to depCode,
            "arvRsStnCd" to arrCode,
            "stlbTrnClsfCd" to "05",
            "trnGpCd" to "109",
            "trnNo" to "",
            "psgNum" to Passenger.totalCount(combined).toString(),
            "seatAttCd" to "015",
            "arriveTime" to "N",
            "tkDptDt" to "",
            "tkDptTm" to "",
            "tkTrnNo" to "",
            "tkTripChgFlg" to "",
            "dlayTnumAplFlg" to "Y",
            "netfunnelKey" to netfunnelKey
        )

        val responseText = sessionManager.srtPostFormRaw(ENDPOINTS["search_schedule"]!!, data)
        val parsed = ResponseParser.parseSrtResponse(responseText)

        if (!parsed.success) {
            throw SrtResponseException(parsed.message)
        }

        val output = ResponseParser.getOutputArray(parsed.json, "dsOutput1")
        val trains = mutableListOf<Train>()
        for (i in 0 until output.length()) {
            val trainData = ResponseParser.jsonObjectToMap(output.getJSONObject(i))
            if (trainData["stlbTrnClsfCd"] == "17") {
                trains.add(Train.fromSrtData(trainData))
            }
        }

        return trains
    }

    override suspend fun reserve(
        train: Train,
        passengers: List<Passenger>,
        seatType: SeatType,
        windowSeat: Boolean?
    ): Reservation {
        if (!isLoggedIn) throw SrtNotLoggedInException()

        if (train.trainName != "SRT") {
            throw IllegalArgumentException("Expected SRT train, got ${train.trainName}")
        }

        if (!train.hasSeat() && train.reserveWaitCode >= 0) {
            val reservation = reserveStandby(train, passengers, seatType)
            if (phoneNumber != null) {
                val agreeClassChange = seatType == SeatType.SPECIAL_FIRST ||
                        seatType == SeatType.GENERAL_FIRST
                reserveStandbyOptionSettings(
                    reservation,
                    isAgreeSMS = true,
                    isAgreeClassChange = agreeClassChange,
                    telNo = phoneNumber!!
                )
            }
            return reservation
        }

        return doReserve(
            RESERVE_JOBID["PERSONAL"]!!,
            train,
            passengers,
            seatType,
            windowSeat = windowSeat
        )
    }

    private suspend fun reserveStandby(
        train: Train,
        passengers: List<Passenger>,
        seatType: SeatType
    ): Reservation {
        val adjustedSeatType = when (seatType) {
            SeatType.SPECIAL_FIRST -> SeatType.SPECIAL_ONLY
            SeatType.GENERAL_FIRST -> SeatType.GENERAL_ONLY
            else -> seatType
        }

        return doReserve(
            RESERVE_JOBID["STANDBY"]!!,
            train,
            passengers,
            adjustedSeatType,
            mblPhone = phoneNumber
        )
    }

    private suspend fun doReserve(
        jobId: String,
        train: Train,
        passengers: List<Passenger>,
        seatType: SeatType,
        mblPhone: String? = null,
        windowSeat: Boolean? = null
    ): Reservation {
        if (!isLoggedIn) throw SrtNotLoggedInException()

        val combined = Passenger.combine(passengers)

        val isSpecialSeat = when (seatType) {
            SeatType.GENERAL_ONLY -> false
            SeatType.SPECIAL_ONLY -> true
            SeatType.GENERAL_FIRST -> !train.isGeneralAvailable
            SeatType.SPECIAL_FIRST -> train.isSpecialAvailable
        }

        val netfunnelKey = netFunnelHelper.run(RailType.SRT)

        val data = mutableMapOf(
            "jobId" to jobId,
            "jrnyCnt" to "1",
            "jrnyTpCd" to "11",
            "jrnySqno1" to "001",
            "stndFlg" to "N",
            "trnGpCd1" to "300",
            "trnGpCd" to "109",
            "grpDv" to "0",
            "rtnDv" to "0",
            "stlbTrnClsfCd1" to train.trainCode,
            "dptRsStnCd1" to train.depStationCode,
            "dptRsStnCdNm1" to train.depStationName,
            "arvRsStnCd1" to train.arrStationCode,
            "arvRsStnCdNm1" to train.arrStationName,
            "dptDt1" to train.depDate,
            "dptTm1" to train.depTime,
            "arvTm1" to train.arrTime,
            "trnNo1" to String.format("%05d", train.trainNumber.toInt()),
            "runDt1" to train.depDate,
            "dptStnConsOrdr1" to train.depStationConsOrder,
            "arvStnConsOrdr1" to train.arrStationConsOrder,
            "dptStnRunOrdr1" to train.depStationRunOrder,
            "arvStnRunOrdr1" to train.arrStationRunOrder,
            "mblPhone" to (mblPhone ?: ""),
            "netfunnelKey" to netfunnelKey
        )

        if (jobId == RESERVE_JOBID["PERSONAL"]) {
            data["reserveType"] = "11"
        }

        data.putAll(
            Passenger.getSrtPassengerDict(
                combined,
                specialSeat = isSpecialSeat,
                windowSeat = windowSeat
            )
        )

        val responseText = sessionManager.srtPostFormRaw(ENDPOINTS["reserve"]!!, data)
        val parsed = ResponseParser.parseSrtResponse(responseText)

        if (!parsed.success) {
            val message = parsed.message
            if ("중복" in message) {
                throw SrtDuplicateException(message)
            }
            throw SrtResponseException(message)
        }

        val reserveListMap = parsed.json.getJSONArray("reservListMap")
        val reservationNumber = reserveListMap.getJSONObject(0).getString("pnrNo")

        val reservations = getReservations()
        return reservations.find { it.reservationNumber == reservationNumber }
            ?: throw SrtResponseException("Reservation not found after creation")
    }

    private suspend fun reserveStandbyOptionSettings(
        reservation: Reservation,
        isAgreeSMS: Boolean,
        isAgreeClassChange: Boolean,
        telNo: String
    ) {
        if (!isLoggedIn) throw SrtNotLoggedInException()

        val data = mapOf(
            "pnrNo" to reservation.reservationNumber,
            "psrmClChgFlg" to if (isAgreeClassChange) "Y" else "N",
            "smsSndFlg" to if (isAgreeSMS) "Y" else "N",
            "telNo" to if (isAgreeSMS) telNo else ""
        )

        sessionManager.srtPostForm(ENDPOINTS["standby_option"]!!, data)
    }

    override suspend fun getReservations(): List<Reservation> {
        if (!isLoggedIn) throw SrtNotLoggedInException()

        val responseText = sessionManager.srtPostFormRaw(
            ENDPOINTS["tickets"]!!,
            mapOf("pageNo" to "0")
        )
        val parsed = ResponseParser.parseSrtResponse(responseText)

        if (!parsed.success) {
            throw SrtResponseException(parsed.message)
        }

        val trainListMap = parsed.json.optJSONArray("trainListMap") ?: return emptyList()
        val payListMap = parsed.json.optJSONArray("payListMap") ?: return emptyList()

        val reservations = mutableListOf<Reservation>()
        for (i in 0 until trainListMap.length()) {
            val trainData = ResponseParser.jsonObjectToMap(trainListMap.getJSONObject(i))
            val payData = ResponseParser.jsonObjectToMap(payListMap.getJSONObject(i))
            val pnrNo = trainData["pnrNo"] as? String ?: continue
            val tickets = getTicketInfoByNumber(pnrNo)
            reservations.add(Reservation.fromSrtData(trainData, payData, tickets))
        }

        return reservations
    }

    override suspend fun getTicketInfo(reservation: Reservation): List<Ticket> {
        return getTicketInfoByNumber(reservation.reservationNumber)
    }

    private suspend fun getTicketInfoByNumber(reservationNumber: String): List<Ticket> {
        if (!isLoggedIn) throw SrtNotLoggedInException()

        val responseText = sessionManager.srtPostFormRaw(
            ENDPOINTS["ticket_info"]!!,
            mapOf("pnrNo" to reservationNumber, "jrnySqno" to "1")
        )
        val parsed = ResponseParser.parseSrtResponse(responseText)

        if (!parsed.success) {
            throw SrtResponseException(parsed.message)
        }

        val trainListMap = parsed.json.optJSONArray("trainListMap") ?: return emptyList()
        return ResponseParser.jsonArrayToListOfMaps(trainListMap).map { Ticket.fromSrtData(it) }
    }

    override suspend fun cancel(reservation: Reservation) {
        if (!isLoggedIn) throw SrtNotLoggedInException()

        val data = mapOf(
            "pnrNo" to reservation.reservationNumber,
            "jrnyCnt" to "1",
            "rsvChgTno" to "0"
        )

        val responseText = sessionManager.srtPostFormRaw(ENDPOINTS["cancel"]!!, data)
        val parsed = ResponseParser.parseSrtResponse(responseText)

        if (!parsed.success) {
            throw SrtResponseException(parsed.message)
        }
    }

    override suspend fun payWithCard(
        reservation: Reservation,
        cardNumber: String,
        cardPassword: String,
        birthday: String,
        expireDate: String,
        installment: Int
    ) {
        if (!isLoggedIn) throw SrtNotLoggedInException()

        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREA)
        val today = dateFormat.format(Date())

        val data = mapOf(
            "stlDmnDt" to today,
            "mbCrdNo" to (membershipNumber ?: ""),
            "stlMnsSqno1" to "1",
            "ststlGridcnt" to "1",
            "totNewStlAmt" to reservation.totalCost.toString(),
            "athnDvCd1" to "J",
            "vanPwd1" to cardPassword,
            "crdVlidTrm1" to expireDate,
            "stlMnsCd1" to "02",
            "rsvChgTno" to "0",
            "chgMcs" to "0",
            "ismtMnthNum1" to installment.toString(),
            "ctlDvCd" to "3102",
            "cgPsId" to "korail",
            "pnrNo" to reservation.reservationNumber,
            "totPrnb" to reservation.seatCount.toString(),
            "mnsStlAmt1" to reservation.totalCost.toString(),
            "crdInpWayCd1" to "@",
            "athnVal1" to birthday,
            "stlCrCrdNo1" to cardNumber,
            "jrnyCnt" to "1",
            "strJobId" to "3102",
            "inrecmnsGridcnt" to "1",
            "dptTm" to reservation.depTime,
            "arvTm" to reservation.arrTime,
            "dptStnConsOrdr2" to "000000",
            "arvStnConsOrdr2" to "000000",
            "trnGpCd" to "300",
            "pageNo" to "-",
            "rowCnt" to "-",
            "pageUrl" to ""
        )

        val responseText = sessionManager.srtPostFormRaw(ENDPOINTS["payment"]!!, data)
        val json = JSONObject(responseText)

        val dsOutput0 = json.optJSONObject("outDataSets")
            ?.optJSONArray("dsOutput0")
            ?.optJSONObject(0)

        if (dsOutput0?.optString("strResult") == "FAIL") {
            throw SrtResponseException(dsOutput0.optString("msgTxt", "결제 실패"))
        }
    }

    override suspend fun refund(reservation: Reservation) {
        if (!isLoggedIn) throw SrtNotLoggedInException()

        val info = getReserveInfo(reservation)

        val data = mapOf(
            "pnr_no" to (info["pnrNo"] as? String ?: ""),
            "cnc_dmn_cont" to "승차권 환불로 취소",
            "saleDt" to (info["ogtkSaleDt"] as? String ?: ""),
            "saleWctNo" to (info["ogtkSaleWctNo"] as? String ?: ""),
            "saleSqno" to (info["ogtkSaleSqno"] as? String ?: ""),
            "tkRetPwd" to (info["ogtkRetPwd"] as? String ?: ""),
            "psgNm" to (info["buyPsNm"] as? String ?: "")
        )

        val responseText = sessionManager.srtPostFormRaw(ENDPOINTS["refund"]!!, data)
        val parsed = ResponseParser.parseSrtResponse(responseText)

        if (!parsed.success) {
            throw SrtResponseException(parsed.message)
        }
    }

    private suspend fun getReserveInfo(reservation: Reservation): Map<String, Any?> {
        val json = sessionManager.srtPostForm(ENDPOINTS["reserve_info"]!!, emptyMap())

        val errorCode = json.optString("ErrorCode", "")
        val errorMsg = json.optString("ErrorMsg", "")
        if (errorCode == "0" && errorMsg.isEmpty()) {
            val dsOutput1 = json.optJSONObject("outDataSets")
                ?.optJSONArray("dsOutput1")
                ?.optJSONObject(0)
            if (dsOutput1 != null) {
                return ResponseParser.jsonObjectToMap(dsOutput1)
            }
        }
        throw SrtResponseException(errorMsg.ifEmpty { "Failed to get reserve info" })
    }

    override fun clearNetFunnel() {
        netFunnelHelper.clear(RailType.SRT)
    }
}
