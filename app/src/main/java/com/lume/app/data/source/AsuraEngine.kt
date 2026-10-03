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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import com.lume.app.util.urlEncode

/** Asura Scans via api.asurascans.com (asuracomic.net front). */
class AsuraEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : CatalogSource {
    private val apiBase get() = (manifest.configString("apiBase") ?: "https://api.asurascans.com/api").trimEnd('/')
    private val siteBase get() = manifest.baseUrl.trimEnd('/')

    override suspend fun getHome(): HomeFeed {
        val popular = getPopular()
        val latest = getLatest()
        return HomeFeed(spotlight = popular.items.take(12), latest = latest.items)
    }

    override suspend fun getPopular(page: Int) =
        seriesList(page, "total_views")

    override suspend fun getLatest(page: Int) =
        seriesList(page, "last_chapter_at")

    private suspend fun seriesList(page: Int, orderBy: String): PagedResult<TitleSummary> {
        val uri = URI("$apiBase/series?page=$page&order=desc&orderBy=$orderBy")
        val response = http.get(
            uri,
            source = manifest,
            headers = apiHeaders(),
            checkCloudflare = false,
        )
        return parseSeriesList(response.body, page)
    }

    override suspend fun search(query: String, page: Int, filters: FilterList): PagedResult<TitleSummary> {
        if (query.isBlank()) return getPopular(page)
        val uri = URI("$apiBase/series?page=$page&search=${urlEncode(query)}")
        val response = http.get(uri, source = manifest, headers = apiHeaders(), checkCloudflare = false)
        return parseSeriesList(response.body, page)
    }

    override suspend fun getTitle(id: String): TitleDetails {
        val slug = publicSlug(id)
        val response = http.get(
            URI("$apiBase/series/$slug"),
            source = manifest,
            headers = apiHeaders(),
            checkCloudflare = false,
        )
        val root = json.parseToJsonElement(response.body).jsonObject
        val series = root["series"]?.jsonObject ?: root["data"]?.jsonObject ?: root
        val title = series["title"]?.jsonPrimitive?.contentOrNull ?: slug
        val cover = series["cover"]?.jsonPrimitive?.contentOrNull
            ?: series["cover_url"]?.jsonPrimitive?.contentOrNull
        val genres = series["genres"]?.jsonArray.orEmpty().mapNotNull {
            it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                ?: it.jsonPrimitive.contentOrNull
        }
        return TitleDetails(
            id = slug,
            title = title,
            coverUrl = cover,
            description = series["description"]?.jsonPrimitive?.contentOrNull,
            status = series["status"]?.jsonPrimitive?.contentOrNull,
            typeLabel = series["type"]?.jsonPrimitive?.contentOrNull,
            genres = genres,
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val slug = publicSlug(titleId)
        val response = http.get(
            URI("$apiBase/series/$slug/chapters"),
            source = manifest,
            headers = apiHeaders(),
            checkCloudflare = false,
        )
        val root = json.parseToJsonElement(response.body).jsonObject
        val data = root["data"]?.jsonArray ?: JsonArray(emptyList())
        return data.mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            val number = item["number"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val chapterSlug = item["slug"]?.jsonPrimitive?.contentOrNull ?: "chapter-$number"
            val seriesSlug = item["series_slug"]?.jsonPrimitive?.contentOrNull ?: slug.substringBeforeLast("-")
            val path = "/comics/$slug/chapter/$number"
            Chapter(
                id = path,
                title = "Chapter $number",
                url = path,
                number = number,
                dateLabel = item["published_at"]?.jsonPrimitive?.contentOrNull?.take(10),
                branchId = chapterSlug,
                branchName = seriesSlug,
            )
        }
    }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val slug = publicSlug(chapter.id.substringBefore("/chapter"))
            .ifBlank { chapter.branchName.orEmpty() }
            .ifBlank { throw SourceFetchException("Asura: нет slug") }
        val number = chapter.number
            ?: chapter.id.substringAfterLast('/').ifBlank { null }
            ?: throw SourceFetchException("Asura: нет номера главы")
        val response = http.get(
            URI("$apiBase/series/$slug/chapters/$number"),
            source = manifest,
            headers = apiHeaders(),
            checkCloudflare = false,
        )
        val root = json.parseToJsonElement(response.body).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val chapterObj = data["chapter"]?.jsonObject ?: data
        val pages = chapterObj["pages"]?.jsonArray ?: JsonArray(emptyList())
        val result = pages.mapIndexedNotNull { index, el ->
            val url = when (el) {
                is JsonObject -> el["url"]?.jsonPrimitive?.contentOrNull
                else -> el.jsonPrimitive.contentOrNull
            } ?: return@mapIndexedNotNull null
            ComicPage(index = index, imageUrl = url)
        }
        if (result.isEmpty()) throw SourceFetchException("Asura: пустые страницы")
        return result
    }

    private fun parseSeriesList(raw: String, page: Int): PagedResult<TitleSummary> {
        val root = json.parseToJsonElement(raw).jsonObject
        val data = root["data"]?.jsonArray ?: JsonArray(emptyList())
        val items = data.mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            val baseSlug = item["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            // Prefer website public slug when present; API accepts both forms.
            val publicUrl = item["public_url"]?.jsonPrimitive?.contentOrNull
            val slug = publicUrl?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: baseSlug
            TitleSummary(
                id = slug,
                title = item["title"]?.jsonPrimitive?.contentOrNull ?: baseSlug,
                coverUrl = item["cover"]?.jsonPrimitive?.contentOrNull
                    ?: item["cover_url"]?.jsonPrimitive?.contentOrNull,
                latestChapter = item["latest_chapters"]?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("number")?.jsonPrimitive?.contentOrNull
                    ?: item["chapter_count"]?.jsonPrimitive?.contentOrNull,
                typeLabel = item["type"]?.jsonPrimitive?.contentOrNull,
            )
        }
        return PagedResult(items = items, page = page, hasNext = items.size >= 20)
    }

    private fun publicSlug(id: String): String {
        val trimmed = id.trim('/')
        return when {
            trimmed.startsWith("comics/") -> trimmed.removePrefix("comics/").substringBefore('/')
            trimmed.startsWith("series/") -> trimmed.removePrefix("series/").substringBefore('/')
            else -> trimmed.substringBefore('/')
        }
    }

    private fun apiHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Origin" to siteBase,
        "Referer" to "$siteBase/",
    )
}
