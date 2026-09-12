package com.koma.kt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.koma.kt.KomaApp
import com.koma.kt.ui.theme.AppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(nav: NavHostController) {
    val sources by KomaApp.instance.sources.sources.collectAsState()
    val activeId by KomaApp.instance.sources.activeSourceId.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var importUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }

    Scaffold(
        containerColor = AppColors.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Сайт", color = AppColors.textPrimary) },
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
                .padding(16.dp),
        ) {
            Text(
                text = "Источники подключаются JSON-конфигом. Можно импортировать пак по ссылке — приложение пересобирать не нужно.",
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            TextField(
                value = importUrl,
                onValueChange = { importUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text("https://example.com/source.json", color = AppColors.textTertiary)
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AppColors.search,
                    unfocusedContainerColor = AppColors.search,
                    focusedTextColor = AppColors.textPrimary,
                    unfocusedTextColor = AppColors.textPrimary,
                    cursorColor = AppColors.accent,
                    focusedIndicatorColor = AppColors.accent,
                    unfocusedIndicatorColor = AppColors.divider,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val url = importUrl.trim()
                    if (url.isEmpty() || busy) return@Button
                    scope.launch {
                        busy = true
                        runCatching {
                            val imported = KomaApp.instance.sources.importUrl(url)
                            KomaApp.instance.sources.setActive(imported.id)
                            imported
                        }.onSuccess {
                            snack.showSnackbar("Источник добавлен")
                            importUrl = ""
                        }.onFailure {
                            snack.showSnackbar(it.message ?: it.toString())
                        }
                        busy = false
                    }
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.accent,
                    contentColor = Color.Black,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) "Импорт…" else "Импортировать по ссылке")
            }
            Spacer(Modifier.height(20.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(sources, key = { it.manifest.id }) { source ->
                    val selected = source.manifest.id == activeId
                    val bundled = KomaApp.instance.sources.isBundled(source.manifest.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    KomaApp.instance.sources.setActive(source.manifest.id)
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = {
                                scope.launch {
                                    KomaApp.instance.sources.setActive(source.manifest.id)
                                }
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = AppColors.accent,
                                unselectedColor = AppColors.textSecondary,
                            ),
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(source.manifest.name, color = AppColors.textPrimary, fontSize = 15.sp)
                            Text(
                                "${source.manifest.engine} · ${source.manifest.baseUrl}",
                                color = AppColors.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                        if (!bundled) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        KomaApp.instance.sources.remove(source.manifest.id)
                                    }
                                },
                            ) {
                                Icon(Icons.Outlined.Delete, null, tint = AppColors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
