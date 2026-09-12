package com.koma.kt

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.koma.kt.data.db.KomaDatabase
import com.koma.kt.data.downloads.DownloadStore
import com.koma.kt.data.network.AppHttp
import com.koma.kt.data.prefs.AppPreferences
import com.koma.kt.data.source.SourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class KomaApp : Application(), ImageLoaderFactory {
    lateinit var http: AppHttp
        private set
    lateinit var database: KomaDatabase
        private set
    lateinit var prefs: AppPreferences
        private set
    lateinit var sources: SourceRegistry
        private set
    lateinit var downloads: DownloadStore
        private set

    val dao get() = database.libraryDao()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this
        http = AppHttp()
        database = Room.databaseBuilder(this, KomaDatabase::class.java, "koma.db")
            .fallbackToDestructiveMigration()
            .build()
        prefs = AppPreferences(this)
        sources = SourceRegistry(this, http, prefs, dao)
        downloads = DownloadStore(this, dao, http)
        appScope.launch(Dispatchers.IO) { sources.load() }
    }

    override fun newImageLoader(): ImageLoader {
        val client: OkHttpClient = http.okHttp.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", AppHttp.DEFAULT_UA)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .build()
                chain.proceed(req)
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

    companion object {
        lateinit var instance: KomaApp
            private set
    }
}
