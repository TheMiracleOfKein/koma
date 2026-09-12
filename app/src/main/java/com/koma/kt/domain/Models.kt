package com.koma.kt.domain

data class TitleSummary(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val typeLabel: String? = null,
    val latestChapter: String? = null,
    val updatedLabel: String? = null,
    val subtitle: String? = null,
)

data class TitleDetails(
    val id: String,
    val title: String,
    val altTitle: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val typeLabel: String? = null,
    val status: String? = null,
    val authors: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
)

data class Chapter(
    val id: String,
    val title: String,
    val url: String,
    val number: String? = null,
    val volume: String? = null,
    val dateLabel: String? = null,
    val branchId: String? = null,
    val branchName: String? = null,
)

data class ComicPage(
    val index: Int,
    val imageUrl: String,
)

data class PagedResult<T>(
    val items: List<T>,
    val page: Int,
    val hasNext: Boolean = false,
)

data class HomeFeed(
    val spotlight: List<TitleSummary> = emptyList(),
    val latest: List<TitleSummary> = emptyList(),
)

/** Order matches Flutter: rtl=0, webtoon=1, ltr=2 */
enum class ReaderMode(val prefsIndex: Int, val label: String) {
    Rtl(0, "RTL"),
    Webtoon(1, "Webtoon"),
    Ltr(2, "LTR");

    companion object {
        fun fromPrefs(index: Int): ReaderMode =
            entries.firstOrNull { it.prefsIndex == index } ?: Webtoon
    }
}

enum class ReaderTheme(val label: String) {
    Light("Светлая"),
    Dark("Темная"),
    System("Системная"),
}
