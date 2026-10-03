package com.lume.app.data.source

import android.content.Context
import com.lume.app.R

/**
 * Catalogue engines are first-party Kotlin adapters.
 * JSON packs only clone *families* (Madara / LibSocial / HiveToons).
 * New engines ship with app updates — no DEX / ExtensionLoader.
 */
object SourceEngines {
    const val MADARA = "madara"
    const val LIBSOCIAL = "libsocial"
    const val HIVETOONS = "hivetoons"
    const val MANGADEX = "mangadex"
    const val MANGAKATANA = "mangakatana"
    const val ASURA = "asura"
    const val REMANGA = "remanga"
    const val MANGABUFF = "mangabuff"
    const val WEEBCENTRAL = "weebcentral"
    const val DEMONIC = "demonic"
    const val LOCAL = "local"

    /** Product core: stable platforms we intend to maintain. */
    val corePlatform: Set<String> = setOf(
        MANGADEX,
        LIBSOCIAL,
        MADARA,
        HIVETOONS,
        MANGAKATANA,
        ASURA,
        REMANGA,
        MANGABUFF,
        LOCAL,
    )

    /**
     * Importable JSON packs may only target these family engines.
     * One-site engines (weebcentral, demonic, …) are not pack templates.
     */
    val packImportable: Set<String> = setOf(MADARA, LIBSOCIAL, HIVETOONS)

    /** Compiled one-site adapters; bundled only if present in assets. */
    val siteOnly: Set<String> = setOf(WEEBCENTRAL, DEMONIC)

    fun isKnown(engine: String): Boolean = engine in corePlatform || engine in siteOnly

    fun isPackImportable(engine: String): Boolean = engine in packImportable

    fun displayName(engine: String): String = when (engine) {
        MADARA -> "Madara"
        LIBSOCIAL -> "LibSocial (cdnlibs)"
        HIVETOONS -> "HiveToons / Iken"
        MANGADEX -> "MangaDex"
        MANGAKATANA -> "MangaKatana"
        ASURA -> "Asura"
        REMANGA -> "ReManga"
        MANGABUFF -> "MangaBuff"
        WEEBCENTRAL -> "Weeb Central"
        DEMONIC -> "Demonic Scans"
        LOCAL -> "Local"
        else -> engine
    }

    /** Short UI tag: platform engine vs family pack vs one-site. */
    fun kindLabel(context: Context, engine: String): String = when (engine) {
        in packImportable -> context.getString(R.string.engine_kind_family)
        LOCAL -> context.getString(R.string.engine_kind_local)
        in siteOnly -> context.getString(R.string.engine_kind_site)
        else -> context.getString(R.string.engine_kind_platform)
    }

    fun importRejectedMessage(context: Context, engine: String): String =
        context.getString(R.string.import_reject_engine, engine)
}
