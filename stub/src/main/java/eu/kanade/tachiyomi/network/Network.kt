package eu.kanade.tachiyomi.network

import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

fun GET(url: String, headers: Headers = Headers.headersOf()): Request =
    Request.Builder().url(url).headers(headers).build()

fun GET(url: HttpUrl, headers: Headers = Headers.headersOf()): Request =
    Request.Builder().url(url).headers(headers).build()

class NetworkHelper {
    val client: OkHttpClient = OkHttpClient()
}
