package com.lume.app.data.backup

import android.content.Context
import android.net.Uri
import com.lume.app.data.db.BookmarkEntity
import com.lume.app.data.db.CategoryEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.ProgressEntity
import com.lume.app.data.db.SourcePackEntity
import com.lume.app.data.db.TitlePrefsEntity
import com.lume.app.data.db.TrackerLinkEntity
import com.lume.app.data.source.SourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val bookmarks: List<BookmarkEntityDto> = emptyList(),
    val progress: List<ProgressEntityDto> = emptyList(),
    val categories: List<CategoryEntityDto> = emptyList(),
    val sourcePacks: List<SourcePackDto> = emptyList(),
    val titlePrefs: List<TitlePrefsDto> = emptyList(),
    val trackerLinks: List<TrackerLinkDto> = emptyList(),
)

@Serializable
data class BookmarkEntityDto(
    val sourceId: String,
    val titleId: String,
    val titleName: String,
    val coverUrl: String? = null,
    val typeLabel: String? = null,
    val createdAt: Long = 0L,
    val categoryIdsJson: String = "[]",
)

@Serializable
data class ProgressEntityDto(
    val sourceId: String,
    val titleId: String,
    val titleName: String,
    val coverUrl: String? = null,
    val typeLabel: String? = null,
    val chapterId: String,
    val chapterName: String,
    val chapterUrl: String,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val updatedAt: Long = 0L,
)

@Serializable
data class CategoryEntityDto(
    val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)

@Serializable
data class SourcePackDto(
    val id: String,
    val json: String,
    val installedAt: Long = 0L,
)

@Serializable
data class TitlePrefsDto(
    val sourceId: String,
    val titleId: String,
    val readerMode: Int? = null,
    val readerTheme: Int? = null,
    val gap: Float? = null,
)

@Serializable
data class TrackerLinkDto(
    val sourceId: String,
    val titleId: String,
    val tracker: String,
    val remoteId: String,
    val remoteUrl: String? = null,
    val status: String? = null,
    val score: Float? = null,
    val lastSync: Long = 0L,
)

class BackupRepository(
    private val context: Context,
    private val dao: LibraryDao,
    private val sources: SourceRegistry,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
) {
    fun backupsDir(): File {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun createBackup(): File = withContext(Dispatchers.IO) {
        val payload = BackupPayload(
            bookmarks = dao.allBookmarks().map {
                BookmarkEntityDto(
                    sourceId = it.sourceId,
                    titleId = it.titleId,
                    titleName = it.titleName,
                    coverUrl = it.coverUrl,
                    typeLabel = it.typeLabel,
                    createdAt = it.createdAt,
                    categoryIdsJson = it.categoryIdsJson,
                )
            },
            progress = dao.allProgress().map {
                ProgressEntityDto(
                    sourceId = it.sourceId,
                    titleId = it.titleId,
                    titleName = it.titleName,
                    coverUrl = it.coverUrl,
                    typeLabel = it.typeLabel,
                    chapterId = it.chapterId,
                    chapterName = it.chapterName,
                    chapterUrl = it.chapterUrl,
                    pageIndex = it.pageIndex,
                    pageCount = it.pageCount,
                    updatedAt = it.updatedAt,
                )
            },
            categories = dao.allCategories().map {
                CategoryEntityDto(id = it.id, name = it.name, sortOrder = it.sortOrder)
            },
            sourcePacks = dao.allSourcePacks().map {
                SourcePackDto(id = it.id, json = it.json, installedAt = it.installedAt)
            },
            titlePrefs = dao.allTitlePrefs().map {
                TitlePrefsDto(
                    sourceId = it.sourceId,
                    titleId = it.titleId,
                    readerMode = it.readerMode,
                    readerTheme = it.readerTheme,
                    gap = it.gap,
                )
            },
            trackerLinks = dao.allTrackerLinks().map {
                TrackerLinkDto(
                    sourceId = it.sourceId,
                    titleId = it.titleId,
                    tracker = it.tracker,
                    remoteId = it.remoteId,
                    remoteUrl = it.remoteUrl,
                    status = it.status,
                    score = it.score,
                    lastSync = it.lastSync,
                )
            },
        )
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(backupsDir(), "LUME_backup_$stamp.json")
        file.writeText(json.encodeToString(payload))
        file
    }

    suspend fun restoreFromFile(file: File) = withContext(Dispatchers.IO) {
        restorePayload(json.decodeFromString(BackupPayload.serializer(), file.readText()))
    }

    @Suppress("unused")
    suspend fun restoreFromUri(uri: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error(com.lume.app.data.locale.appString(com.lume.app.R.string.error_backup_read))
        restorePayload(json.decodeFromString(BackupPayload.serializer(), text))
    }

    suspend fun restoreLatest(): File? = withContext(Dispatchers.IO) {
        val latest = backupsDir().listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.maxByOrNull { it.lastModified() }
            ?: return@withContext null
        restoreFromFile(latest)
        latest
    }

    private suspend fun restorePayload(payload: BackupPayload) {
        for (c in payload.categories) {
            dao.upsertCategory(CategoryEntity(id = c.id, name = c.name, sortOrder = c.sortOrder))
        }
        for (b in payload.bookmarks) {
            dao.upsertBookmark(
                BookmarkEntity(
                    sourceId = b.sourceId,
                    titleId = b.titleId,
                    titleName = b.titleName,
                    coverUrl = b.coverUrl,
                    typeLabel = b.typeLabel,
                    createdAt = b.createdAt,
                    categoryIdsJson = b.categoryIdsJson,
                ),
            )
        }
        for (p in payload.progress) {
            dao.upsertProgress(
                ProgressEntity(
                    sourceId = p.sourceId,
                    titleId = p.titleId,
                    titleName = p.titleName,
                    coverUrl = p.coverUrl,
                    typeLabel = p.typeLabel,
                    chapterId = p.chapterId,
                    chapterName = p.chapterName,
                    chapterUrl = p.chapterUrl,
                    pageIndex = p.pageIndex,
                    pageCount = p.pageCount,
                    updatedAt = p.updatedAt,
                ),
            )
        }
        for (s in payload.sourcePacks) {
            dao.upsertSourcePack(
                SourcePackEntity(id = s.id, json = s.json, installedAt = s.installedAt),
            )
        }
        for (t in payload.titlePrefs) {
            dao.upsertTitlePrefs(
                TitlePrefsEntity(
                    sourceId = t.sourceId,
                    titleId = t.titleId,
                    readerMode = t.readerMode,
                    readerTheme = t.readerTheme,
                    gap = t.gap,
                ),
            )
        }
        for (l in payload.trackerLinks) {
            dao.upsertTrackerLink(
                TrackerLinkEntity(
                    sourceId = l.sourceId,
                    titleId = l.titleId,
                    tracker = l.tracker,
                    remoteId = l.remoteId,
                    remoteUrl = l.remoteUrl,
                    status = l.status,
                    score = l.score,
                    lastSync = l.lastSync,
                ),
            )
        }
        sources.load()
    }
}
