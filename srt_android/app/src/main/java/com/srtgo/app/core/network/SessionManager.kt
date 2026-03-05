package com.srtgo.app.core.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UploadDataProviders
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpCookie
import java.net.URI
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context
) {

    private val engine: CronetEngine = CronetEngine.Builder(context)
        .enableHttp2(true)
        .enableBrotli(true)
        .build()

    private val executor = Executors.newFixedThreadPool(4)

    private val srtCookies = ConcurrentHashMap<String, MutableList<HttpCookie>>()
    private val ktxCookies = ConcurrentHashMap<String, MutableList<HttpCookie>>()

    // ── SRT ──

    suspend fun srtPostForm(url: String, params: Map<String, String>): JSONObject {
        return JSONObject(srtPostFormRaw(url, params))
    }

    suspend fun srtPostFormRaw(url: String, params: Map<String, String>): String {
        return postForm(url, params, UserAgents.SRT_DEFAULT_HEADERS, srtCookies)
    }

    // ── KTX ──

    suspend fun ktxPostForm(url: String, params: Map<String, String>): JSONObject {
        return JSONObject(postForm(url, params, UserAgents.KTX_DEFAULT_HEADERS, ktxCookies))
    }

    suspend fun ktxGet(url: String, params: Map<String, String> = emptyMap()): JSONObject {
        return JSONObject(get(url, params, UserAgents.KTX_DEFAULT_HEADERS, ktxCookies))
    }

    // ── Cookie management ──

    fun clearSrtCookies() { srtCookies.clear() }
    fun clearKtxCookies() { ktxCookies.clear() }
    fun clearAll() { srtCookies.clear(); ktxCookies.clear() }

    // ── Cronet engine access for NetFunnelHelper ──

    fun getEngine(): CronetEngine = engine
    fun getExecutor() = executor

    // ── Internal HTTP methods ──

    private suspend fun postForm(
        url: String,
        params: Map<String, String>,
        headers: Map<String, String>,
        cookieStore: ConcurrentHashMap<String, MutableList<HttpCookie>>
    ): String = suspendCancellableCoroutine { cont ->
        val body = buildFormBody(params).toByteArray(Charsets.UTF_8)

        val reqBuilder = engine.newUrlRequestBuilder(url, createCallback(cont, url, cookieStore), executor)
            .setHttpMethod("POST")
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .setUploadDataProvider(UploadDataProviders.create(body), executor)

        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
        addCookieHeader(reqBuilder, url, cookieStore)

        val request = reqBuilder.build()
        cont.invokeOnCancellation { request.cancel() }
        request.start()
    }

    private suspend fun get(
        url: String,
        params: Map<String, String>,
        headers: Map<String, String>,
        cookieStore: ConcurrentHashMap<String, MutableList<HttpCookie>>
    ): String = suspendCancellableCoroutine { cont ->
        val fullUrl = if (params.isEmpty()) url else {
            "$url?${buildFormBody(params)}"
        }

        val reqBuilder = engine.newUrlRequestBuilder(fullUrl, createCallback(cont, fullUrl, cookieStore), executor)
            .setHttpMethod("GET")

        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
        addCookieHeader(reqBuilder, fullUrl, cookieStore)

        val request = reqBuilder.build()
        cont.invokeOnCancellation { request.cancel() }
        request.start()
    }

    // ── Helpers ──

    private fun buildFormBody(params: Map<String, String>): String {
        return params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
    }

    private fun createCallback(
        cont: kotlinx.coroutines.CancellableContinuation<String>,
        url: String,
        cookieStore: ConcurrentHashMap<String, MutableList<HttpCookie>>
    ): UrlRequest.Callback {
        return object : UrlRequest.Callback() {
            private val responseBody = ByteArrayOutputStream()

            override fun onRedirectReceived(
                request: UrlRequest, info: UrlResponseInfo, newUrl: String
            ) {
                saveCookiesFromResponse(info, url, cookieStore)
                request.followRedirect()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                saveCookiesFromResponse(info, url, cookieStore)
                request.read(ByteBuffer.allocateDirect(65536))
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
    }

    private fun saveCookiesFromResponse(
        info: UrlResponseInfo,
        url: String,
        cookieStore: ConcurrentHashMap<String, MutableList<HttpCookie>>
    ) {
        val host = try { URI(url).host } catch (_: Exception) { return }
        val headers = info.allHeaders ?: return

        val setCookieValues = headers.entries
            .filter { it.key.equals("set-cookie", ignoreCase = true) }
            .flatMap { it.value }

        if (setCookieValues.isEmpty()) return

        val existing = cookieStore.getOrPut(host) { mutableListOf() }
        synchronized(existing) {
            for (header in setCookieValues) {
                try {
                    val parsed = HttpCookie.parse(header)
                    for (cookie in parsed) {
                        existing.removeAll { it.name == cookie.name }
                        if (!cookie.hasExpired()) {
                            existing.add(cookie)
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private fun addCookieHeader(
        reqBuilder: UrlRequest.Builder,
        url: String,
        cookieStore: ConcurrentHashMap<String, MutableList<HttpCookie>>
    ) {
        val host = try { URI(url).host } catch (_: Exception) { return }
        val cookies = cookieStore[host] ?: return
        val valid = synchronized(cookies) {
            cookies.filter { !it.hasExpired() }
        }
        if (valid.isNotEmpty()) {
            reqBuilder.addHeader("Cookie", valid.joinToString("; ") { "${it.name}=${it.value}" })
        }
    }
}
