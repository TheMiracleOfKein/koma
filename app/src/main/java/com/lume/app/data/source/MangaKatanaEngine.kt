package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import com.lume.app.domain.FilterList
import com.lume.app.domain.HomeFeed
import com.lume.app.domain.PagedResult
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import org.jsoup.Jsoup
import java.net.URI
import com.lume.app.util.urlEncode

/** MangaKatana HTML catalogue (list + thzq page array). */
class MangaKatanaEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
) : CatalogSource {
    override suspend fun getHome(): HomeFeed {
        val latest = getLatest()
        val popular = getPopular()
        return HomeFeed(spotlight = popular.items.take(12), latest = latest.items)
    }

    override suspend fun getLatest(page: Int): PagedResult<TitleSummary> {
        val path = if (page <= 1) "/latest" else "/latest/page/$page"
        val response = http.get(manifest.baseUri.resolve(path), source = manifest)
        return parseList(response.body, page)
    }

    override suspend fun getPopular(page: Int): PagedResult<TitleSummary> {
        val path = if (page <= 1) "/manga" else "/manga/page/$page"
        val response = http.get(manifest.baseUri.resolve(path), source = manifest)
        return parseList(response.body, page)
    }

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> {
        if (query.isBlank()) return getPopular(page)
        val q = urlEncode(query)
        val uri = if (page <= 1) {
            URI("${manifest.baseUrl.trimEnd('/')}/?search=$q")
        } else {
            URI("${manifest.baseUrl.trimEnd('/')}/page/$page?search=$q")
        }
        val response = http.get(uri, source = manifest)
        return parseList(response.body, page)
    }

    override suspend fun getTitle(id: String): TitleDetails {
        val response = http.get(abs(id), source = manifest)
        val doc = Jsoup.parse(response.body, manifest.baseUrl)
        val title = doc.selectFirst("h1, .heading, .single-title")?.text()?.trim().orEmpty()
        val cover = doc.selectFirst(".cover img, .wrap_img img, meta[property=og:image]")
            ?.let { it.attr("abs:src").ifBlank { it.attr("content") } }
        val description = doc.selectFirst("#info, .summary, .desc, .description")?.text()?.trim()
        val genres = doc.select(".genres a, a[href*=/genre/]").map { it.text().trim() }.filter { it.isNotEmpty() }
        val status = doc.selectFirst(".status, .d-status")?.text()?.trim()
        return TitleDetails(
            id = id,
            title = title.ifEmpty { id },
            coverUrl = cover?.takeIf { it.isNotBlank() },
            description = description,
            genres = genres,
            status = status,
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val response = http.get(abs(titleId), source = manifest)
        val doc = Jsoup.parse(response.body, manifest.baseUrl)
        val chapters = mutableListOf<Chapter>()
        val seen = mutableSetOf<String>()
        for (a in doc.select("a[href*=/manga/]")) {
            val href = a.attr("abs:href")
            if (!href.contains("/c") || href.endsWith("/fc")) continue
            val path = URI(href).path.orEmpty()
            if (!seen.add(path)) continue
            val name = a.text().replace(Regex("\\s+"), " ").trim().ifEmpty { path.substringAfterLast('/') }
            chapters += Chapter(
                id = path,
                title = name,
                url = href,
                number = Regex("""c([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
                    .find(path)?.groupValues?.getOrNull(1),
            )
        }
        return chapters
    }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val response = http.get(abs(chapter.url.ifBlank { chapter.id }), source = manifest)
        val thzq = Regex("""var\s+thzq\s*=\s*(\[[\s\S]*?]);""").find(response.body)?.groupValues?.getOrNull(1)
            ?: throw SourceFetchException("MangaKatana: нет thzq")
        val urls = Regex("""'(https?://[^']+)'""").findAll(thzq).map { it.groupValues[1] }.toList()
        if (urls.isEmpty()) throw SourceFetchException("MangaKatana: пустые страницы")
        return urls.mapIndexed { index, url -> ComicPage(index = index, imageUrl = url) }
    }

    private fun parseList(html: String, page: Int): PagedResult<TitleSummary> {
        val doc = Jsoup.parse(html, manifest.baseUrl)
        val items = mutableListOf<TitleSummary>()
        val seen = mutableSetOf<String>()
        val nodes = doc.select("#book_list .item, .book_list .item, .item[data-id]")
        val scope = nodes.ifEmpty { doc.select("a[href*=/manga/]") }
        for (node in scope) {
            val a = if (node.tagName() == "a") node else node.selectFirst("a[href*=/manga/]") ?: continue
            val href = a.attr("abs:href")
            if (href.contains("/c") || href.endsWith("/fc")) continue
            val path = runCatching { URI(href).path }.getOrNull().orEmpty()
            if (path.isEmpty() || !seen.add(path)) continue
            val title = a.attr("title").ifBlank {
                node.selectFirst(".title, h3, h4, a")?.text().orEmpty()
            }.ifBlank { a.text() }.trim()
            if (title.isEmpty()) continue
            val img = node.selectFirst("img")
            val cover = img?.attr("abs:src")?.ifBlank { img.attr("data-src") }
                ?: img?.parent()?.selectFirst("source")?.attr("srcset")?.split(" ")?.firstOrNull()
            items += TitleSummary(
                id = path,
                title = title,
                coverUrl = cover?.takeIf { it.isNotBlank() },
                latestChapter = node.selectFirst(".chapter, .update_time, .text")?.text()?.trim(),
            )
        }
        return PagedResult(items = items, page = page, hasNext = items.size >= 20)
    }

    private fun abs(pathOrUrl: String): URI {
        val parsed = URI(pathOrUrl)
        return if (parsed.scheme != null) parsed else manifest.baseUri.resolve(pathOrUrl.trimStart('/'))
    }
}
