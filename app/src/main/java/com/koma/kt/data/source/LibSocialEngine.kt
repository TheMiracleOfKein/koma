package com.koma.kt.data.source

import com.koma.kt.data.network.AppHttp
import com.koma.kt.domain.CatalogSource
import com.koma.kt.domain.Chapter
import com.koma.kt.domain.ComicPage
import com.koma.kt.domain.HomeFeed
import com.koma.kt.domain.PagedResult
import com.koma.kt.domain.SourceFetchException
import com.koma.kt.domain.SourceManifest
import com.koma.kt.domain.TitleDetails
import com.koma.kt.domain.TitleSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder

class LibSocialEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : CatalogSource {
    private val apiBase: URI
        get() = URI(manifest.configString("apiBase") ?: "https://api.mangalib.me/api")

    private val apiHeaders: Map<String, String>
        get() = mapOf(
            "Accept" to "application/json, text/plain, */*",
            "Site-Id" to (manifest.configString("siteId") ?: "1"),
            "Client-Time-Zone" to "Europe/Moscow",
        )

    override suspend fun getHome(): HomeFeed {
        val latest = getLatest()
        val popular = getPopular()
        return HomeFeed(spotlight = popular.items.take(12), latest = latest.items)
    }

    override suspend fun getLatest(page: Int) = mangaList(page, "last_chapter_at")
    override suspend fun getPopular(page: Int) = mangaList(page, "rating_score")

    private suspend fun mangaList(page: Int, sortBy: String): PagedResult<TitleSummary> {
        val siteId = manifest.configString("siteId") ?: "1"
        val uri = URI(
            "${apiBase.toString().trimEnd('/')}/manga?page=$page&site_id[]=$siteId&sort_by=$sortBy",
        )
        val response = http.get(uri, source = manifest, headers = apiHeaders, checkCloudflare = false)
        return parseList(response.body, page)
    }

    override suspend fun search(query: String, page: Int): PagedResult<TitleSummary> {
        val siteId = manifest.configString("siteId") ?: "1"
        val q = URLEncoder.encode(query, "UTF-8")
        val uri = URI("${apiBase.toString().trimEnd('/')}/manga?q=$q&page=$page&site_id[]=$siteId")
        val response = http.get(uri, source = manifest, headers = apiHeaders, checkCloudflare = false)
        return parseList(response.body, page)
    }

    override suspend fun getTitle(id: String): TitleDetails {
        val slug = slug(id)
        val uri = URI("${apiBase.toString().trimEnd('/')}/manga/$slug")
        val response = http.get(uri, source = manifest, headers = apiHeaders, checkCloudflare = false)
        val root = asObject(json.parseToJsonElement(response.body))
        val data = asObject(root["data"] ?: root)
        val coverObj = data["cover"] as? JsonObject
        val cover = coverObj?.get("default")?.jsonPrimitive?.contentOrNull
            ?: coverObj?.get("md")?.jsonPrimitive?.contentOrNull
        val genres = (data["genres"] as? JsonArray).orEmpty().mapNotNull {
            (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
        }
        val authors = (data["authors"] as? JsonArray).orEmpty().mapNotNull {
            (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
        }
        return TitleDetails(
            id = if (id.startsWith("/")) id else "/$slug",
            title = data["rus_name"]?.jsonPrimitive?.contentOrNull
                ?: data["name"]?.jsonPrimitive?.contentOrNull
                ?: slug,
            altTitle = data["name"]?.jsonPrimitive?.contentOrNull,
            coverUrl = cover?.takeIf { it.isNotEmpty() },
            description = data["summary"]?.jsonPrimitive?.contentOrNull
                ?: data["description"]?.jsonPrimitive?.contentOrNull,
            typeLabel = nestedLabel(data["type"]),
            status = nestedLabel(data["status"]),
            authors = authors,
            genres = genres,
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val slug = slug(titleId)
        val uri = URI("${apiBase.toString().trimEnd('/')}/manga/$slug/chapters")
        val response = http.get(uri, source = manifest, headers = apiHeaders, checkCloudflare = false)
        val root = json.parseToJsonElement(response.body)
        val list = when {
            root is JsonArray -> root
            root is JsonObject && root["data"] is JsonArray -> root["data"]!!.jsonArray
            root is JsonObject && root["data"] is JsonObject ->
                (root["data"] as JsonObject)["chapters"]?.jsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        val chapters = list.mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            val volume = item["volume"]?.jsonPrimitive?.contentOrNull
                ?: item["chapter_volume"]?.jsonPrimitive?.contentOrNull
                ?: ""
            val number = item["number"]?.jsonPrimitive?.contentOrNull
                ?: item["chapter_number"]?.jsonPrimitive?.contentOrNull
                ?: ""
            val name = (item["name"]?.jsonPrimitive?.contentOrNull
                ?: item["chapter_name"]?.jsonPrimitive?.contentOrNull
                ?: "").trim()
            val branchId = item["branch_id"]?.jsonPrimitive?.contentOrNull
            val id = "/$slug/v$volume/c$number${if (branchId == null) "" else "?bid=$branchId"}"
            Chapter(
                id = id,
                title = listOfNotNull(
                    volume.takeIf { it.isNotEmpty() && it != "null" }?.let { "Том $it" },
                    number.takeIf { it.isNotEmpty() && it != "null" }?.let { "Глава $it" },
                    name.takeIf { it.isNotEmpty() },
                ).joinToString(" ").trim(),
                url = id,
                number = number.takeIf { it != "null" },
                volume = volume.takeIf { it != "null" },
                branchId = branchId,
            )
        }
        return chapters.asReversed()
    }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val slug = slug(chapter.id)
        val volume = chapter.volume ?: "1"
        val number = chapter.number ?: "1"
        val query = buildString {
            append("number=").append(URLEncoder.encode(number, "UTF-8"))
            append("&volume=").append(URLEncoder.encode(volume, "UTF-8"))
            if (chapter.branchId != null) {
                append("&branch_id=").append(URLEncoder.encode(chapter.branchId, "UTF-8"))
            }
        }
        val uri = URI("${apiBase.toString().trimEnd('/')}/manga/$slug/chapter?$query")
        val response = http.get(uri, source = manifest, headers = apiHeaders, checkCloudflare = false)
        val root = asObject(json.parseToJsonElement(response.body))
        val data = asObject(root["data"] ?: root)
        val pages = mutableListOf<ComicPage>()
        val rawPages = data["pages"] as? JsonArray ?: JsonArray(emptyList())
        for (page in rawPages) {
            var url: String? = when (page) {
                is JsonPrimitive -> page.contentOrNull
                is JsonObject -> page["url"]?.jsonPrimitive?.contentOrNull
                    ?: page["link"]?.jsonPrimitive?.contentOrNull
                    ?: page["src"]?.jsonPrimitive?.contentOrNull
                else -> null
            }
            if (url.isNullOrBlank()) continue
            if (!url.startsWith("http")) {
                val imageServer = data["imageServer"]?.jsonPrimitive?.contentOrNull
                    ?: (data["servers"] as? JsonObject)?.get("main")?.jsonPrimitive?.contentOrNull
                    ?: ""
                if (imageServer.isNotEmpty()) url = "$imageServer$url"
            }
            pages += ComicPage(index = pages.size, imageUrl = url)
        }
        if (pages.isEmpty()) throw SourceFetchException("Не удалось получить страницы главы MangaLib")
        return pages
    }

    private fun parseList(raw: String, page: Int): PagedResult<TitleSummary> {
        val root = asObject(json.parseToJsonElement(raw))
        val data = root["data"]
        val list = when {
            data is JsonArray -> data
            data is JsonObject && data["data"] is JsonArray -> data["data"]!!.jsonArray
            else -> JsonArray(emptyList())
        }
        val items = list.mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            val slug = item["slug_url"]?.jsonPrimitive?.contentOrNull
                ?: item["slug"]?.jsonPrimitive?.contentOrNull
                ?: item["id"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            val coverObj = item["cover"] as? JsonObject
            val cover = coverObj?.get("default")?.jsonPrimitive?.contentOrNull
                ?: coverObj?.get("md")?.jsonPrimitive?.contentOrNull
                ?: item["image"]?.jsonPrimitive?.contentOrNull
            TitleSummary(
                id = "/$slug",
                title = item["rus_name"]?.jsonPrimitive?.contentOrNull
                    ?: item["name"]?.jsonPrimitive?.contentOrNull
                    ?: slug,
                coverUrl = cover?.takeIf { it.isNotEmpty() },
                typeLabel = nestedLabel(item["type"]),
                latestChapter = item["last_chapter_number"]?.jsonPrimitive?.contentOrNull
                    ?: item["last_chapter"]?.jsonPrimitive?.contentOrNull,
            )
        }
        return PagedResult(items = items, page = page, hasNext = items.size >= 20)
    }

    private fun slug(id: String): String {
        val path = id.substringBefore('?').trim('/')
        return path.split('/').firstOrNull().orEmpty().ifEmpty { id.trim('/') }
    }

    private fun nestedLabel(el: JsonElement?): String? = when (el) {
        is JsonObject -> el["label"]?.jsonPrimitive?.contentOrNull ?: el["name"]?.jsonPrimitive?.contentOrNull
        is JsonPrimitive -> el.contentOrNull
        else -> null
    }

    private fun asObject(el: JsonElement): JsonObject = el as? JsonObject ?: JsonObject(emptyMap())
}
