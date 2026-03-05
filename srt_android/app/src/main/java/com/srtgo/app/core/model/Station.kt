package com.srtgo.app.core.model

object Station {

    private val SRT_STATION_CODE: Map<String, String> = mapOf(
        "수서" to "0551",
        "동탄" to "0552",
        "평택지제" to "0553",
        "경주" to "0508",
        "곡성" to "0049",
        "공주" to "0514",
        "광주송정" to "0036",
        "구례구" to "0050",
        "김천(구미)" to "0507",
        "나주" to "0037",
        "남원" to "0048",
        "대전" to "0010",
        "동대구" to "0015",
        "마산" to "0059",
        "목포" to "0041",
        "밀양" to "0017",
        "부산" to "0020",
        "서대구" to "0506",
        "순천" to "0051",
        "여수EXPO" to "0053",
        "여천" to "0139",
        "오송" to "0297",
        "울산(통도사)" to "0509",
        "익산" to "0030",
        "전주" to "0045",
        "정읍" to "0033",
        "진영" to "0056",
        "진주" to "0063",
        "창원" to "0057",
        "창원중앙" to "0512",
        "천안아산" to "0502",
        "포항" to "0515"
    )

    val SRT_STATIONS: Map<String, String> = SRT_STATION_CODE

    private val SRT_STATION_NAME: Map<String, String> =
        SRT_STATION_CODE.entries.associate { (name, code) -> code to name }

    private val KTX_STATION_CODE: Map<String, String> = mapOf(
        "서울" to "SEL",
        "용산" to "YSN",
        "영등포" to "YDP",
        "광명" to "KWM",
        "수원" to "SWN",
        "천안아산" to "CNA",
        "오송" to "OSN",
        "대전" to "DJN",
        "서대전" to "SDJ",
        "김천구미" to "KCG",
        "동대구" to "DDG",
        "경주" to "GJU",
        "포항" to "POH",
        "밀양" to "MYN",
        "구포" to "GPU",
        "부산" to "PUS",
        "울산(통도사)" to "ULS",
        "마산" to "MAS",
        "창원중앙" to "CWJ",
        "경산" to "GSN",
        "논산" to "NSN",
        "익산" to "IKS",
        "정읍" to "JEU",
        "광주송정" to "KJS",
        "목포" to "MKP",
        "전주" to "JNJ",
        "순천" to "SCN",
        "여수EXPO" to "YSE",
        "청량리" to "CRL",
        "강릉" to "GNE",
        "행신" to "HAS",
        "정동진" to "JDJ",
        "진영" to "JYG"
    )

    val KTX_STATIONS: Map<String, String> = KTX_STATION_CODE

    private val KTX_STATION_NAME: Map<String, String> =
        KTX_STATION_CODE.entries.associate { (name, code) -> code to name }

    fun getStations(railType: RailType): List<String> = when (railType) {
        RailType.SRT -> SRT_STATION_CODE.keys.toList()
        RailType.KTX -> KTX_STATION_CODE.keys.toList()
    }

    fun getCode(railType: RailType, name: String): String = when (railType) {
        RailType.SRT -> SRT_STATION_CODE[name]
            ?: throw IllegalArgumentException("Unknown SRT station: $name")
        RailType.KTX -> KTX_STATION_CODE[name]
            ?: throw IllegalArgumentException("Unknown KTX station: $name")
    }

    fun getName(railType: RailType, code: String): String = when (railType) {
        RailType.SRT -> SRT_STATION_NAME[code] ?: code
        RailType.KTX -> KTX_STATION_NAME[code] ?: code
    }

    fun isValidStation(railType: RailType, name: String): Boolean = when (railType) {
        RailType.SRT -> name in SRT_STATION_CODE
        RailType.KTX -> name in KTX_STATION_CODE
    }
}
