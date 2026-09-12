package com.koma.kt.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koma.kt.KomaApp
import com.koma.kt.ui.nav.Routes
import com.koma.kt.ui.theme.AppColors
import kotlinx.coroutines.launch

@Composable
fun MainMenu(
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val darkTheme by KomaApp.instance.prefs.darkTheme.collectAsState(initial = true)
    val sources by KomaApp.instance.sources.sources.collectAsState()
    val activeId by KomaApp.instance.sources.activeSourceId.collectAsState(initial = null)
    val activeName = sources.firstOrNull { it.manifest.id == activeId }?.manifest?.name
        ?: sources.firstOrNull()?.manifest?.name
        ?: "нет"

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.54f))
                .clickable(onClick = onClose),
        )
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .fillMaxHeight()
                    .clickable(onClick = onClose),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(AppColors.background)
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TopChip(
                        icon = if (darkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                        label = "Тема",
                        onClick = {
                            scope.launch { KomaApp.instance.prefs.setDarkTheme(!darkTheme) }
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    TopChip(
                        icon = Icons.Outlined.Search,
                        label = "Поиск",
                        onClick = {
                            onClose()
                            onNavigate(Routes.Search)
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.card),
                ) {
                    MenuTile(
                        icon = Icons.Outlined.Public,
                        title = "Источник",
                        trailingText = activeName,
                        onClick = {
                            onClose()
                            onNavigate(Routes.Sources)
                        },
                    )
                    MenuTile(
                        icon = Icons.Outlined.Settings,
                        title = "Настройки",
                        onClick = {
                            onClose()
                            onNavigate(Routes.Settings)
                        },
                    )
                    MenuTile(
                        icon = Icons.Outlined.Download,
                        title = "Загрузки",
                        onClick = {
                            onClose()
                            onNavigate(Routes.Downloads)
                        },
                    )
                    MenuTile(
                        icon = Icons.Outlined.History,
                        title = "История",
                        onClick = {
                            onClose()
                            onNavigate(Routes.History)
                        },
                    )
                    MenuTile(
                        icon = Icons.Outlined.BookmarkBorder,
                        title = "Закладки",
                        onClick = {
                            onClose()
                            onNavigate(Routes.Library)
                        },
                    )
                    MenuTile(
                        icon = Icons.Outlined.Home,
                        title = "Главная",
                        onClick = {
                            onClose()
                            onNavigate(Routes.Home)
                        },
                    )
                    MenuTile(
                        icon = Icons.Outlined.GridView,
                        title = "Каталог",
                        onClick = {
                            onClose()
                            onNavigate(Routes.Catalog)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TopChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AppColors.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = AppColors.textSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun MenuTile(
    icon: ImageVector,
    title: String,
    trailingText: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AppColors.textSecondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = AppColors.textPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        if (!trailingText.isNullOrBlank()) {
            Text(trailingText, color = AppColors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForwardIos,
            null,
            tint = AppColors.textTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}
