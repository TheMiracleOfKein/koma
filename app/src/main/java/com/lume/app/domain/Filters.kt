@file:Suppress("unused") // Filter variants are part of the catalogue API surface (Madara uses Select).

package com.lume.app.domain

/**
 * Mihon-inspired filter model for catalogue browse/search.
 * Engines return a list of filters; UI binds and passes selected values back into search().
 */
sealed class SourceFilter {
    abstract val id: String
    abstract val name: String

    data class Header(
        override val id: String,
        override val name: String,
    ) : SourceFilter()

    data class Check(
        override val id: String,
        override val name: String,
        var state: Boolean = false,
    ) : SourceFilter()

    data class TriState(
        override val id: String,
        override val name: String,
        /** 0 ignore, 1 include, 2 exclude */
        var state: Int = 0,
    ) : SourceFilter()

    data class Select(
        override val id: String,
        override val name: String,
        val options: List<String>,
        var selected: Int = 0,
    ) : SourceFilter() {
        val selectedValue: String get() = options.getOrElse(selected) { options.firstOrNull().orEmpty() }
    }

    data class Sort(
        override val id: String,
        override val name: String,
        val options: List<String>,
        var index: Int = 0,
        var ascending: Boolean = false,
    ) : SourceFilter()

    data class Text(
        override val id: String,
        override val name: String,
        var state: String = "",
    ) : SourceFilter()

    data class Group(
        override val id: String,
        override val name: String,
        val filters: List<SourceFilter>,
    ) : SourceFilter()
}

data class FilterList(val filters: List<SourceFilter> = emptyList()) {
    fun isEmpty(): Boolean = filters.isEmpty()
}

data class GlobalSearchHit(
    val sourceId: String,
    val sourceName: String,
    val title: TitleSummary,
)

data class LibraryUpdateResult(
    val sourceId: String,
    val titleId: String,
    val titleName: String,
    val newChapterCount: Int,
)
