package com.srtgo.app.core.exception

sealed class RailException(message: String, val code: String? = null) : Exception(message)

// SRT exceptions
class SrtLoginException(message: String) : RailException(message)
class SrtResponseException(message: String) : RailException(message)
class SrtDuplicateException(message: String) : RailException(message)
class SrtNotLoggedInException(
    message: String = "SRT 로그인이 필요합니다"
) : RailException(message)
class SrtNetFunnelException(message: String) : RailException(message)

// KTX exceptions
class KtxLoginException(message: String) : RailException(message)
class KtxResponseException(message: String, code: String? = null) : RailException(message, code)
class KtxNoResultsException(
    code: String? = null
) : RailException("검색 결과가 없습니다", code) {
    companion object {
        val CODES = setOf("P100", "WRG000000", "WRD000061", "WRT300005")
    }
}
class KtxSoldOutException(
    code: String? = null
) : RailException("매진되었습니다", code) {
    companion object {
        val CODES = setOf("IRT010110", "ERR211161")
    }
}
class KtxNetFunnelException(message: String) : RailException(message)
class KtxNeedLoginException(
    code: String? = null
) : RailException("KTX 로그인이 필요합니다", code) {
    companion object {
        val CODES = setOf("P058")
    }
}
