package eu.kanade.tachiyomi.extension.vi.moetruyen

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

class MoeTruyen : HttpSource() {

    override val name = "MoeTruyen"
    override val baseUrl = "https://moetruyen.net"
    override val lang = "vi"
    override val supportsLatest = true

    // IMGX Decryptor cache
    private val imgxGrants = ConcurrentHashMap<String, ImgxPageEntry>()

    data class ImgxGrant(
        val version: Int,
        val algorithm: String,
        val imageId: String,
        val issuedAt: Long,
        val expiresAt: Long,
        val nonce: String,
        val keyNonce: String,
        val keyHash: String,
        val signature: String,
        val wrappedDecodeKey: String?,
        val decodeKey: String?,
    )

    data class ImgxPageEntry(
        val pageIndex: Int,
        val downloadUrl: String,
        val storageKey: String,
        val grant: ImgxGrant,
    )

    override val client: OkHttpClient by lazy {
        network.client.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                val urlString = request.url.toString()

                val entry = imgxGrants[urlString]
                if (entry != null) {
                    val scrambledBytes = response.body?.bytes()
                        ?: return@addInterceptor response
                    try {
                        val decodeKey = unwrapDecodeKeyFromGrant(entry.grant, entry.storageKey)
                        val decryptedBytes = decodeImgxWithKey(scrambledBytes, decodeKey)
                        val mediaType = "image/webp".toMediaTypeOrNull()
                        val body = decryptedBytes.toResponseBody(mediaType)
                        response.newBuilder().body(body).build()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        response
                    }
                } else {
                    response
                }
            }
            .build()
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Cookie", "moetruyen_full_web=Moetruyen123456")
        .add(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        )

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/manga?sort=views&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = Jsoup.parse(response.body!!.string(), baseUrl)
        val mangas = document.select("article.manga-card").map(::mangaFromElement)
        val hasNext = document.selectFirst("nav a[aria-label='Trang sau']:not(.is-disabled)") != null
        return MangasPage(mangas, hasNext)
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/manga?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = Jsoup.parse(response.body!!.string(), baseUrl)
        val mangas = document.select("article.manga-card").map(::mangaFromElement)
        val hasNext = document.selectFirst("nav a[aria-label='Trang sau']:not(.is-disabled)") != null
        return MangasPage(mangas, hasNext)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a[href*=/manga/]")!!
        setUrlWithoutDomain(link.absUrl("href"))

        val titleEl = element.selectFirst("h3")
        title = titleEl?.attr("title")?.ifEmpty { null } ?: titleEl?.text() ?: ""

        val imgEl = element.selectFirst("img")
        thumbnail_url = imgEl?.absUrl("data-src")?.ifEmpty { null }
            ?: imgEl?.absUrl("src")
    }

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply { if (query.isNotBlank()) addQueryParameter("q", query) }
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = Jsoup.parse(response.body!!.string(), baseUrl)
        return SManga.create().apply {
            title = document.selectFirst("h1.manga-detail-title")?.text() ?: ""
            author = document.select("p.manga-detail-meta-line")
                .firstOrNull { it.selectFirst(".manga-detail-meta-label")?.text()?.contains("Tác giả") == true }
                ?.select("a.inline-link")
                ?.joinToString { it.text() }
                ?.ifEmpty { null }
            genre = document.select(".manga-detail-genre-chips a.chip")
                .joinToString { it.text() }
                .ifEmpty { null }
            description = document.selectFirst("[data-description-content]")?.text()
                ?: document.selectFirst(".manga-description__text")?.text()
            status = when (document.selectFirst(".manga-status-pill")?.text()?.trim()) {
                "Còn tiếp" -> SManga.ONGOING
                "Hoàn thành" -> SManga.COMPLETED
                "Tạm dừng" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            thumbnail_url = document.selectFirst(".detail-cover img")?.absUrl("src")
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request =
        GET("$baseUrl${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val visitedPages = mutableSetOf<String>()
        var currentUrl = response.request.url.toString()
        var document = Jsoup.parse(response.body!!.string(), baseUrl)

        while (visitedPages.add(currentUrl)) {
            chapters += parseChapterList(document)

            val nextLink = document.selectFirst(
                "nav[aria-label*='Phân trang chương'] a[aria-label='Trang chương sau']:not(.is-disabled)",
            )
            val nextUrl = nextLink?.absUrl("href")?.takeIf { it.isNotEmpty() && it != "#" }
                ?: break

            if (visitedPages.contains(nextUrl)) break
            currentUrl = nextUrl
            client.newCall(GET(currentUrl, headers)).execute().use {
                document = Jsoup.parse(it.body!!.string(), baseUrl)
            }
        }

        return chapters
    }

    private fun parseChapterList(document: Document): List<SChapter> =
        document.select("ul.chapter-list li.chapter a.chapter-link").map { el ->
            SChapter.create().apply {
                setUrlWithoutDomain(el.absUrl("href"))
                name = el.selectFirst(".chapter-num")?.text() ?: ""
                val timeEl = el.selectFirst(".chapter-time")
                date_upload = parseRelativeDate(timeEl?.text())
            }
        }

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        val calendar = Calendar.getInstance()
        val number = Regex("""\d+""").find(dateStr)?.value?.toIntOrNull() ?: return 0L
        when {
            dateStr.contains("giây") -> calendar.add(Calendar.SECOND, -number)
            dateStr.contains("phút") -> calendar.add(Calendar.MINUTE, -number)
            dateStr.contains("giờ") -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            dateStr.contains("ngày") -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            dateStr.contains("tuần") -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            dateStr.contains("tháng") -> calendar.add(Calendar.MONTH, -number)
            dateStr.contains("năm") -> calendar.add(Calendar.YEAR, -number)
            else -> {
                // Try absolute date dd/MM/yyyy
                return try {
                    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
                        .apply { timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh") }
                        .parse(dateStr.trim())?.time ?: 0L
                } catch (e: Exception) {
                    0L
                }
            }
        }
        return calendar.timeInMillis
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = Jsoup.parse(response.body!!.string(), baseUrl)
        val pages = mutableListOf<Page>()
        val readerPages = document.selectFirst(".reader-pages")
        val initialPagesJson = readerPages?.attr("data-reader-imgx-initial-pages") ?: ""

        if (initialPagesJson.isNotEmpty()) {
            // IMGX encrypted images
            val totalPages = readerPages?.attr("data-reader-total-pages")?.toIntOrNull() ?: 0
            val grants = mutableMapOf<Int, ImgxPageEntry>()

            runCatching {
                val decodedJson = URLDecoder.decode(initialPagesJson, "UTF-8")
                val jsonArray = JSONArray(decodedJson)
                for (i in 0 until jsonArray.length()) {
                    val entry = parseImgxPageEntry(jsonArray.getJSONObject(i))
                    grants[entry.pageIndex] = entry
                }
            }

            // Fetch remaining pages
            val remaining = (0 until totalPages).filter { !grants.containsKey(it) }
            val accessUrl = readerPages?.attr("abs:data-reader-imgx-access-url") ?: ""

            if (accessUrl.isNotEmpty() && remaining.isNotEmpty()) {
                for (chunk in remaining.chunked(5)) {
                    runCatching {
                        val body = JSONObject().apply {
                            put("pageIndexes", JSONArray(chunk))
                        }.toString().toRequestBody("application/json".toMediaTypeOrNull())

                        client.newCall(
                            Request.Builder().url(accessUrl).post(body).headers(headers).build(),
                        ).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val arr = JSONObject(resp.body!!.string()).getJSONArray("pages")
                                for (i in 0 until arr.length()) {
                                    val entry = parseImgxPageEntry(arr.getJSONObject(i))
                                    grants[entry.pageIndex] = entry
                                }
                            }
                        }
                    }
                }
            }

            for (i in 0 until totalPages) {
                grants[i]?.let { entry ->
                    imgxGrants[entry.downloadUrl] = entry
                    pages.add(Page(i, imageUrl = entry.downloadUrl))
                }
            }
        } else {
            // Plain images — no encryption
            document.select("img.page-media")
                .filterNot { el -> el.parents().any { it.tagName().equals("noscript", ignoreCase = true) } }
                .mapNotNull { el -> el.absUrl("data-src").ifEmpty { el.absUrl("src") }.ifEmpty { null } }
                .distinct()
                .forEachIndexed { i, url -> pages.add(Page(i, imageUrl = url)) }
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    private fun parseImgxPageEntry(item: JSONObject): ImgxPageEntry {
        val grantObj = item.getJSONObject("grant")
        val grant = ImgxGrant(
            version = grantObj.getInt("version"),
            algorithm = grantObj.getString("algorithm"),
            imageId = grantObj.getString("imageId"),
            issuedAt = grantObj.getLong("issuedAt"),
            expiresAt = grantObj.getLong("expiresAt"),
            nonce = grantObj.getString("nonce"),
            keyNonce = grantObj.getString("keyNonce"),
            keyHash = grantObj.getString("keyHash"),
            signature = grantObj.getString("signature"),
            wrappedDecodeKey = grantObj.optString("wrappedDecodeKey").ifEmpty { null },
            decodeKey = grantObj.optString("decodeKey").ifEmpty { null },
        )
        return ImgxPageEntry(
            pageIndex = item.getInt("pageIndex"),
            downloadUrl = item.getString("downloadUrl"),
            storageKey = item.getString("storageKey"),
            grant = grant,
        )
    }

    // ============================== IMGX Crypto ===========================

    private fun fnv1a32(bytes: ByteArray): Long {
        var hash = 0x811c9dc5L
        for (b in bytes) {
            hash = hash xor (b.toInt() and 0xff).toLong()
            hash = (hash * 0x01000193L) and 0xffffffffL
        }
        return if (hash == 0L) 0x9e3779b9L else hash
    }

    private fun nextXorShift32(value: Long): Long {
        var x = value and 0xffffffffL
        x = x xor ((x shl 13) and 0xffffffffL)
        x = x xor (x ushr 17)
        x = x xor ((x shl 5) and 0xffffffffL)
        return x and 0xffffffffL
    }

    private fun createGrantKeyMask(material: String, byteLength: Int): ByteArray {
        val mask = ByteArray(byteLength)
        var seed = fnv1a32(material.toByteArray(Charsets.UTF_8))
        for (i in 0 until byteLength) {
            if (i % 4 == 0) seed = nextXorShift32((seed + i + 0x9e3779b9L) and 0xffffffffL)
            mask[i] = ((seed ushr ((i % 4) * 8)) and 0xffL).toByte()
        }
        return mask
    }

    private fun createGrantKeyWrapMaterial(grant: ImgxGrant, storageKey: String): String =
        listOf(
            "IMGX-GRANT-WRAP-v1",
            grant.version.toString(),
            grant.algorithm,
            grant.imageId,
            grant.issuedAt.toString(),
            grant.expiresAt.toString(),
            grant.nonce,
            grant.keyNonce,
            grant.signature,
            storageKey.trim().removePrefix("/"),
        ).joinToString(".")

    private fun unwrapDecodeKeyFromGrant(grant: ImgxGrant, storageKey: String): ByteArray {
        if (grant.wrappedDecodeKey != null) {
            val wrapped = base64UrlDecode(grant.wrappedDecodeKey)
            val mask = createGrantKeyMask(createGrantKeyWrapMaterial(grant, storageKey), wrapped.size)
            return ByteArray(wrapped.size) { i -> (wrapped[i].toInt() xor mask[i].toInt()).toByte() }
        }
        if (grant.decodeKey != null) return base64UrlDecode(grant.decodeKey)
        throw Exception("IMGX: Thiếu mã khóa giải mã")
    }

    private fun seedFromKey(key: ByteArray): Long {
        if (key.size < 4) return 0x9e3779b9L
        val seed = ((key[0].toInt() and 0xff).toLong() shl 24) or
            ((key[1].toInt() and 0xff).toLong() shl 16) or
            ((key[2].toInt() and 0xff).toLong() shl 8) or
            (key[3].toInt() and 0xff).toLong()
        return if (seed == 0L) 0x9e3779b9L else seed
    }

    private fun unshuffleBytesInPlace(bytes: ByteArray, key: ByteArray) {
        val swaps = IntArray(bytes.size)
        var seed = seedFromKey(key)
        for (i in bytes.size - 1 downTo 1) {
            seed = nextXorShift32(seed)
            swaps[i] = (seed % (i + 1)).toInt()
        }
        for (i in 1 until bytes.size) {
            if (i != swaps[i]) {
                val tmp = bytes[i]
                bytes[i] = bytes[swaps[i]]
                bytes[swaps[i]] = tmp
            }
        }
    }

    private fun decodeImgxWithKey(binary: ByteArray, decodeKey: ByteArray): ByteArray {
        if (binary.size < 13) throw Exception("IMGX: Dữ liệu ảnh quá ngắn")
        val payload = binary.copyOfRange(13, binary.size)
        unshuffleBytesInPlace(payload, decodeKey)
        for (i in payload.indices) {
            payload[i] = (payload[i].toInt() xor (decodeKey[i % decodeKey.size].toInt() and 0xff)).toByte()
        }
        return payload
    }

    private fun base64UrlDecode(text: String): ByteArray =
        android.util.Base64.decode(
            text,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )

    override fun chapterPageParse(response: Response): SChapter =
        throw UnsupportedOperationException()

    override fun getFilterList() = FilterList()
}

