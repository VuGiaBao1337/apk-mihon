package eu.kanade.tachiyomi.network

import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

fun GET(url: String, headers: Headers = Headers.headersOf()): Request =
    Request.Builder().url(url).headers(headers).build()

fun GET(url: HttpUrl, headers: Headers = Headers.headersOf()): Request =
    Request.Builder().url(url).headers(headers).build()

fun okhttp3.Call.asObservableSuccess(): Observable<Response> =
    Observable.fromCallable { execute() }

suspend fun okhttp3.Call.awaitSuccess(): Response = execute()

fun okhttp3.OkHttpClient.newCachelessCallWithProgress(request: Request, page: Any): okhttp3.Call =
    newCall(request)

class NetworkHelper {
    val client: OkHttpClient = OkHttpClient()
    fun defaultUserAgentProvider(): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
}
