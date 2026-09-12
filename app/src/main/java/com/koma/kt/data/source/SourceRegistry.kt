package com.koma.kt.data.source

import android.content.Context
import com.koma.kt.data.db.LibraryDao
import com.koma.kt.data.db.SourcePackEntity
import com.koma.kt.data.network.AppHttp
import com.koma.kt.data.prefs.AppPreferences
import com.koma.kt.domain.CatalogSource
import com.koma.kt.domain.SourceManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.net.URI

class SourceRegistry(
    private val context: Context,
    private val http: AppHttp,
    private val prefs: AppPreferences,
    private val dao: LibraryDao,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    private val bundledIds = mutableSetOf<String>()
    private val _sources = MutableStateFlow<List<CatalogSource>>(emptyList())
    val sources: StateFlow<List<CatalogSource>> = _sources.asStateFlow()

    val activeSourceId: Flow<String?> = prefs.activeSourceId

    fun isBundled(id: String): Boolean = id in bundledIds

    suspend fun load() {
        bundledIds.clear()
        val loaded = mutableListOf<CatalogSource>()
        val assetNames = context.assets.list("sources").orEmpty().filter { it.endsWith(".json") }
        for (name in assetNames) {
            val raw = context.assets.open("sources/$name").bufferedReader().use { it.readText() }
            val manifest = json.decodeFromString(SourceManifest.serializer(), raw)
            bundledIds += manifest.id
            upsert(loaded, createEngine(manifest))
        }
        for (pack in dao.allSourcePacks()) {
            runCatching {
                val manifest = json.decodeFromString(SourceManifest.serializer(), pack.json)
                upsert(loaded, createEngine(manifest))
            }
        }
        _sources.value = loaded
        if (prefs.activeSourceId.first() == null && loaded.isNotEmpty()) {
            setActive(loaded.first().manifest.id)
        }
    }

    suspend fun setActive(id: String) {
        prefs.setActiveSourceId(id)
    }

    fun sourceById(id: String): CatalogSource? =
        _sources.value.firstOrNull { it.manifest.id == id }

    suspend fun activeSource(): CatalogSource? {
        val id = prefs.activeSourceId.first()
        return sourceById(id ?: return _sources.value.firstOrNull())
            ?: _sources.value.firstOrNull()
    }

    suspend fun importJson(raw: String): SourceManifest {
        val manifest = json.decodeFromString(SourceManifest.serializer(), raw)
        createEngine(manifest) // validate engine
        dao.upsertSourcePack(
            SourcePackEntity(
                id = manifest.id,
                json = json.encodeToString(SourceManifest.serializer(), manifest),
            ),
        )
        val next = _sources.value.toMutableList()
        upsert(next, createEngine(manifest))
        _sources.value = next
        if (prefs.activeSourceId.first() == null) setActive(manifest.id)
        return manifest
    }

    suspend fun importUrl(url: String): SourceManifest {
        val response = http.get(URI(url), checkCloudflare = false)
        return importJson(response.body)
    }

    suspend fun remove(id: String) {
        if (id in bundledIds) return
        dao.deleteSourcePack(id)
        _sources.value = _sources.value.filterNot { it.manifest.id == id }
        val active = prefs.activeSourceId.first()
        if (active == id && _sources.value.isNotEmpty()) {
            setActive(_sources.value.first().manifest.id)
        }
    }

    private fun upsert(list: MutableList<CatalogSource>, source: CatalogSource) {
        list.removeAll { it.manifest.id == source.manifest.id }
        list += source
    }

    private fun createEngine(manifest: SourceManifest): CatalogSource = when (manifest.engine) {
        "madara" -> MadaraEngine(manifest, http)
        "libsocial" -> LibSocialEngine(manifest, http)
        else -> error("Unknown engine: ${manifest.engine}")
    }
}
