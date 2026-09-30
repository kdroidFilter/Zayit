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
internal object HomeWidgetsLayout {
    fun current(): List<WidgetPlacement> = decodeLayout(AppSettings.homeWidgetsLayoutFlow.value)

    private fun save(layout: List<WidgetPlacement>) = AppSettings.setHomeWidgetsLayout(encodeLayout(layout))

    fun add(
        widget: HomeWidget,
        size: WidgetSize = widget.defaultSize,
    ) {
        val layout = current()
        if (layout.none { it.widget.id == widget.id }) save(layout + WidgetPlacement(widget, size))
    }

    fun remove(widget: HomeWidget) = save(current().filterNot { it.widget.id == widget.id })

    fun resize(
        widget: HomeWidget,
        size: WidgetSize,
    ) = save(current().map { if (it.widget.id == widget.id) WidgetPlacement(widget, size) else it })

    /** Moves [widget] to the place [target] holds, pushing [target] and what follows one step on. */
    fun move(
        widget: HomeWidget,
        target: HomeWidget,
    ) = save(moveBefore(current(), widget.id, target.id))

    fun reset() = AppSettings.setHomeWidgetsLayout(null)
}

internal fun moveBefore(
    layout: List<WidgetPlacement>,
    id: String,
    targetId: String,
): List<WidgetPlacement> {
    val moving = layout.firstOrNull { it.widget.id == id } ?: return layout
    val from = layout.indexOf(moving)
    val to = layout.indexOfFirst { it.widget.id == targetId }
    if (to < 0 || from == to) return layout
    val rest = layout - moving
    // Dropped on a later widget: land after it, the way the others make room
    val index = if (to > from) rest.indexOfFirst { it.widget.id == targetId } + 1 else rest.indexOfFirst { it.widget.id == targetId }
    return rest.toMutableList().apply { add(index, moving) }
}
