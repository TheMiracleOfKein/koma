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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.net.URI
import com.koma.kt.util.urlEncode

/**
 * HiveToons / HiveToon family — JSON query API + HTML chapter pages.
 */
class HiveToonsEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : CatalogSource {
    private val apiBase get() = (manifest.configString("apiBase") ?: "https://api.hivetoons.org").trimEnd('/')
    private val siteBase get() = manifest.baseUrl.trimEnd('/')

    override suspend fun getHome(): HomeFeed {
        val popular = getPopular()
        val latest = getLatest()
        return HomeFeed(spotlight = popular.items.take(12), latest = latest.items)
    }

    override suspend fun getPopular(page: Int) = query(page, "total_views")
    override suspend fun getLatest(page: Int) = query(page, "updated_at")

    private suspend fun query(page: Int, orderBy: String, search: String = ""): PagedResult<TitleSummary> {
        val q = buildString {
            append("page=$page&perPage=24&orderBy=$orderBy")
            if (search.isNotBlank()) append("&searchTerm=${urlEncode(search)}")
        }
        val response = http.get(
            URI("$apiBase/api/query?$q"),
            source = manifest,
            headers = apiHeaders(),
            checkCloudflare = false,
        )
        val root = json.parseToJsonElement(response.body).jsonObject
        val posts = root["posts"]?.jsonArray ?: JsonArray(emptyList())
        val items = posts.mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            val slug = item["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            TitleSummary(
                id = "/series/$slug",
                title = item["postTitle"]?.jsonPrimitive?.contentOrNull
                    ?: item["title"]?.jsonPrimitive?.contentOrNull
                    ?: slug,
                coverUrl = item["featuredImage"]?.jsonPrimitive?.contentOrNull
                    ?: item["cover"]?.jsonPrimitive?.contentOrNull,
                latestChapter = item["chapterCount"]?.jsonPrimitive?.contentOrNull
                    ?: item["chaptersCount"]?.jsonPrimitive?.contentOrNull,
            )
        }
        return PagedResult(items = items, page = page, hasNext = items.size >= 24)
    }

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> {
        if (query.isBlank()) return getPopular(page)
        return query(page, "total_views", search = query)
    }

    override suspend fun getTitle(id: String): TitleDetails {
        val response = http.get(abs(id), source = manifest)
        val doc = Jsoup.parse(response.body, siteBase)
        val title = doc.selectFirst("h1, h2")?.text()?.trim().orEmpty()
        val cover = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("img")?.attr("abs:src")
        val description = doc.selectFirst("meta[name=description], .description, p")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        }?.trim()
        return TitleDetails(
            id = id,
            title = title.ifEmpty { id.substringAfterLast('/') },
            coverUrl = cover,
            description = description,
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val response = http.get(abs(titleId), source = manifest)
        val doc = Jsoup.parse(response.body, siteBase)
        val slug = titleId.trim('/').substringAfterLast('/')
        return doc.select("a[href*=/series/$slug/chapter]").mapNotNull { a ->
            val href = a.attr("abs:href")
            val path = runCatching { URI(href).path }.getOrNull().orEmpty()
            if (!path.contains("/chapter")) return@mapNotNull null
            val name = a.text().replace(Regex("\\s+"), " ").trim().ifEmpty { path.substringAfterLast('/') }
            Chapter(
                id = path,
                title = name,
                url = path,
                number = Regex("""chapter-([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
                    .find(path)?.groupValues?.getOrNull(1),
            )
        }.distinctBy { it.id }
    }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val response = http.get(abs(chapter.url.ifBlank { chapter.id }), source = manifest)
        val doc = Jsoup.parse(response.body, siteBase)
        val pages = doc.select("img").mapNotNull { img ->
            val url = img.attr("abs:src").ifBlank { img.attr("data-src") }
            if (!url.startsWith("http")) return@mapNotNull null
            val lower = url.lowercase()
            if ("logo" in lower || "icon" in lower || "avatar" in lower || "featured" in lower) {
                return@mapNotNull null
            }
            if ("/upload/" !in url && "storage." !in lower) return@mapNotNull null
            url
        }.distinct().mapIndexed { index, url -> ComicPage(index = index, imageUrl = url) }
        if (pages.isEmpty()) throw SourceFetchException("HiveToons: пустые страницы")
        return pages
    }

    private fun abs(pathOrUrl: String): URI {
        val parsed = URI(pathOrUrl)
        return if (parsed.scheme != null) parsed else URI("$siteBase/${pathOrUrl.trimStart('/')}")
    }

    private fun apiHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Origin" to siteBase,
        "Referer" to "$siteBase/",
    )
}
