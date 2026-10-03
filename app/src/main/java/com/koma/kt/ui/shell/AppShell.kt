package com.koma.kt.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.koma.kt.KomaApp
import com.koma.kt.R
import com.koma.kt.ui.components.CloudflareChallengeOverlay
import com.koma.kt.ui.components.KomaLogo
import com.koma.kt.ui.nav.Routes
import com.koma.kt.ui.screens.CatalogScreen
import com.koma.kt.ui.screens.ChaptersScreen
import com.koma.kt.ui.screens.DownloadsScreen
import com.koma.kt.ui.screens.HistoryScreen
import com.koma.kt.ui.screens.HomeScreen
import com.koma.kt.ui.screens.LibraryScreen
import com.koma.kt.ui.screens.ReaderScreen
import com.koma.kt.ui.screens.SearchScreen
import com.koma.kt.ui.screens.SettingsScreen
import com.koma.kt.ui.screens.SourcesScreen
import com.koma.kt.ui.screens.TitleScreen
import com.koma.kt.ui.theme.AppColors
import java.net.URI

private val MainTabRoutes = setOf(
    Routes.Library,
    Routes.Catalog,
    Routes.Home,
    Routes.History,
)

@Composable
fun KomaRoot() {
    val nav = rememberNavController()
    var challengeUri by remember { mutableStateOf<URI?>(null) }
    val http = KomaApp.instance.http

    LaunchedEffect(Unit) {
        http.challengeRequests.collect { challengeUri = it }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppShell(nav)
        challengeUri?.let { uri ->
            CloudflareChallengeOverlay(
                uri = uri,
                onDismiss = { challengeUri = null },
                onSolved = { challengeUri = null },
            )
        }
    }
}

@Composable
fun AppShell(nav: NavHostController) {
    var menuOpen by remember { mutableStateOf(false) }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottomBar = route in MainTabRoutes

    Box(modifier = Modifier.fillMaxSize().background(AppColors.background)) {
        Scaffold(
            containerColor = AppColors.background,
            // Only consume bottom insets for the nav bar; screens with TopAppBar
            // handle status bars themselves (avoids double top gap on Title etc.).
            contentWindowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
            bottomBar = {
                if (showBottomBar) {
                    BottomNavBar(
                        currentRoute = route,
                        onSelect = { target ->
                            if (target == null) {
                                menuOpen = true
                            } else {
                                nav.navigate(target) {
                                    popUpTo(nav.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.Home,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(AppColors.background),
            ) {
                composable(Routes.Home) { HomeScreen(nav) }
                composable(Routes.Catalog) { CatalogScreen(nav) }
                composable(Routes.Library) { LibraryScreen(nav) }
                composable(Routes.History) { HistoryScreen(nav) }
                composable(Routes.Search) { SearchScreen(nav) }
                composable(Routes.Sources) { SourcesScreen(nav) }
                composable(Routes.Settings) { SettingsScreen(nav) }
                composable(Routes.Downloads) { DownloadsScreen(nav) }
                composable(
                    route = Routes.Title,
                    arguments = listOf(
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("titleId") { type = NavType.StringType },
                    ),
                ) { entry ->
                    TitleScreen(
                        nav = nav,
                        sourceId = entry.arguments!!.getString("sourceId")!!,
                        titleId = Routes.decode(entry.arguments!!.getString("titleId")!!),
                    )
                }
                composable(
                    route = Routes.Chapters,
                    arguments = listOf(
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("titleId") { type = NavType.StringType },
                    ),
                ) { entry ->
                    ChaptersScreen(
                        nav = nav,
                        sourceId = entry.arguments!!.getString("sourceId")!!,
                        titleId = Routes.decode(entry.arguments!!.getString("titleId")!!),
                    )
                }
                composable(
                    route = Routes.Reader,
                    arguments = listOf(
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("titleId") { type = NavType.StringType },
                        navArgument("chapterId") { type = NavType.StringType },
                    ),
                ) { entry ->
                    ReaderScreen(
                        nav = nav,
                        sourceId = entry.arguments!!.getString("sourceId")!!,
                        titleId = Routes.decode(entry.arguments!!.getString("titleId")!!),
                        chapterId = Routes.decode(entry.arguments!!.getString("chapterId")!!),
                    )
                }
            }
        }

        if (menuOpen) {
            MainMenu(
                onClose = { menuOpen = false },
                onNavigate = { target ->
                    menuOpen = false
                    when (target) {
                        Routes.Home, Routes.Catalog, Routes.Library, Routes.History -> {
                            nav.navigate(target) {
                                popUpTo(nav.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        else -> nav.navigate(target)
                    }
                },
            )
        }
    }
}

@Composable
private fun BottomNavBar(
    currentRoute: String?,
    onSelect: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.navBar)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = AppColors.divider, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavSlot(
                selected = currentRoute == Routes.Library,
                label = stringResource(R.string.nav_library),
                icon = Icons.Outlined.Bookmark,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(Routes.Library) },
            )
            NavSlot(
                selected = currentRoute == Routes.Catalog,
                label = stringResource(R.string.nav_catalog),
                icon = Icons.Outlined.GridView,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(Routes.Catalog) },
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onSelect(Routes.Home) },
                contentAlignment = Alignment.Center,
            ) {
                KomaLogo(selected = currentRoute == Routes.Home, size = 30.dp)
            }
            NavSlot(
                selected = currentRoute == Routes.History,
                label = stringResource(R.string.nav_history),
                icon = Icons.Outlined.History,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(Routes.History) },
            )
            NavSlot(
                selected = false,
                label = stringResource(R.string.nav_menu),
                icon = Icons.Outlined.Menu,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(null) },
            )
        }
    }
}

@Composable
private fun NavSlot(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) AppColors.textPrimary else AppColors.textSecondary
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = color, fontSize = 10.sp)
    }
}
