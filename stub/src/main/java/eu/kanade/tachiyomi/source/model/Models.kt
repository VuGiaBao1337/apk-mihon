package eu.kanade.tachiyomi.source.model

class SManga private constructor() {
    var url: String = ""
    var title: String = ""
    var artist: String? = null
    var author: String? = null
    var description: String? = null
    var genre: String? = null
    var status: Int = 0
    var thumbnail_url: String? = null
    var initialized: Boolean = false

    fun setUrlWithoutDomain(url: String) { this.url = url }

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6

        fun create(): SManga = SManga()
    }
}

class SChapter private constructor() {
    var url: String = ""
    var name: String = ""
    var date_upload: Long = 0L
    var chapter_number: Float = -1f
    var scanlator: String? = null

    fun setUrlWithoutDomain(url: String) { this.url = url }

    companion object {
        fun create(): SChapter = SChapter()
    }
}

class Page(
    val index: Int,
    val url: String = "",
    val imageUrl: String? = null,
)

class MangasPage(val mangas: List<SManga>, val hasNextPage: Boolean)

class FilterList(vararg val filters: Any)
