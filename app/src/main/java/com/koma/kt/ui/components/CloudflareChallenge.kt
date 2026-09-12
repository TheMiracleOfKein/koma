package com.koma.kt.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.koma.kt.KomaApp
import com.koma.kt.ui.theme.AppColors
import java.net.URI

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloudflareChallengeOverlay(
    uri: URI,
    onDismiss: () -> Unit,
    onSolved: () -> Unit,
) {
    val http = KomaApp.instance.http
    var hint by remember {
        mutableStateOf("Дождитесь сайта источника (не страницы Cloudflare), затем нажмите «Продолжить».")
    }
    val origin = "${uri.scheme}://${uri.host}/"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background),
    ) {
        Text(
            text = "Проверка Cloudflare",
            color = AppColors.textPrimary,
            fontSize = 20.sp,
            modifier = Modifier.padding(16.dp),
        )
        Text(
            text = hint,
            color = AppColors.textSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val title = view?.title.orEmpty().lowercase()
                            val isChallenge = title.contains("just a moment") ||
                                title.contains("attention required") ||
                                title.contains("cloudflare")
                            if (!isChallenge) {
                                hint = "Сайт загружен. Можно продолжить."
                            }
                        }
                    }
                    http.setUserAgent(settings.userAgentString)
                    loadUrl(origin)
                }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = AppColors.textSecondary)
            }
            Button(
                onClick = {
                    http.syncWebViewCookies(origin)
                    onSolved()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.accent,
                    contentColor = Color.Black,
                ),
            ) { Text("Продолжить") }
        }
    }
}
