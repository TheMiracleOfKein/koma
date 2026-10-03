package com.lume.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.net.URI

@Serializable
data class SourceManifest(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val engine: String,
    val baseUrl: String,
    val mediaKind: String = "comics",
    val language: String? = null,
    val icon: String? = null,
    val nsfw: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val engineConfig: JsonObject = JsonObject(emptyMap()),
) {
    val baseUri: URI get() = URI(baseUrl.trimEnd('/') + "/")

    fun configString(key: String): String? {
        val el = engineConfig[key] ?: return null
        return (el as? JsonPrimitive)?.contentOrNull
    }

    fun configBool(key: String, fallback: Boolean = false): Boolean {
        val el = engineConfig[key] ?: return fallback
        val prim = el as? JsonPrimitive ?: return fallback
        return prim.booleanOrNull ?: prim.contentOrNull?.toBooleanStrictOrNull() ?: fallback
    }
}

/**
 * CatalogueSource — Mihon-inspired contract, native to Lume.
 * Engines are first-party Kotlin; JSON packs only clone family engines.
 * No DEX / ExtensionLoader plugins.
 */
interface CatalogSource {
    val manifest: SourceManifest

    val supportsLatest: Boolean get() = true
    val supportsPopular: Boolean get() = true

    suspend fun getHome(): HomeFeed
    suspend fun getLatest(page: Int = 1): PagedResult<TitleSummary>
    suspend fun getPopular(page: Int = 1): PagedResult<TitleSummary>

    /** Default filters for this source (may be empty). */
    fun getFilters(): FilterList = FilterList()

    suspend fun search(
        query: String,
        page: Int = 1,
        filters: FilterList = FilterList(),
    ): PagedResult<TitleSummary>

    suspend fun getTitle(id: String): TitleDetails
    suspend fun getChapters(titleId: String): List<Chapter>
    suspend fun getPages(chapter: Chapter): List<ComicPage>
}

/** Optional per-source preferences screen hook. */
@Suppress("unused")
interface ConfigurableSource {
    fun preferenceKeys(): List<String>
}

class CloudflareException(val uri: URI) : Exception("Cloudflare challenge: $uri")
class SourceFetchException(message: String) : Exception(message)
