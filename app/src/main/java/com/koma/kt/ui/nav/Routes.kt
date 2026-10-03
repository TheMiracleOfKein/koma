package com.koma.kt.ui.nav

import com.koma.kt.util.urlEncode
import java.net.URLDecoder

object Routes {
    const val Library = "library"
    const val Catalog = "catalog"
    const val Home = "home"
    const val History = "history"
    const val Search = "search"
    const val Sources = "sources"
    const val Settings = "settings"
    const val Downloads = "downloads"
    const val Title = "title/{sourceId}/{titleId}"
    const val Chapters = "title/{sourceId}/{titleId}/chapters"
    const val Reader = "read/{sourceId}/{titleId}/{chapterId}"

    fun title(sourceId: String, titleId: String) =
        "title/$sourceId/${encode(titleId)}"

    fun chapters(sourceId: String, titleId: String) =
        "title/$sourceId/${encode(titleId)}/chapters"

    fun reader(sourceId: String, titleId: String, chapterId: String) =
        "read/$sourceId/${encode(titleId)}/${encode(chapterId)}"

    fun encode(value: String): String =
        urlEncode(value).replace("+", "%20")

    fun decode(value: String): String =
        URLDecoder.decode(value, "UTF-8")
}
