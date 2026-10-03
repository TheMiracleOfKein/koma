package com.lume.app.data.network

import android.webkit.CookieManager
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.SourceManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, ConcurrentHashMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val map = store.getOrPut(host) { ConcurrentHashMap() }
        cookies.forEach { map[it.name] = it }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val map = store[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        return map.values.filter { it.expiresAt >= now || it.expiresAt == 0L }
            .filter { it.matches(url) }
    }

    fun putAll(host: String, cookies: List<Cookie>) {
        val map = store.getOrPut(host) { ConcurrentHashMap() }
        cookies.forEach { map[it.name] = it }
    }
}

data class HttpResult(
    val code: Int,
    val body: String,
    val finalUrl: String,
)

class AppHttp {
    val cookieJar = MemoryCookieJar()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val okHttp: OkHttpClient get() = client

    private val preferWebViewHosts = ConcurrentHashMap.newKeySet<String>()
    private var userAgent: String = DEFAULT_UA

    private val _challengeRequests = MutableSharedFlow<URI>(extraBufferCapacity = 1)
    val challengeRequests: SharedFlow<URI> = _challengeRequests.asSharedFlow()

    private val _networkEpoch = MutableStateFlow(0)
    val networkEpoch: StateFlow<Int> = _networkEpoch.asStateFlow()

    fun setUserAgent(ua: String) {
        if (ua.isNotBlank()) userAgent = ua
    }

    fun preferWebViewFor(host: String) {
        preferWebViewHosts.add(host)
    }

    fun bumpNetworkEpoch() {
        _networkEpoch.value = _networkEpoch.value + 1
    }

    suspend fun requestChallenge(uri: URI) {
        _challengeRequests.emit(uri)
    }

    fun syncWebViewCookies(url: String) {
        val manager = CookieManager.getInstance()
        val raw = manager.getCookie(url) ?: return
        val httpUrl = url.toHttpUrl()
        val cookies = raw.split(';')
            .mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                Cookie.parse(httpUrl, trimmed)
            }
        cookieJar.putAll(httpUrl.host, cookies)
        preferWebViewFor(httpUrl.host)
        bumpNetworkEpoch()
    }

    suspend fun get(
        uri: URI,
        source: SourceManifest? = null,
        headers: Map<String, String> = emptyMap(),
        checkCloudflare: Boolean = true,
    ): HttpResult = execute("GET", uri, source, headers, null, checkCloudflare)

    suspend fun postForm(
        uri: URI,
        source: SourceManifest? = null,
        headers: Map<String, String> = emptyMap(),
        form: Map<String, String>,
        checkCloudflare: Boolean = true,
    ): HttpResult {
        val body = FormBody.Builder().apply {
            form.forEach { (k, v) -> add(k, v) }
        }.build()
        return execute("POST", uri, source, headers, body, checkCloudflare)
    }

    suspend fun getBytes(
        uri: URI,
        source: SourceManifest? = null,
        headers: Map<String, String> = emptyMap(),
    ): ByteArray = withContext(Dispatchers.IO) {
        val request = buildRequest("GET", uri, source, headers, null)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SourceHttpException(response.code, uri.toString())
            }
            response.body.bytes()
        }
    }

    private suspend fun execute(
        method: String,
        uri: URI,
        source: SourceManifest?,
        headers: Map<String, String>,
        body: okhttp3.RequestBody?,
        checkCloudflare: Boolean,
    ): HttpResult = withContext(Dispatchers.IO) {
        val request = buildRequest(method, uri, source, headers, body)
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            validate(response, text, uri, checkCloudflare)
            HttpResult(response.code, text, response.request.url.toString())
        }
    }

    private fun buildRequest(
        method: String,
        uri: URI,
        source: SourceManifest?,
        headers: Map<String, String>,
        body: okhttp3.RequestBody?,
    ): Request {
        val builder = Request.Builder().url(uri.toString()).method(
            method,
            if (method == "GET" || method == "HEAD") null else body
                ?: "".toRequestBody("application/x-www-form-urlencoded".toMediaType()),
        )
        builder.header("User-Agent", userAgent)
        builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        builder.header("Accept-Language", "en-US,en;q=0.9,ru;q=0.8")
        source?.headers?.forEach { (k, v) -> builder.header(k, v) }
        headers.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }

    private fun validate(response: Response, body: String, uri: URI, checkCloudflare: Boolean) {
        val status = response.code
        if (checkCloudflare && isCloudflare(response, body, status)) {
            throw CloudflareException(uri)
        }
        if (status == 403 || status == 503) {
            throw CloudflareException(uri)
        }
        if (status !in 200..299) {
            throw SourceHttpException(status, uri.toString())
        }
    }

    private fun isCloudflare(response: Response, body: String, status: Int): Boolean {
        val server = response.header("Server").orEmpty().lowercase()
        val lower = body.lowercase()
        if (server.contains("cloudflare") && (status == 403 || status == 503)) return true
        return (status == 403 || status == 503) &&
            (lower.contains("just a moment") ||
                lower.contains("cf-browser-verification") ||
                lower.contains("challenge-platform") ||
                lower.contains("cloudflare"))
    }

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}

class SourceHttpException(val code: Int, val url: String) : Exception("HTTP $code: $url")
