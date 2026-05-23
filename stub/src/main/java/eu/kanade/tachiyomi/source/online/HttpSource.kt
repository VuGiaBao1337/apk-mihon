package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

abstract class HttpSource {
    abstract val name: String
    abstract val baseUrl: String
    abstract val lang: String
    abstract val supportsLatest: Boolean

    val network: NetworkHelper = NetworkHelper()

    open val client: OkHttpClient get() = network.client

    open val headers: Headers get() = headersBuilder().build()

    open fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0")

    abstract fun popularMangaRequest(page: Int): Request
    abstract fun popularMangaParse(response: Response): MangasPage

    abstract fun latestUpdatesRequest(page: Int): Request
    abstract fun latestUpdatesParse(response: Response): MangasPage

    abstract fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request
    abstract fun searchMangaParse(response: Response): MangasPage

    abstract fun mangaDetailsParse(response: Response): SManga

    open fun chapterListRequest(manga: SManga): Request =
        throw UnsupportedOperationException()
    abstract fun chapterListParse(response: Response): List<SChapter>

    abstract fun pageListParse(response: Response): List<Page>
    abstract fun imageUrlParse(response: Response): String

    open fun getFilterList(): FilterList = FilterList()

    open fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        Observable.fromCallable {
            client.newCall(chapterListRequest(manga)).execute().use { chapterListParse(it) }
        }
}
