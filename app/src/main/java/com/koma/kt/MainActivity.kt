package com.koma.kt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.koma.kt.ui.shell.KomaRoot
import com.koma.kt.ui.theme.AppColors
import com.koma.kt.ui.theme.KomaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme by KomaApp.instance.prefs.darkTheme.collectAsState(initial = true)
            KomaTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (darkTheme) AppColors.background else AppColors.lightBackground,
                ) {
                    KomaRoot()
                }
            }
        }
    }
}
