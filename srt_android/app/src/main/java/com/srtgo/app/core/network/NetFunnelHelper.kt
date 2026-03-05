package com.srtgo.app.core.network

import com.srtgo.app.core.exception.KtxNetFunnelException
import com.srtgo.app.core.exception.SrtNetFunnelException
import com.srtgo.app.core.model.RailType
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class NetFunnelHelper @Inject constructor(
    private val sessionManager: SessionManager
) {

    companion object {
        private const val WAIT_STATUS_PASS = "200"
        private const val WAIT_STATUS_FAIL = "201"
        private const val ALREADY_COMPLETED = "502"

        private val OP_CODES = mapOf(
            "getTidchkEnter" to "5101",
            "chkEnter" to "5002",
            "setComplete" to "5004"
        )

        private const val SRT_CACHE_TTL = 48
        private const val KTX_CACHE_TTL = 50

        private val RESPONSE_PATTERN = Regex("NetFunnel\\.gControl\\.result='([^']+)'")
    }

    private var srtCachedKey: String? = null
    private var srtLastFetchTime: Long = 0
    private var ktxCachedKey: String? = null
    private var ktxLastFetchTime: Long = 0

    suspend fun run(railType: RailType): String {
        val currentTime = System.currentTimeMillis()
        val cacheTtl = if (railType == RailType.SRT) SRT_CACHE_TTL else KTX_CACHE_TTL

        if (isCacheValid(railType, currentTime, cacheTtl)) {
            return getCachedKey(railType)!!
        }

        try {
            val result = start(railType)
            var status = result.status
            var key = result.key
            var ip = result.ip

            setCachedKey(railType, key, currentTime)

            while (status == WAIT_STATUS_FAIL) {
                delay(1000)
                val checkResult = check(railType, ip)
                status = checkResult.status
                key = checkResult.key
                ip = checkResult.ip
                setCachedKey(railType, key, System.currentTimeMillis())
            }

            val completeResult = complete(railType, ip)
            if (completeResult.status == WAIT_STATUS_PASS || completeResult.status == ALREADY_COMPLETED) {
                return getCachedKey(railType)!!
            }

            clear(railType)
            throwNetFunnelError(railType, "Failed to complete NetFunnel")
        } catch (e: Exception) {
            if (e is SrtNetFunnelException || e is KtxNetFunnelException) throw e
            clear(railType)
            throwNetFunnelError(railType, e.message ?: "Unknown NetFunnel error")
        }
    }

    fun clear(railType: RailType) {
        when (railType) {
            RailType.SRT -> { srtCachedKey = null; srtLastFetchTime = 0 }
            RailType.KTX -> { ktxCachedKey = null; ktxLastFetchTime = 0 }
        }
    }

    private suspend fun start(railType: RailType): NetFunnelResult =
        makeRequest(railType, "getTidchkEnter")

    private suspend fun check(railType: RailType, ip: String? = null): NetFunnelResult =
        makeRequest(railType, "chkEnter", ip)

    private suspend fun complete(railType: RailType, ip: String? = null): NetFunnelResult =
        makeRequest(railType, "setComplete", ip)

    private suspend fun makeRequest(
        railType: RailType,
        opcode: String,
        ip: String? = null
    ): NetFunnelResult {
        val host = ip ?: "nf.letskorail.com"
        val baseUrl = "https://$host/ts.wseq"
        val params = buildParams(railType, OP_CODES[opcode]!!)

        val queryString = params.entries.joinToString("&") { (k, v) ->
            if (v.isEmpty()) URLEncoder.encode(k, "UTF-8")
            else "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val fullUrl = "$baseUrl?$queryString"

        val headers = if (railType == RailType.SRT) UserAgents.NETFUNNEL_HEADERS else UserAgents.KTX_NETFUNNEL_HEADERS

        val responseText = cronetGet(fullUrl, headers)
        return parseResponse(railType, responseText)
    }

    private suspend fun cronetGet(
        url: String,
        headers: Map<String, String>
    ): String = suspendCancellableCoroutine { cont ->
        val engine = sessionManager.getEngine()
        val executor = sessionManager.getExecutor()

        val callback = object : UrlRequest.Callback() {
            private val responseBody = ByteArrayOutputStream()

            override fun onRedirectReceived(
                request: UrlRequest, info: UrlResponseInfo, newUrl: String
            ) {
                request.followRedirect()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                request.read(ByteBuffer.allocateDirect(32768))
            }

            override fun onReadCompleted(
                request: UrlRequest, info: UrlResponseInfo, buffer: ByteBuffer
            ) {
                buffer.flip()
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                responseBody.write(bytes)
                buffer.clear()
                request.read(buffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                if (cont.isActive) cont.resume(responseBody.toString("UTF-8"))
            }

            override fun onFailed(
                request: UrlRequest, info: UrlResponseInfo?, error: CronetException
            ) {
                if (cont.isActive) cont.resumeWithException(error)
            }
        }

        val reqBuilder = engine.newUrlRequestBuilder(url, callback, executor)
            .setHttpMethod("GET")

        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

        val request = reqBuilder.build()
        cont.invokeOnCancellation { request.cancel() }
        request.start()
    }

    private fun buildParams(railType: RailType, opcode: String): Map<String, String> {
        val params = mutableMapOf(
            "opcode" to opcode,
            "nfid" to "0",
            "prefix" to "NetFunnel.gRtype=$opcode;",
            "js" to "true",
            System.currentTimeMillis().toString() to ""
        )

        val aid = if (railType == RailType.SRT) "act_10" else "act_8"

        if (opcode == OP_CODES["getTidchkEnter"] || opcode == OP_CODES["chkEnter"]) {
            params["sid"] = "service_1"
            params["aid"] = aid
            if (opcode == OP_CODES["chkEnter"]) {
                params["key"] = getCachedKey(railType) ?: ""
                params["ttl"] = "1"
            }
        } else if (opcode == OP_CODES["setComplete"]) {
            params["key"] = getCachedKey(railType) ?: ""
        }

        return params
    }

    private fun parseResponse(railType: RailType, response: String): NetFunnelResult {
        return when (railType) {
            RailType.SRT -> parseSrtResponse(response)
            RailType.KTX -> parseKtxResponse(response)
        }
    }

    private fun parseSrtResponse(response: String): NetFunnelResult {
        val match = RESPONSE_PATTERN.find(response)
            ?: throw SrtNetFunnelException("Failed to parse NetFunnel response")

        val parts = match.groupValues[1].split(":", limit = 3)
        if (parts.size < 3) throw SrtNetFunnelException("Failed to parse NetFunnel response")

        val status = parts[1]
        val paramsStr = parts[2]

        val params = paramsStr.split("&")
            .filter { "=" in it }
            .associate { param ->
                val (k, v) = param.split("=", limit = 2)
                k to v
            }

        return NetFunnelResult(
            status = status,
            key = params["key"] ?: "",
            nwait = params["nwait"] ?: "0",
            ip = params["ip"]
        )
    }

    private fun parseKtxResponse(response: String): NetFunnelResult {
        val parts = response.split(":", limit = 2)
        if (parts.size < 2) throw KtxNetFunnelException("Failed to parse NetFunnel response")

        val status = parts[0]
        val paramsStr = parts[1]

        val params = paramsStr.split("&")
            .filter { "=" in it }
            .associate { param ->
                val (k, v) = param.split("=", limit = 2)
                k to v
            }

        return NetFunnelResult(
            status = status,
            key = params["key"] ?: "",
            nwait = params["nwait"] ?: "0",
            ip = params["ip"]
        )
    }

    private fun isCacheValid(railType: RailType, currentTime: Long, cacheTtlSeconds: Int): Boolean {
        val cachedKey = getCachedKey(railType)
        val lastFetchTime = when (railType) {
            RailType.SRT -> srtLastFetchTime
            RailType.KTX -> ktxLastFetchTime
        }
        return cachedKey != null && (currentTime - lastFetchTime) < cacheTtlSeconds * 1000L
    }

    private fun getCachedKey(railType: RailType): String? = when (railType) {
        RailType.SRT -> srtCachedKey
        RailType.KTX -> ktxCachedKey
    }

    private fun setCachedKey(railType: RailType, key: String, time: Long) {
        when (railType) {
            RailType.SRT -> { srtCachedKey = key; srtLastFetchTime = time }
            RailType.KTX -> { ktxCachedKey = key; ktxLastFetchTime = time }
        }
    }

    private fun throwNetFunnelError(railType: RailType, message: String): Nothing = when (railType) {
        RailType.SRT -> throw SrtNetFunnelException(message)
        RailType.KTX -> throw KtxNetFunnelException(message)
    }
}

data class NetFunnelResult(
    val status: String,
    val key: String,
    val nwait: String,
    val ip: String? = null
)
