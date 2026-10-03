package com.lume.app.data.downloads

import android.content.Context
import com.lume.app.R
import com.lume.app.data.db.DownloadEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.network.AppHttp
import com.lume.app.data.offline.OfflineTitleCache
import com.lume.app.data.source.SourceRegistry
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import com.lume.app.domain.TitleDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class TitleDiskMeta(
    val sourceId: String,
    val titleId: String,
    val titleName: String,
    val coverUrl: String? = null,
    val typeLabel: String? = null,
    val description: String? = null,
    val status: String? = null,
    val authors: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
)

@Serializable
data class ChapterDiskMeta(
    val chapterId: String,
    val chapterName: String,
    val url: String = "",
    val number: String? = null,
)

class DownloadStore(
    private val context: Context,
    private val dao: LibraryDao,
    private val http: AppHttp,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val progressMap = ConcurrentHashMap<String, Float>()
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    fun keyOf(sourceId: String, titleId: String, chapterId: String) =
        "$sourceId|$titleId|$chapterId"

    fun safe(value: String) =
        value.replace(Regex("""[<>:"/\\|?*]"""), "_").replace(' ', '_')

    private fun titleDir(sourceId: String, titleId: String): File {
        val dir = File(context.filesDir, "downloads/${safe(sourceId)}/${safe(titleId)}")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun chapterDir(sourceId: String, titleId: String, chapterId: String): File {
        val dir = File(titleDir(sourceId, titleId), safe(chapterId))
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun downloadedChapterIds(sourceId: String, titleId: String): Set<String> =
        dao.downloadedChapterIds(sourceId, titleId).toSet()

    suspend fun localPages(sourceId: String, titleId: String, chapterId: String): List<ComicPage>? {
        val meta = dao.getDownload(sourceId, titleId, chapterId)
        if (meta != null) {
            val paths = runCatching { json.decodeFromString<List<String>>(meta.pagePathsJson) }.getOrNull()
            if (paths != null) {
                val pages = mutableListOf<ComicPage>()
                var ok = true
                for ((i, path) in paths.withIndex()) {
                    val file = File(path)
                    if (!file.exists()) {
                        ok = false
                        break
                    }
                    pages += ComicPage(index = i, imageUrl = file.absolutePath)
                }
                if (ok && pages.isNotEmpty()) return pages
            }
        }
        return scanChapterDir(sourceId, titleId, chapterId)
    }

    private fun scanChapterDir(sourceId: String, titleId: String, chapterId: String): List<ComicPage>? {
        val dir = chapterDir(sourceId, titleId, chapterId)
        if (!dir.isDirectory) {
            // Recovered rows may already store safe folder names as ids.
            val alt = File(context.filesDir, "downloads/${safe(sourceId)}/${safe(titleId)}/$chapterId")
            if (alt.isDirectory) {
                return listPagesIn(alt)
            }
            return null
        }
        return listPagesIn(dir)
    }

    private fun listPagesIn(dir: File): List<ComicPage>? {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.length() > 0L && it.name != "meta.json" }
            ?.sortedBy { it.name }
            .orEmpty()
        if (files.isEmpty()) return null
        return files.mapIndexed { i, f -> ComicPage(index = i, imageUrl = f.absolutePath) }
    }

    private fun writeTitleMeta(sourceId: String, titleId: String, title: TitleDetails?) {
        if (title == null) return
        val file = File(titleDir(sourceId, titleId), "meta.json")
        val meta = TitleDiskMeta(
            sourceId = sourceId,
            titleId = titleId,
            titleName = title.title,
            coverUrl = title.coverUrl,
            typeLabel = title.typeLabel,
            description = title.description,
            status = title.status,
            authors = title.authors,
            genres = title.genres,
        )
        runCatching { file.writeText(json.encodeToString(meta)) }
    }

    private fun writeChapterMeta(sourceId: String, titleId: String, chapter: Chapter) {
        val file = File(chapterDir(sourceId, titleId, chapter.id), "meta.json")
        val meta = ChapterDiskMeta(
            chapterId = chapter.id,
            chapterName = chapter.title,
            url = chapter.url,
            number = chapter.number,
        )
        runCatching { file.writeText(json.encodeToString(meta)) }
    }

    private fun readTitleMeta(titleFolder: File): TitleDiskMeta? {
        val file = File(titleFolder, "meta.json")
        if (!file.isFile) return null
        return runCatching { json.decodeFromString<TitleDiskMeta>(file.readText()) }.getOrNull()
    }

    private fun readChapterMeta(chapterFolder: File): ChapterDiskMeta? {
        val file = File(chapterFolder, "meta.json")
        if (!file.isFile) return null
        return runCatching { json.decodeFromString<ChapterDiskMeta>(file.readText()) }.getOrNull()
    }

    /**
     * Rebuilds Room download rows from files left on disk after a DB wipe.
     */
    suspend fun recoverFromDisk(): Int {
        val root = File(context.filesDir, "downloads")
        if (!root.isDirectory) return 0
        var recovered = 0
        root.listFiles()?.filter { it.isDirectory }?.forEach { sourceDir ->
            val folderSourceId = sourceDir.name
            sourceDir.listFiles()?.filter { it.isDirectory }?.forEach { titleFolder ->
                val titleMeta = readTitleMeta(titleFolder)
                val sourceId = titleMeta?.sourceId ?: folderSourceId
                val titleId = titleMeta?.titleId ?: titleFolder.name
                val titleName = titleMeta?.titleName ?: titleFolder.name
                val coverUrl = titleMeta?.coverUrl

                titleFolder.listFiles()?.filter { it.isDirectory }?.forEach { chapterFolder ->
                    val chapterMeta = readChapterMeta(chapterFolder)
                    val chapterId = chapterMeta?.chapterId ?: chapterFolder.name
                    val chapterName = chapterMeta?.chapterName ?: chapterFolder.name
                    val existing = dao.getDownload(sourceId, titleId, chapterId)
                        ?: dao.getDownload(folderSourceId, titleFolder.name, chapterFolder.name)
                    if (existing != null &&
                        existing.titleName != existing.titleId &&
                        existing.coverUrl != null
                    ) {
                        return@forEach
                    }
                    val pages = listPagesIn(chapterFolder) ?: return@forEach
                    val paths = pages.map { it.imageUrl }
                    dao.upsertDownload(
                        DownloadEntity(
                            sourceId = sourceId,
                            titleId = titleId,
                            chapterId = chapterId,
                            titleName = titleName,
                            coverUrl = coverUrl,
                            chapterName = chapterName,
                            pageCount = paths.size,
                            pagePathsJson = json.encodeToString(paths),
                        ),
                    )
                    // Drop weak duplicate keyed by folder names if ids differ
                    if (folderSourceId != sourceId || titleFolder.name != titleId || chapterFolder.name != chapterId) {
                        runCatching {
                            dao.deleteDownload(folderSourceId, titleFolder.name, chapterFolder.name)
                        }
                    }
                    recovered++
                }

                // Title meta is written above; chapters filled in enrich / chaptersFromDownloads.
            }
        }
        return recovered
    }

    /**
     * Pulls live title/chapter names + cover from sources and rebuilds offline cache.
     */
    suspend fun enrichMetadata(sources: SourceRegistry, offlineCache: OfflineTitleCache): Int {
        val all = dao.allDownloads()
        if (all.isEmpty()) return 0
        var updated = 0
        val groups = all.groupBy { it.sourceId to it.titleId }
        for ((key, items) in groups) {
            val (sourceId, titleId) = key
            val source = sources.sourceById(sourceId) ?: continue
            val details = runCatching { source.getTitle(titleId) }.getOrNull()
            val chapters = runCatching { source.getChapters(titleId) }.getOrNull().orEmpty()
            if (details != null) {
                writeTitleMeta(sourceId, titleId, details)
                if (chapters.isNotEmpty()) {
                    offlineCache.save(sourceId, titleId, details, chapters)
                } else {
                    offlineCache.save(
                        sourceId,
                        titleId,
                        details,
                        items.map {
                            Chapter(id = it.chapterId, title = it.chapterName, url = it.chapterId)
                        },
                    )
                }
            }
            for (item in items) {
                val matched = chapters.firstOrNull {
                    it.id == item.chapterId || safe(it.id) == item.chapterId || safe(it.id) == safe(item.chapterId)
                }
                val chapterName = matched?.title ?: item.chapterName
                val chapterId = matched?.id ?: item.chapterId
                if (matched != null) {
                    writeChapterMeta(sourceId, titleId, matched)
                }
                val titleName = details?.title ?: item.titleName
                val cover = details?.coverUrl ?: item.coverUrl
                val needsUpdate = titleName != item.titleName ||
                    cover != item.coverUrl ||
                    chapterName != item.chapterName ||
                    chapterId != item.chapterId
                if (!needsUpdate && details == null) continue
                if (chapterId != item.chapterId) {
                    dao.deleteDownload(item.sourceId, item.titleId, item.chapterId)
                }
                dao.upsertDownload(
                    item.copy(
                        chapterId = chapterId,
                        titleName = titleName,
                        coverUrl = cover,
                        chapterName = chapterName,
                    ),
                )
                updated++
            }
            // If network failed, still build offline cache from whatever we have on disk meta / rows
            if (details == null) {
                val titleFolder = File(context.filesDir, "downloads/${safe(sourceId)}/${safe(titleId)}")
                val disk = readTitleMeta(titleFolder)
                val d = TitleDetails(
                    id = titleId,
                    title = disk?.titleName ?: items.first().titleName,
                    coverUrl = disk?.coverUrl ?: items.first().coverUrl,
                    typeLabel = disk?.typeLabel,
                    description = disk?.description,
                    status = disk?.status,
                    authors = disk?.authors.orEmpty(),
                    genres = disk?.genres.orEmpty(),
                )
                val ch = items.map { row ->
                    val folder = File(titleFolder, safe(row.chapterId)).takeIf { it.isDirectory }
                        ?: File(titleFolder, row.chapterId)
                    val cm = readChapterMeta(folder)
                    Chapter(
                        id = cm?.chapterId ?: row.chapterId,
                        title = cm?.chapterName ?: row.chapterName,
                        url = cm?.url?.takeIf { it.isNotBlank() } ?: (cm?.chapterId ?: row.chapterId),
                        number = cm?.number,
                    )
                }
                offlineCache.save(sourceId, titleId, d, ch)
                if (disk != null && (disk.titleName != items.first().titleName || disk.coverUrl != items.first().coverUrl)) {
                    for (item in items) {
                        dao.upsertDownload(
                            item.copy(titleName = disk.titleName, coverUrl = disk.coverUrl),
                        )
                        updated++
                    }
                }
            }
        }
        return updated
    }

    fun abortPartial(sourceId: String, titleId: String, chapterId: String) {
        val key = keyOf(sourceId, titleId, chapterId)
        progressMap.remove(key)
        _progress.value = progressMap.toMap()
        chapterDir(sourceId, titleId, chapterId).deleteRecursively()
    }

    suspend fun downloadChapter(
        source: CatalogSource,
        titleId: String,
        title: TitleDetails?,
        chapter: Chapter,
        onProgress: ((Float) -> Unit)? = null,
    ) {
        val sourceId = source.manifest.id
        val key = keyOf(sourceId, titleId, chapter.id)
        setProgress(key, 0f)
        onProgress?.invoke(0f)

        try {
            val pages = source.getPages(chapter)
            if (pages.isEmpty()) error(context.getString(R.string.error_no_pages_download))

            writeTitleMeta(sourceId, titleId, title)
            val dir = chapterDir(sourceId, titleId, chapter.id)
            val paths = mutableListOf<String>()
            val referer = source.manifest.headers["Referer"] ?: "${source.manifest.baseUrl.trimEnd('/')}/"

            for ((i, page) in pages.withIndex()) {
                currentCoroutineContext().ensureActive()
                val url = page.imageUrl
                val bytes = if (url.startsWith("/") || (!url.startsWith("http") && File(url).exists())) {
                    File(url).readBytes()
                } else {
                    http.getBytes(
                        URI(url),
                        source = source.manifest,
                        headers = mapOf(
                            "Referer" to referer,
                            "Accept" to "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
                        ),
                    )
                }
                val ext = when {
                    url.contains(".png", ignoreCase = true) -> "png"
                    url.contains(".webp", ignoreCase = true) -> "webp"
                    else -> "jpg"
                }
                val file = File(dir, "%04d.%s".format(i, ext))
                file.writeBytes(bytes)
                paths += file.absolutePath
                val p = (i + 1).toFloat() / pages.size
                setProgress(key, p)
                onProgress?.invoke(p)
            }

            writeChapterMeta(sourceId, titleId, chapter)
            dao.upsertDownload(
                DownloadEntity(
                    sourceId = sourceId,
                    titleId = titleId,
                    chapterId = chapter.id,
                    titleName = title?.title ?: titleId,
                    coverUrl = title?.coverUrl,
                    chapterName = chapter.title,
                    pageCount = paths.size,
                    pagePathsJson = json.encodeToString(paths),
                ),
            )
        } catch (e: CancellationException) {
            abortPartial(sourceId, titleId, chapter.id)
            throw e
        } finally {
            progressMap.remove(key)
            _progress.value = progressMap.toMap()
        }
    }

    suspend fun delete(sourceId: String, titleId: String, chapterId: String) {
        val meta = dao.getDownload(sourceId, titleId, chapterId)
        if (meta != null) {
            runCatching {
                json.decodeFromString<List<String>>(meta.pagePathsJson).forEach { File(it).delete() }
            }
            chapterDir(sourceId, titleId, chapterId).deleteRecursively()
        }
        dao.deleteDownload(sourceId, titleId, chapterId)
    }

    suspend fun deleteTitle(sourceId: String, titleId: String) {
        val items = dao.downloadsForTitle(sourceId, titleId)
        items.forEach { delete(it.sourceId, it.titleId, it.chapterId) }
        titleDir(sourceId, titleId).deleteRecursively()
    }

    private fun setProgress(key: String, value: Float) {
        progressMap[key] = value
        _progress.value = progressMap.toMap()
    }
}
