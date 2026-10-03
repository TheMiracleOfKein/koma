package com.lume.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lume.app.domain.ReaderMode
import com.lume.app.domain.ReaderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore by preferencesDataStore("lume_prefs")

data class ReaderSettings(
    val mode: ReaderMode = ReaderMode.Webtoon,
    val theme: ReaderTheme = ReaderTheme.Dark,
    val gap: Float = 0f,
    val doubleTapZoom: Boolean = true,
    val hidePageNumber: Boolean = false,
    /** Continuous scroll across chapters with a divider; next chapter is prefetched. */
    val seamlessReading: Boolean = true,
)

class AppPreferences(private val context: Context) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val activeSourceKey = stringPreferencesKey("active_source_id")
    private val readerModeKey = intPreferencesKey("reader_mode")
    private val readerThemeKey = intPreferencesKey("reader_theme")
    private val readerGapKey = doublePreferencesKey("reader_gap")
    private val readerZoomKey = booleanPreferencesKey("reader_zoom")
    private val readerHidePageKey = booleanPreferencesKey("reader_hide_page")
    private val readerSeamlessKey = booleanPreferencesKey("reader_seamless")
    private val libraryUpdatesKey = booleanPreferencesKey("library_updates_enabled")
    private val appLanguageKey = stringPreferencesKey("app_language")

    private val anilistTokenKey = stringPreferencesKey("anilist_access_token")
    private val anilistClientIdKey = stringPreferencesKey("anilist_client_id")
    private val anilistClientSecretKey = stringPreferencesKey("anilist_client_secret")
    private val shikiTokenKey = stringPreferencesKey("shikimori_access_token")
    private val shikiRefreshKey = stringPreferencesKey("shikimori_refresh_token")
    private val shikiUserIdKey = stringPreferencesKey("shikimori_user_id")
    private val shikiClientIdKey = stringPreferencesKey("shikimori_client_id")
    private val shikiClientSecretKey = stringPreferencesKey("shikimori_client_secret")

    val darkTheme: Flow<Boolean> = context.appDataStore.data.map { it[darkThemeKey] ?: true }

    val activeSourceId: Flow<String?> = context.appDataStore.data.map { it[activeSourceKey] }

    val libraryUpdatesEnabled: Flow<Boolean> =
        context.appDataStore.data.map { it[libraryUpdatesKey] ?: false }

    /** "system" | "en" | "ru" | … — see [com.lume.app.data.locale.AppLanguage]. */
    val appLanguage: Flow<String> =
        context.appDataStore.data.map { it[appLanguageKey] ?: "system" }

    val anilistAccessToken: Flow<String?> = context.appDataStore.data.map { it[anilistTokenKey] }
    val anilistClientId: Flow<String?> = context.appDataStore.data.map { it[anilistClientIdKey] }
    val anilistClientSecret: Flow<String?> = context.appDataStore.data.map { it[anilistClientSecretKey] }
    val shikimoriAccessToken: Flow<String?> = context.appDataStore.data.map { it[shikiTokenKey] }
    val shikimoriUserId: Flow<String?> = context.appDataStore.data.map { it[shikiUserIdKey] }
    val shikimoriClientId: Flow<String?> = context.appDataStore.data.map { it[shikiClientIdKey] }
    val shikimoriClientSecret: Flow<String?> = context.appDataStore.data.map { it[shikiClientSecretKey] }

    val readerSettings: Flow<ReaderSettings> = context.appDataStore.data.map { prefs ->
        val modeIndex = prefs[readerModeKey] ?: ReaderMode.Webtoon.prefsIndex
        val themeIndex = prefs[readerThemeKey] ?: ReaderTheme.Dark.ordinal
        ReaderSettings(
            mode = ReaderMode.fromPrefs(modeIndex),
            theme = ReaderTheme.entries.getOrElse(themeIndex) { ReaderTheme.Dark },
            gap = (prefs[readerGapKey] ?: 0.0).toFloat().coerceIn(0f, 32f),
            doubleTapZoom = prefs[readerZoomKey] ?: true,
            hidePageNumber = prefs[readerHidePageKey] ?: false,
            seamlessReading = prefs[readerSeamlessKey] ?: true,
        )
    }

    suspend fun setDarkTheme(value: Boolean) {
        context.appDataStore.edit { it[darkThemeKey] = value }
    }

    suspend fun setActiveSourceId(id: String) {
        context.appDataStore.edit { it[activeSourceKey] = id }
    }

    suspend fun setLibraryUpdatesEnabled(value: Boolean) {
        context.appDataStore.edit { it[libraryUpdatesKey] = value }
    }

    suspend fun setAppLanguage(tag: String) {
        context.appDataStore.edit { it[appLanguageKey] = tag }
    }

    suspend fun setReaderSettings(settings: ReaderSettings) {
        context.appDataStore.edit {
            it[readerModeKey] = settings.mode.prefsIndex
            it[readerThemeKey] = settings.theme.ordinal
            it[readerGapKey] = settings.gap.toDouble()
            it[readerZoomKey] = settings.doubleTapZoom
            it[readerHidePageKey] = settings.hidePageNumber
            it[readerSeamlessKey] = settings.seamlessReading
        }
    }

    suspend fun setAniListAccessToken(token: String?) {
        context.appDataStore.edit {
            if (token.isNullOrBlank()) it.remove(anilistTokenKey) else it[anilistTokenKey] = token
        }
    }

    @Suppress("unused")
    suspend fun setAniListClient(clientId: String, clientSecret: String) {
        context.appDataStore.edit {
            it[anilistClientIdKey] = clientId
            it[anilistClientSecretKey] = clientSecret
        }
    }

    suspend fun setShikimoriTokens(access: String?, refresh: String?) {
        context.appDataStore.edit {
            if (access.isNullOrBlank()) {
                it.remove(shikiTokenKey)
                it.remove(shikiUserIdKey)
            } else {
                it[shikiTokenKey] = access
            }
            if (refresh.isNullOrBlank()) it.remove(shikiRefreshKey) else it[shikiRefreshKey] = refresh
        }
    }

    suspend fun setShikimoriUserId(userId: String?) {
        context.appDataStore.edit {
            if (userId.isNullOrBlank()) it.remove(shikiUserIdKey) else it[shikiUserIdKey] = userId
        }
    }

    @Suppress("unused")
    suspend fun setShikimoriClient(clientId: String, clientSecret: String) {
        context.appDataStore.edit {
            it[shikiClientIdKey] = clientId
            it[shikiClientSecretKey] = clientSecret
        }
    }
}
