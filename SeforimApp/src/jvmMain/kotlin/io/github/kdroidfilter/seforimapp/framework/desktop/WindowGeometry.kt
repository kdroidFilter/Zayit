package io.github.kdroidfilter.seforimapp.framework.desktop

import dev.nucleusframework.window.tao.TaoMonitors
import kotlin.math.min

/**
 * A monitor may have been unplugged since the session was saved; a window restored to its old
 * bounds would then be invisible and undraggable. Require a minimal visible strip on some screen.
 */
internal fun isVisibleOnAnyScreen(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): Boolean =
    runCatching {
        TaoMonitors.all().any { monitor ->
            // Saved geometry is in dp; monitors report physical px.
            val scale = monitor.scaleFactor.takeIf { it > 0f } ?: 1f
            val b = monitor.boundsPx
            val left = (b.left / scale).toInt()
            val top = (b.top / scale).toInt()
            val right = (b.right / scale).toInt()
            val bottom = (b.bottom / scale).toInt()
            val visibleW = min(x + width, right) - maxOf(x, left)
            val visibleH = min(y + height, bottom) - maxOf(y, top)
            visibleW >= MIN_VISIBLE_W && visibleH >= MIN_VISIBLE_H
        }
    }.getOrDefault(true)

private const val MIN_VISIBLE_W = 120
private const val MIN_VISIBLE_H = 60
