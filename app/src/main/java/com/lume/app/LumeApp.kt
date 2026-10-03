package com.lume.app

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lume.app.data.backup.BackupRepository
import com.lume.app.data.db.LumeDatabase
import com.lume.app.data.downloads.DownloadQueue
import com.lume.app.data.downloads.DownloadStore
import com.lume.app.data.library.LibraryUpdateWorker
import com.lume.app.data.network.AppHttp
import com.lume.app.data.network.NetworkMonitor
import com.lume.app.data.offline.OfflineTitleCache
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.data.source.LibAuthStore
import com.lume.app.data.source.SourceRegistry
import com.lume.app.data.tracker.TrackerRegistry
import com.lume.app.data.tracker.TrackerSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class LumeApp : Application(), ImageLoaderFactory {
    lateinit var http: AppHttp
        private set
    lateinit var database: LumeDatabase
        private set
    lateinit var prefs: AppPreferences
        private set
    lateinit var sources: SourceRegistry
        private set
    lateinit var downloads: DownloadStore
        private set
    lateinit var downloadQueue: DownloadQueue
        private set
    lateinit var backup: BackupRepository
        private set
    lateinit var trackers: TrackerRegistry
        private set
    lateinit var trackerSync: TrackerSync
        private set
    lateinit var offlineCache: OfflineTitleCache
        private set
    lateinit var network: NetworkMonitor
        private set
    lateinit var libAuth: LibAuthStore
        private set

    val dao get() = database.libraryDao()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this
        http = AppHttp()
        network = NetworkMonitor(this)
        database = Room.databaseBuilder(this, LumeDatabase::class.java, "lume.db")
            // Prefer additive migrations; destructive wipes library/download metadata.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .addMigrations(LumeDatabase.MIGRATION_5_6)
            .build()
        prefs = AppPreferences(this)
        libAuth = LibAuthStore(this)
        // Per-app language: empty list = follow system (see AppLanguage.SYSTEM).
        runCatching {
            kotlinx.coroutines.runBlocking {
                com.lume.app.data.locale.AppLocaleController.applyStoredTag(prefs.appLanguage.first())
            }
        }
        sources = SourceRegistry(this, http, prefs, dao, libAuth)
        downloads = DownloadStore(this, dao, http)
        offlineCache = OfflineTitleCache(dao)
        downloadQueue = DownloadQueue(dao, downloads, sources, offlineCache)
        backup = BackupRepository(this, dao, sources)
        trackers = TrackerRegistry(http, dao, prefs)
        trackerSync = TrackerSync(this, dao, trackers)
        appScope.launch(Dispatchers.IO) {
            sources.load()
            val recovered = downloads.recoverFromDisk()
            if (recovered > 0) {
                android.util.Log.i("LumeApp", "Recovered $recovered download(s) from disk")
            }
            runCatching {
                val enriched = downloads.enrichMetadata(sources, offlineCache)
                if (enriched > 0) {
                    android.util.Log.i("LumeApp", "Enriched $enriched download row(s)")
                }
            }
            val enabled = prefs.libraryUpdatesEnabled.first()
            LibraryUpdateWorker.schedule(this@LumeApp, enabled)
        }
    }

    override fun newImageLoader(): ImageLoader {
        val client: OkHttpClient = http.okHttp.newBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                    .header("User-Agent", AppHttp.DEFAULT_UA)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                if (original.header("Referer").isNullOrBlank()) {
                    refererForImageHost(original.url.host)?.let { builder.header("Referer", it) }
                }
                chain.proceed(builder.build())
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    /**
     * Lib CDNs (cover.cdnlibs.org, img*.imglib.info, …) return 403 without a site Referer.
     */
    private fun refererForImageHost(host: String): String? {
        val h = host.lowercase()
        val needsLibReferer =
            h.contains("cdnlibs") ||
                h.contains("imglib") ||
                h.contains("imgslib") ||
                h.contains("hentaicdn") ||
                h.contains("reimg") ||
                h.contains("mangabuff") ||
                h.endsWith("mangalib.me") ||
                h.endsWith("hentailib.me") ||
                h.endsWith("yaoilib.me") ||
                h.endsWith("ranobelib.me") ||
                h.endsWith("remanga.org")
        if (!needsLibReferer) return null
        if (!::sources.isInitialized) {
            return when {
                h.contains("hentai") -> "https://hentailib.me/"
                h.contains("yaoi") -> "https://yaoilib.me/"
                h.contains("ranobe") -> "https://ranobelib.me/"
                h.contains("reimg") || h.contains("remanga") -> "https://remanga.org/"
                h.contains("mangabuff") -> "https://mangabuff.ru/"
                else -> "https://mangalib.me/"
            }
        }
        val all = sources.sources.value
        val preferred = when {
            h.contains("hentai") -> all.firstOrNull { it.manifest.id.contains("hentai", ignoreCase = true) }
            h.contains("yaoi") -> all.firstOrNull { it.manifest.id.contains("yaoi", ignoreCase = true) }
            h.contains("ranobe") -> all.firstOrNull { it.manifest.id.contains("ranobe", ignoreCase = true) }
            h.contains("reimg") || h.contains("remanga") -> all.firstOrNull { it.manifest.engine == "remanga" }
            h.contains("mangabuff") -> all.firstOrNull { it.manifest.engine == "mangabuff" }
            else -> all.firstOrNull { it.manifest.engine.equals("libsocial", ignoreCase = true) && it.manifest.id == "mangalib" }
                ?: all.firstOrNull { it.manifest.engine.equals("libsocial", ignoreCase = true) }
        }
        return preferred?.manifest?.headers?.get("Referer")
            ?: preferred?.manifest?.baseUrl?.trimEnd('/')?.plus("/")
            ?: when {
                h.contains("hentai") -> "https://hentailib.me/"
                h.contains("yaoi") -> "https://yaoilib.me/"
                h.contains("ranobe") -> "https://ranobelib.me/"
                h.contains("reimg") || h.contains("remanga") -> "https://remanga.org/"
                h.contains("mangabuff") -> "https://mangabuff.ru/"
                else -> "https://mangalib.me/"
            }
    }

    companion object {
        lateinit var instance: LumeApp
            private set
    }
}
