package com.koma.kt.data.locale

import androidx.annotation.StringRes
import com.koma.kt.KomaApp

/** Resolve a string with the current app locale (for non-Composable / engine code). */
fun appString(@StringRes id: Int, vararg formatArgs: Any): String {
    val app = runCatching { KomaApp.instance }.getOrNull() ?: return ""
    return if (formatArgs.isEmpty()) app.getString(id) else app.getString(id, *formatArgs)
}
