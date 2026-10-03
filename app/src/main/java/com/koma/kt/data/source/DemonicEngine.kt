package com.koma.kt.data.source

import com.koma.kt.data.network.AppHttp
import com.koma.kt.domain.CatalogSource
import com.koma.kt.domain.Chapter
import com.koma.kt.domain.ComicPage
import com.koma.kt.domain.FilterList
import com.koma.kt.domain.HomeFeed
import com.koma.kt.domain.PagedResult
import com.koma.kt.domain.SourceFetchException
import com.koma.kt.domain.SourceManifest
import com.koma.kt.domain.TitleDetails
import com.koma.kt.domain.TitleSummary
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder
import com.koma.kt.util.urlEncode

/** Demonic Scans (demonicscans.org) HTML catalogue. */
class DemonicEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
) : CatalogSource {
    override suspend fun getHome(): HomeFeed {
        val latest = getLatest()
        val popular = getPopular()
        return HomeFeed(spotlight = popular.items.take(12), latest = latest.items)
    }

    override suspend fun getLatest(page: Int): PagedResult<TitleSummary> {
        val response = http.get(manifest.baseUri.resolve("/lastupdates.php"), source = manifest)
        return parseMangaLinks(response.body, page)
    }

    override suspend fun getPopular(page: Int): PagedResult<TitleSummary> {
        val response = http.get(manifest.baseUri.resolve("/newmangalist.php"), source = manifest)
        return parseMangaLinks(response.body, page)
    }

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> {
        if (query.isBlank()) return getPopular(page)
        val uri = URI(
            "${manifest.baseUrl.trimEnd('/')}/advanced.php?manga_name=${urlEncode(query)}",
        )
        val response = http.get(uri, source = manifest)
        return parseMangaLinks(response.body, page)
    }

    override suspend fun getTitle(id: String): TitleDetails {
        val response = http.get(abs(id), source = manifest)
        val doc = Jsoup.parse(response.body, manifest.baseUrl)
        val title = doc.selectFirst("h1, .big-title, title")?.text()
            ?.substringBefore("|")?.trim().orEmpty()
        val cover = doc.selectFirst("img.manga-thumb, .manga-info img, meta[property=og:image]")
            ?.let { it.attr("abs:src").ifBlank { it.attr("content") } }
        val description = doc.selectFirst(".manga-description, #description, .desc")?.text()?.trim()
        val genres = doc.select("a[href*=genre], .genres a").map { it.text().trim() }.filter { it.isNotEmpty() }
        return TitleDetails(
            id = id,
            title = title.ifEmpty { prettyTitle(id) },
            coverUrl = cover,
            description = description,
            genres = genres,
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val response = http.get(abs(titleId), source = manifest)
        val doc = Jsoup.parse(response.body, manifest.baseUrl)
        return doc.select("a[href*=chaptered.php]").mapNotNull { a ->
            val href = a.attr("abs:href").ifBlank { a.attr("href") }
            if (!href.contains("chaptered.php")) return@mapNotNull null
            val uri = runCatching { URI(href) }.getOrNull() ?: return@mapNotNull null
            val query = uri.query.orEmpty()
            val number = query.split('&')
                .firstOrNull { it.startsWith("chapter=") }
                ?.substringAfter('=')
            val name = a.text().replace(Regex("\\s+"), " ").trim()
                .ifEmpty { "Chapter ${number ?: "?"}" }
            Chapter(
                id = href,
                title = name,
                url = href,
                number = number,
            )
        }.distinctBy { it.id }
    }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val response = http.get(abs(chapter.url.ifBlank { chapter.id }), source = manifest)
        val doc = Jsoup.parse(response.body, manifest.baseUrl)
        val pages = doc.select("img").mapNotNull { img ->
            val url = img.attr("abs:src").ifBlank { img.attr("src") }
            if (!url.startsWith("http")) return@mapNotNull null
            if (url.contains("cdn-icons") || url.contains("logo") || url.contains("flaticon")) return@mapNotNull null
            if (!url.contains("demonic") && !url.contains("/cdn") &&
                !url.contains(".jpg") && !url.contains(".png") && !url.contains(".webp")
            ) {
                return@mapNotNull null
            }
            url
        }.distinct().mapIndexed { index, url -> ComicPage(index = index, imageUrl = url) }
        if (pages.isEmpty()) throw SourceFetchException("DemonicScans: пустые страницы")
        return pages
    }

    private fun parseMangaLinks(html: String, page: Int): PagedResult<TitleSummary> {
        val doc = Jsoup.parse(html, manifest.baseUrl)
        val items = mutableListOf<TitleSummary>()
        val seen = mutableSetOf<String>()
        for (a in doc.select("a[href*=/manga/]")) {
            val href = a.attr("abs:href")
            val path = runCatching { URI(href).path }.getOrNull().orEmpty()
            if (path.isEmpty() || !seen.add(path)) continue
            val title = a.text().trim().ifEmpty { prettyTitle(path) }
            if (title.isEmpty()) continue
            val cover = a.selectFirst("img")?.attr("abs:src")
                ?: a.parent()?.selectFirst("img")?.attr("abs:src")
            items += TitleSummary(id = path, title = title, coverUrl = cover)
        }
        return PagedResult(items = items, page = page, hasNext = false)
    }

    private fun prettyTitle(id: String): String {
        val slug = id.substringAfterLast('/').substringBefore('?')
        return URLDecoder.decode(slug, "UTF-8")
            .replace("%27", "'")
            .replace('-', ' ')
            .trim()
    }

    private fun abs(pathOrUrl: String): URI {
        val parsed = runCatching { URI(pathOrUrl) }.getOrNull()
        return when {
            parsed?.scheme != null -> parsed
            pathOrUrl.startsWith("?") -> manifest.baseUri.resolve("/chaptered.php$pathOrUrl")
            else -> manifest.baseUri.resolve(pathOrUrl.trimStart('/'))
        }
    }
}
