package io.github.kdroidfilter.seforimapp.features.home.widgets

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings

/** "zmanim:LARGE,earth:MEDIUM": unknown widgets are dropped and unknown sizes fall back, so an old save never breaks. */
internal fun decodeLayout(raw: String?): List<WidgetPlacement> {
    if (raw == null) return homeWidgets
    return raw
        .split(',')
        .filter { it.isNotBlank() }
        .mapNotNull { entry ->
            val id = entry.substringBefore(':')
            val widget = availableHomeWidgets.firstOrNull { it.id == id } ?: return@mapNotNull null
            val size = WidgetSize.entries.firstOrNull { it.name == entry.substringAfter(':', "") }
            WidgetPlacement(widget, size?.takeIf { it in widget.sizes } ?: widget.defaultSize)
        }.distinctBy { it.widget.id }
}

internal fun encodeLayout(layout: List<WidgetPlacement>): String = layout.joinToString(",") { "${it.widget.id}:${it.size.name}" }

/** The edits the Home offers on its layout, each saved at once. */
internal class HomeWidgetsLayout(
    private val appSettings: AppSettings,
) {
    fun current(): List<WidgetPlacement> = decodeLayout(appSettings.homeWidgetsLayoutFlow.value)

    fun save(layout: List<WidgetPlacement>) = appSettings.setHomeWidgetsLayout(encodeLayout(layout))

    /** At the end, or in [before]'s place when dropped on it. */
    fun add(
        widget: HomeWidget,
        size: WidgetSize = widget.defaultSize,
        before: HomeWidget? = null,
    ) {
        val layout = current()
        if (layout.any { it.widget.id == widget.id }) return
        val index = layout.indexOfFirst { it.widget.id == before?.id }.takeIf { it >= 0 } ?: layout.size
        save(layout.toMutableList().apply { add(index, WidgetPlacement(widget, size)) })
    }

    /** Returns what was removed and where, for an undo to [restore] it. */
    fun remove(widget: HomeWidget): RemovedWidget? {
        val layout = current()
        val index = layout.indexOfFirst { it.widget.id == widget.id }
        if (index < 0) return null
        save(layout.filterIndexed { i, _ -> i != index })
        return RemovedWidget(layout[index], index)
    }

    fun restore(removed: RemovedWidget) {
        val layout = current()
        if (layout.any { it.widget.id == removed.placement.widget.id }) return
        save(layout.toMutableList().apply { add(removed.index.coerceAtMost(size), removed.placement) })
    }

    fun resize(
        widget: HomeWidget,
        size: WidgetSize,
    ) = save(current().map { if (it.widget.id == widget.id) WidgetPlacement(widget, size) else it })

    fun reset() = appSettings.setHomeWidgetsLayout(null)
}

internal data class RemovedWidget(
    val placement: WidgetPlacement,
    val index: Int,
)
