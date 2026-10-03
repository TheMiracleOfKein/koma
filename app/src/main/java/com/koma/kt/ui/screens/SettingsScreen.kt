package com.koma.kt.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.koma.kt.KomaApp
import com.koma.kt.R
import com.koma.kt.data.library.LibraryUpdateWorker
import com.koma.kt.data.locale.AppLanguage
import com.koma.kt.data.locale.AppLocaleController
import com.koma.kt.ui.nav.Routes
import com.koma.kt.ui.theme.AppColors
import com.koma.kt.ui.tracker.TrackerAuthActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val darkTheme by KomaApp.instance.prefs.darkTheme.collectAsState(initial = true)
    val libraryUpdates by KomaApp.instance.prefs.libraryUpdatesEnabled.collectAsState(initial = false)
    val appLanguageTag by KomaApp.instance.prefs.appLanguage.collectAsState(initial = "system")
    val anilistTokenState by KomaApp.instance.prefs.anilistAccessToken.collectAsState(initial = null)
    val shikiTokenState by KomaApp.instance.prefs.shikimoriAccessToken.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val downloadPath = remember {
        KomaApp.instance.filesDir.resolve("downloads").absolutePath
    }
    val anilistLoggedIn = !anilistTokenState.isNullOrBlank()
    val shikiLoggedIn = !shikiTokenState.isNullOrBlank()
    val currentLanguage = AppLanguage.fromStored(appLanguageTag)

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), color = AppColors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = AppColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(AppColors.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_dark_theme),
                checked = darkTheme,
                onCheckedChange = { value ->
                    scope.launch { KomaApp.instance.prefs.setDarkTheme(value) }
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.settings_language), color = AppColors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            AppLanguage.entries.forEach { lang ->
                val selected = currentLanguage == lang
                Text(
                    text = stringResource(lang.labelRes),
                    color = AppColors.textPrimary,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) AppColors.accentMuted else AppColors.surface)
                        .clickable {
                            scope.launch {
                                KomaApp.instance.prefs.setAppLanguage(lang.tag)
                                AppLocaleController.apply(lang)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            SettingsSwitchRow(
                label = stringResource(R.string.settings_library_auto_check),
                checked = libraryUpdates,
                onCheckedChange = { value ->
                    scope.launch {
                        KomaApp.instance.prefs.setLibraryUpdatesEnabled(value)
                        LibraryUpdateWorker.schedule(KomaApp.instance, value)
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            SettingsLink(stringResource(R.string.settings_sources)) { nav.navigate(Routes.Sources) }
            SettingsLink(stringResource(R.string.settings_downloads)) { nav.navigate(Routes.Downloads) }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_trackers), color = AppColors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            TrackerLoginCard(
                name = "Shikimori",
                loggedIn = shikiLoggedIn,
                onLogin = {
                    scope.launch {
                        val err = TrackerAuthActivity.start(context, "shikimori")
                        if (err != null) {
                            statusMessage = err
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onLogout = {
                    scope.launch {
                        KomaApp.instance.trackers.shikimori.logout()
                        statusMessage = resources.getString(R.string.tracker_logout_shikimori)
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            TrackerLoginCard(
                name = "AniList",
                loggedIn = anilistLoggedIn,
                onLogin = {
                    scope.launch {
                        val err = TrackerAuthActivity.start(context, "anilist")
                        if (err != null) {
                            statusMessage = err
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onLogout = {
                    scope.launch {
                        KomaApp.instance.trackers.anilist.logout()
                        statusMessage = resources.getString(R.string.tracker_logout_anilist)
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_download_folder), color = AppColors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = downloadPath,
                color = AppColors.textTertiary,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.surface)
                    .padding(16.dp),
            )
            Spacer(Modifier.height(8.dp))
            SettingsLink(stringResource(R.string.settings_restore_downloads)) {
                scope.launch {
                    val n = KomaApp.instance.downloads.recoverFromDisk()
                    val e = runCatching {
                        KomaApp.instance.downloads.enrichMetadata(
                            KomaApp.instance.sources,
                            KomaApp.instance.offlineCache,
                        )
                    }.getOrDefault(0)
                    statusMessage = when {
                        n > 0 || e > 0 -> resources.getString(R.string.restore_downloads_done, n, e)
                        else -> resources.getString(R.string.restore_downloads_empty)
                    }
                    Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsLink(stringResource(R.string.settings_create_backup)) {
                scope.launch {
                    runCatching { KomaApp.instance.backup.createBackup() }
                        .onSuccess {
                            statusMessage = resources.getString(R.string.backup_created, it.name)
                        }
                        .onFailure {
                            statusMessage = it.message ?: resources.getString(R.string.backup_error)
                        }
                }
            }
            SettingsLink(stringResource(R.string.settings_restore_backup)) {
                scope.launch {
                    runCatching { KomaApp.instance.backup.restoreLatest() }
                        .onSuccess { file ->
                            statusMessage = if (file != null) {
                                resources.getString(R.string.backup_restored, file.name)
                            } else {
                                resources.getString(R.string.backup_none)
                            }
                        }
                        .onFailure {
                            statusMessage = it.message ?: resources.getString(R.string.restore_error)
                        }
                }
            }
            statusMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = AppColors.accent, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TrackerLoginCard(
    name: String,
    loggedIn: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.surface)
            .padding(14.dp),
    ) {
        Text(
            if (loggedIn) {
                stringResource(R.string.tracker_status_logged_in, name)
            } else {
                stringResource(R.string.tracker_status_logged_out, name)
            },
            color = AppColors.textPrimary,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(10.dp))
        if (loggedIn) {
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_logout), color = AppColors.danger)
            }
        } else {
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.accent,
                    contentColor = Color.Black,
                ),
            ) {
                Text(stringResource(R.string.action_login))
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = AppColors.textPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.accent,
                checkedTrackColor = AppColors.accentMuted,
            ),
        )
    }
}

@Composable
private fun SettingsLink(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AppColors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.textSecondary,
        )
    }
}
