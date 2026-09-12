package com.koma.kt.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks", primaryKeys = ["sourceId", "titleId"])
data class BookmarkEntity(
    val sourceId: String,
    val titleId: String,
    val titleName: String,
    val coverUrl: String? = null,
    val typeLabel: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "reading_progress", primaryKeys = ["sourceId", "titleId"])
data class ProgressEntity(
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
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "chapter_read", primaryKeys = ["sourceId", "titleId", "chapterId"])
data class ChapterReadEntity(
    val sourceId: String,
    val titleId: String,
    val chapterId: String,
    val readAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "downloads", primaryKeys = ["sourceId", "titleId", "chapterId"])
data class DownloadEntity(
    val sourceId: String,
    val titleId: String,
    val chapterId: String,
    val titleName: String,
    val coverUrl: String? = null,
    val chapterName: String,
    val pageCount: Int,
    val pagePathsJson: String,
    val downloadedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "source_packs")
data class SourcePackEntity(
    @PrimaryKey val id: String,
    val json: String,
    val installedAt: Long = System.currentTimeMillis(),
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    suspend fun getBookmark(sourceId: String, titleId: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(item: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteBookmark(sourceId: String, titleId: String)

    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC")
    fun observeHistory(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM reading_progress WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    suspend fun getProgress(sourceId: String, titleId: String): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(item: ProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markChapterRead(item: ChapterReadEntity)

    @Query("SELECT chapterId FROM chapter_read WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun readChapterIds(sourceId: String, titleId: String): List<String>

    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(item: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId = :chapterId LIMIT 1")
    suspend fun getDownload(sourceId: String, titleId: String, chapterId: String): DownloadEntity?

    @Query("SELECT chapterId FROM downloads WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun downloadedChapterIds(sourceId: String, titleId: String): List<String>

    @Query("DELETE FROM downloads WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId = :chapterId")
    suspend fun deleteDownload(sourceId: String, titleId: String, chapterId: String)

    @Query("DELETE FROM reading_progress")
    suspend fun clearAllProgress()

    @Query("DELETE FROM reading_progress WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun clearProgress(sourceId: String, titleId: String)

    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC LIMIT :limit")
    fun observeHistoryLimit(limit: Int): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM source_packs ORDER BY installedAt ASC")
    suspend fun allSourcePacks(): List<SourcePackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourcePack(item: SourcePackEntity)

    @Query("DELETE FROM source_packs WHERE id = :id")
    suspend fun deleteSourcePack(id: String)
}

@Database(
    entities = [
        BookmarkEntity::class,
        ProgressEntity::class,
        ChapterReadEntity::class,
        DownloadEntity::class,
        SourcePackEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class KomaDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}
