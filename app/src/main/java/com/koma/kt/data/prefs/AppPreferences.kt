package com.koma.kt.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.koma.kt.domain.ReaderMode
import com.koma.kt.domain.ReaderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore by preferencesDataStore("koma_prefs")

data class ReaderSettings(
    val mode: ReaderMode = ReaderMode.Webtoon,
    val theme: ReaderTheme = ReaderTheme.Dark,
    val gap: Float = 0f,
    val doubleTapZoom: Boolean = true,
    val hidePageNumber: Boolean = false,
)

class AppPreferences(private val context: Context) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val activeSourceKey = androidx.datastore.preferences.core.stringPreferencesKey("active_source_id")
    private val readerModeKey = intPreferencesKey("reader_mode")
    private val readerThemeKey = intPreferencesKey("reader_theme")
    private val readerGapKey = doublePreferencesKey("reader_gap")
    private val readerZoomKey = booleanPreferencesKey("reader_zoom")
    private val readerHidePageKey = booleanPreferencesKey("reader_hide_page")

    val darkTheme: Flow<Boolean> = context.appDataStore.data.map { it[darkThemeKey] ?: true }

    val activeSourceId: Flow<String?> = context.appDataStore.data.map { it[activeSourceKey] }

    val readerSettings: Flow<ReaderSettings> = context.appDataStore.data.map { prefs ->
        val modeIndex = prefs[readerModeKey] ?: ReaderMode.Webtoon.prefsIndex
        val themeIndex = prefs[readerThemeKey] ?: ReaderTheme.Dark.ordinal
        ReaderSettings(
            mode = ReaderMode.fromPrefs(modeIndex),
            theme = ReaderTheme.entries.getOrElse(themeIndex) { ReaderTheme.Dark },
            gap = (prefs[readerGapKey] ?: 0.0).toFloat().coerceIn(0f, 32f),
            doubleTapZoom = prefs[readerZoomKey] ?: true,
            hidePageNumber = prefs[readerHidePageKey] ?: false,
        )
    }

    suspend fun setDarkTheme(value: Boolean) {
        context.appDataStore.edit { it[darkThemeKey] = value }
    }

    suspend fun setActiveSourceId(id: String) {
        context.appDataStore.edit { it[activeSourceKey] = id }
    }

    suspend fun setReaderSettings(settings: ReaderSettings) {
        context.appDataStore.edit {
            it[readerModeKey] = settings.mode.prefsIndex
            it[readerThemeKey] = settings.theme.ordinal
            it[readerGapKey] = settings.gap.toDouble()
            it[readerZoomKey] = settings.doubleTapZoom
            it[readerHidePageKey] = settings.hidePageNumber
        }
    }
}
