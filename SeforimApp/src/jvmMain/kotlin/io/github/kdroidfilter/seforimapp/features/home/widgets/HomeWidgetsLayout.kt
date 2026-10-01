package io.github.kdroidfilter.seforimapp.features.home.widgets

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings

/** Further down than any layout a user makes (every widget stacked at its tallest is under 50 rows). */
private const val MAX_SAVED_ROW = 200

/** The full-width grid layouts are saved and checked at: their limits don't depend on the window. */
private val SAVED_PITCH = CellPitch(MAX_GRID_WIDTH)

/**
 * "zmanim@0,0,13,4;earth@13,0,7,4": each widget's area, in cells. An old or broken save never breaks: unknown widgets
 * are dropped, areas brought within their widget's limits, and a widget overlapping one before it moved to the vacant
 * area nearest; a save of the format before areas keeps its widgets and their order.
 */
internal fun decodeLayout(raw: String?): List<WidgetPlacement> {
    if (raw == null) return defaultLayout()
    if (raw.isNotBlank() && '@' !in raw) return decodeOrderLayout(raw)
    val layout = mutableListOf<WidgetPlacement>()
    for (entry in raw.split(';').filter { it.isNotBlank() }) {
        val widget = availableHomeWidgets.firstOrNull { it.id == entry.substringBefore('@') } ?: continue
        if (layout.any { it.widget.id == widget.id }) continue
        val numbers = entry.substringAfter('@').split(',').map { it.trim().toIntOrNull() }
        if (numbers.size != 4 || numbers.any { it == null }) continue
        val (x, y, w, h) = numbers.map { it!! }
        // A row far beyond any real layout (a corrupted save) would have every search walk to it
        val cell = widget.clamp(CellRect(x, y.coerceAtMost(MAX_SAVED_ROW), w, h), SAVED_PITCH)
        val vacant =
            if (layout.isVacant(cell)) cell else layout.nearestArea(SAVED_PITCH.centre(cell), cell.span, SAVED_PITCH)
        layout += WidgetPlacement(widget, vacant)
    }
    return layout
}

/**
 * The format before widgets had areas, "zmanim:LARGE,earth:MEDIUM": the widgets in their order, each at its default
 * size in the first vacant area, as if added one after the other. The ones removed stay removed.
 */
private fun decodeOrderLayout(raw: String): List<WidgetPlacement> =
    raw
        .split(',')
        .mapNotNull { entry -> availableHomeWidgets.firstOrNull { it.id == entry.substringBefore(':').trim() } }
        .distinct()
        .fold(emptyList()) { layout, widget -> layout + WidgetPlacement(widget, layout.firstVacant(widget.defaultSpan)) }

internal fun encodeLayout(layout: List<WidgetPlacement>): String =
    layout.joinToString(";") { (widget, c) -> "${widget.id}@${c.x},${c.y},${c.w},${c.h}" }

/** The edits the Home offers on its layout, each saved at once. */
internal class HomeWidgetsLayout(
    private val appSettings: AppSettings,
) {
    fun current(): List<WidgetPlacement> = decodeLayout(appSettings.homeWidgetsLayoutFlow.value)

    fun save(layout: List<WidgetPlacement>) = appSettings.setHomeWidgetsLayout(encodeLayout(layout))

    /** The widgets shown, and the ones this platform can't show: those take no cells here, but stay in the save. */
    private fun edit(change: (List<WidgetPlacement>) -> List<WidgetPlacement>) {
        val (shown, hidden) = current().partition { it.widget.isSupported }
        val changed = change(shown)
        save(changed + hidden.filter { h -> changed.none { it.widget.id == h.widget.id } })
    }

    /** At its default size, in the first vacant area (row after row), as Android adds a widget from its picker. */
    fun add(widget: HomeWidget) {
        if (current().any { it.widget.id == widget.id }) return
        edit { layout -> layout + WidgetPlacement(widget, layout.firstVacant(widget.defaultSpan)) }
    }

    /** Returns what was removed, for an undo to [restore] it. Its area stays empty: nothing moves into it. */
    fun remove(widget: HomeWidget): RemovedWidget? {
        val layout = current()
        val removed = layout.firstOrNull { it.widget.id == widget.id } ?: return null
        save(layout - removed)
        return RemovedWidget(removed)
    }

    /** Back on its area, or the vacant one nearest if another widget took it since. */
    fun restore(removed: RemovedWidget) {
        val placement = removed.placement
        if (current().any { it.widget.id == placement.widget.id }) return
        edit { layout ->
            val cell = placement.cell
            val vacant = if (layout.isVacant(cell)) cell else layout.nearestArea(SAVED_PITCH.centre(cell), cell.span, SAVED_PITCH)
            layout + placement.copy(cell = vacant)
        }
    }

    /** [widget] on [cell] (within its limits), the widgets in the way making room. */
    fun place(
        widget: HomeWidget,
        cell: CellRect,
    ) {
        val placement = WidgetPlacement(widget, widget.clamp(cell, SAVED_PITCH))
        // One this platform can't show takes no cells here: it just moves, pushing nothing
        if (!widget.isSupported) {
            save(current().map { if (it.widget.id == widget.id) placement else it })
            return
        }
        edit { it.reorder(placement, SAVED_PITCH) }
    }

    fun reset() = appSettings.setHomeWidgetsLayout(null)
}

internal data class RemovedWidget(
    val placement: WidgetPlacement,
)
