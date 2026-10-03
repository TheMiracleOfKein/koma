package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import com.lume.app.domain.HomeFeed
import com.lume.app.domain.PagedResult
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import com.lume.app.util.urlEncode

class RemangaEngine(
    override val manifest: SourceManifest,
    private val http: AppHttp,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : CatalogSource {
    private val apiBase: String
        get() = (manifest.configString("apiBase") ?: "https://api.remanga.org/api").trimEnd('/')

    private val siteBase: String
        get() = manifest.baseUrl.trimEnd('/')

    private val headers: Map<String, String>
        get() = mapOf(
            "Accept" to "application/json",
            "Referer" to "$siteBase/",
            "Origin" to siteBase,
        )

    override suspend fun getHome(): HomeFeed {
        val popular = getPopular()
        val latest = getLatest()
        return HomeFeed(spotlight = popular.items.take(12), latest = latest.items)
    }

    override suspend fun getLatest(page: Int): PagedResult<TitleSummary> =
        titles(page, ordering = "-chapter_date")

    override suspend fun getPopular(page: Int): PagedResult<TitleSummary> =
        titles(page, ordering = "-score")

    private suspend fun titles(page: Int, ordering: String): PagedResult<TitleSummary> {
        val uri = URI("$apiBase/titles/?ordering=$ordering&count=20&page=$page")
        val response = http.get(uri, source = manifest, headers = headers, checkCloudflare = false)
        return parseTitleList(response.body, page)
    }

    override suspend fun search(
        query: String,
        page: Int,
        filters: com.lume.app.domain.FilterList,
    ): PagedResult<TitleSummary> {
        val q = urlEncode(query)
        val uri = URI("$apiBase/search/?query=$q&count=20&page=$page")
        val response = http.get(uri, source = manifest, headers = headers, checkCloudflare = false)
        return parseTitleList(response.body, page)
    }

    override fun getFilters(): com.lume.app.domain.FilterList = com.lume.app.domain.FilterList()

    override suspend fun getTitle(id: String): TitleDetails {
        val dir = slug(id)
        val uri = URI("$apiBase/titles/$dir/")
        val response = http.get(uri, source = manifest, headers = headers, checkCloudflare = false)
        val root = asObject(json.parseToJsonElement(response.body))
        val data = asObject(root["content"] ?: root["data"] ?: root)
        val genres = (data["genres"] as? JsonArray).orEmpty().mapNotNull { stringOrNull((it as? JsonObject)?.get("name")) }
        val authors = (data["creators"] as? JsonObject)?.values.orEmpty().flatMap { el ->
            when (el) {
                is JsonArray -> el.mapNotNull { stringOrNull((it as? JsonObject)?.get("name")) }
                is JsonObject -> listOfNotNull(stringOrNull(el["name"]))
                else -> emptyList()
            }
        }
        return TitleDetails(
            id = dir,
            title = stringOrNull(data["main_name"]) ?: stringOrNull(data["secondary_name"]) ?: dir,
            altTitle = stringOrNull(data["secondary_name"]),
            coverUrl = coverUrl(data["cover"]),
            description = stringOrNull(data["description"]),
            typeLabel = nestedName(data["type"]),
            status = nestedName(data["status"]) ?: nestedName(data["translate_status"]),
            authors = authors,
            genres = genres,
        )
    }

    override suspend fun getChapters(titleId: String): List<Chapter> {
        val dir = slug(titleId)
        val detailUri = URI("$apiBase/titles/$dir/")
        val detail = http.get(detailUri, source = manifest, headers = headers, checkCloudflare = false)
        val root = asObject(json.parseToJsonElement(detail.body))
        val data = asObject(root["content"] ?: root)
        val branches = data["branches"] as? JsonArray ?: JsonArray(emptyList())
        val branchId = (branches.firstOrNull() as? JsonObject)?.let { stringOrNull(it["id"]) }
            ?: throw SourceFetchException("ReManga: нет веток перевода")
        val out = ArrayList<Chapter>()
        var page = 1
        while (page <= 50) {
            val uri = URI("$apiBase/titles/chapters/?branch_id=$branchId&page=$page&count=100&ordering=-index")
            val response = http.get(uri, source = manifest, headers = headers, checkCloudflare = false)
            val chRoot = asObject(json.parseToJsonElement(response.body))
            val list = chRoot["content"] as? JsonArray ?: break
            if (list.isEmpty()) break
            for (el in list) {
                val item = el as? JsonObject ?: continue
                val id = stringOrNull(item["id"]) ?: continue
                val tome = stringOrNull(item["tome"]) ?: ""
                val number = stringOrNull(item["chapter"]) ?: ""
                val name = stringOrNull(item["name"]).orEmpty()
                val paid = item["is_paid"]?.jsonPrimitive?.contentOrNull == "true"
                if (paid) continue
                out += Chapter(
                    id = id,
                    title = buildString {
                        if (tome.isNotBlank()) append("Том $tome ")
                        append("Глава $number")
                        if (name.isNotBlank()) append(" — $name")
                    }.trim(),
                    url = id,
                    number = number,
                    volume = tome.takeIf { it.isNotBlank() },
                    branchId = branchId,
                    dateLabel = stringOrNull(item["upload_date"])?.take(10),
                )
            }
            if (list.size < 100) break
            page++
        }
        return out
    }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val id = chapter.id
        val uri = URI("$apiBase/titles/chapters/$id/")
        val response = http.get(uri, source = manifest, headers = headers, checkCloudflare = false)
        val root = asObject(json.parseToJsonElement(response.body))
        val data = asObject(root["content"] ?: root)
        val pagesEl = data["pages"] ?: throw SourceFetchException("ReManga: нет страниц")
        val urls = mutableListOf<String>()
        when (pagesEl) {
            is JsonArray -> {
                for (row in pagesEl) {
                    when (row) {
                        is JsonArray -> row.forEach { collectPageUrl(it, urls) }
                        else -> collectPageUrl(row, urls)
                    }
                }
            }
            else -> Unit
        }
        if (urls.isEmpty()) throw SourceFetchException("ReManga: пустые страницы")
        return urls.mapIndexed { i, u -> ComicPage(index = i, imageUrl = u) }
    }

    private fun collectPageUrl(el: JsonElement, out: MutableList<String>) {
        val obj = el as? JsonObject ?: return
        val link = stringOrNull(obj["link"]) ?: stringOrNull(obj["url"]) ?: return
        out += if (link.startsWith("http")) link else "$siteBase$link"
    }

    private fun parseTitleList(raw: String, page: Int): PagedResult<TitleSummary> {
        val root = asObject(json.parseToJsonElement(raw))
        val list = root["content"] as? JsonArray ?: JsonArray(emptyList())
        val items = list.mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            val dir = stringOrNull(item["dir"]) ?: return@mapNotNull null
            TitleSummary(
                id = dir,
                title = stringOrNull(item["main_name"]) ?: stringOrNull(item["secondary_name"]) ?: dir,
                coverUrl = coverUrl(item["cover"]),
                typeLabel = stringOrNull(item["type"]) ?: nestedName(item["type"]),
                latestChapter = stringOrNull(item["count_chapters"]),
            )
        }
        return PagedResult(items = items, page = page, hasNext = items.size >= 20)
    }

    private fun coverUrl(el: JsonElement?): String? {
        val obj = el as? JsonObject ?: return stringOrNull(el)?.let { absMedia(it) }
        val path = stringOrNull(obj["mid"]) ?: stringOrNull(obj["high"]) ?: stringOrNull(obj["low"])
        return path?.let { absMedia(it) }
    }

    private fun absMedia(path: String): String {
        if (path.startsWith("http")) return path
        val p = path.removePrefix("/")
        return if (p.startsWith("media/")) "https://remanga.org/$p"
        else "https://remanga.org/media/$p"
    }

    private fun slug(id: String): String = id.trim('/').substringBefore('/').ifBlank { id }

    private fun nestedName(el: JsonElement?): String? = when (el) {
        is JsonObject -> stringOrNull(el["name"]) ?: stringOrNull(el["label"])
        is JsonPrimitive -> el.contentOrNull
        else -> null
    }

    private fun stringOrNull(el: JsonElement?): String? = when (el) {
        null, JsonNull -> null
        is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        else -> null
    }

    private fun asObject(el: JsonElement): JsonObject = el as? JsonObject ?: JsonObject(emptyMap())
}
