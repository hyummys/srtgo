package com.srtgo.app.core.network

object UserAgents {
    const val SRT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; SM-S912N Build/AP3A.240905.015.A2; wv) AppleWebKit/537.36" +
        "(KHTML, like Gecko) Version/4.0 Chrome/136.0.7103.125 Mobile Safari/537.36SRT-APP-Android V.2.0.38"

    const val KTX_USER_AGENT =
        "Dalvik/2.1.0 (Linux; U; Android 14; SM-S912N Build/UP1A.231005.007)"

    val SRT_DEFAULT_HEADERS = mapOf(
        "User-Agent" to SRT_USER_AGENT,
        "Accept" to "application/json"
    )

    val KTX_DEFAULT_HEADERS = mapOf(
        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
        "User-Agent" to KTX_USER_AGENT,
        "Host" to "www.korail.com",
        "Connection" to "Keep-Alive",
        "Accept-Encoding" to "gzip"
    )

    val NETFUNNEL_HEADERS = mapOf(
        "Host" to "nf.letskorail.com",
        "Connection" to "keep-alive",
        "Pragma" to "no-cache",
        "Cache-Control" to "no-cache",
        "sec-ch-ua-platform" to "Android",
        "User-Agent" to SRT_USER_AGENT,
        "sec-ch-ua" to "\"Chromium\";v=\"136\", \"Android WebView\";v=\"136\", \"Not=A/Brand\";v=\"99\"",
        "sec-ch-ua-mobile" to "?1",
        "Accept" to "*/*",
        "X-Requested-With" to "kr.co.srail.newapp",
        "Sec-Fetch-Site" to "cross-site",
        "Sec-Fetch-Mode" to "no-cors",
        "Sec-Fetch-Dest" to "script",
        "Sec-Fetch-Storage-Access" to "active",
        "Referer" to "https://app.srail.or.kr/",
        "Accept-Encoding" to "gzip, deflate, br, zstd",
        "Accept-Language" to "en-US,en;q=0.9,ko-KR;q=0.8,ko;q=0.7"
    )

    val KTX_NETFUNNEL_HEADERS = mapOf(
        "Host" to "nf.letskorail.com",
        "Connection" to "Keep-Alive",
        "User-Agent" to "Apache-HttpClient/UNAVAILABLE (java 1.4)"
    )
}
