package com.srtgo.app.core.model

data class Passenger(
    val type: PassengerType,
    val count: Int = 1
) {
    val displayName: String
        get() = "${type.displayName} ${count}명"

    companion object {
        fun combine(passengers: List<Passenger>): List<Passenger> {
            return passengers
                .groupBy { it.type }
                .map { (type, list) -> Passenger(type, list.sumOf { it.count }) }
                .filter { it.count > 0 }
        }

        fun totalCount(passengers: List<Passenger>): Int {
            return passengers.sumOf { it.count }
        }

        fun getSrtPassengerDict(
            passengers: List<Passenger>,
            specialSeat: Boolean = false,
            windowSeat: Boolean? = null
        ): Map<String, String> {
            val combined = combine(passengers)
            val windowSeatCode = when (windowSeat) {
                null -> "000"
                true -> "012"
                false -> "013"
            }
            val data = mutableMapOf(
                "totPrnb" to totalCount(combined).toString(),
                "psgGridcnt" to combined.size.toString(),
                "locSeatAttCd1" to windowSeatCode,
                "rqSeatAttCd1" to "015",
                "dirSeatAttCd1" to "009",
                "smkSeatAttCd1" to "000",
                "etcSeatAttCd1" to "000",
                "psrmClCd1" to if (specialSeat) "2" else "1"
            )

            combined.forEachIndexed { index, passenger ->
                val i = index + 1
                data["psgTpCd$i"] = passenger.type.code
                data["psgInfoPerPrnb$i"] = passenger.count.toString()
            }

            return data
        }

        fun getKtxPassengerDict(
            passengers: List<Passenger>,
            index: Int = 1
        ): Map<String, String> {
            val combined = combine(passengers)
            val data = mutableMapOf<String, String>()
            combined.forEachIndexed { idx, passenger ->
                val i = idx + index
                val iStr = i.toString()
                data["txtPsgTpCd$iStr"] = passenger.type.code
                data["txtDiscKndCd$iStr"] = "000"
                data["txtCompaCnt$iStr"] = passenger.count.toString()
                data["txtCardCode_$iStr"] = ""
                data["txtCardNo_$iStr"] = ""
                data["txtCardPw_$iStr"] = ""
            }
            return data
        }
    }
}
