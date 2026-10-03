package io.github.kdroidfilter.seforimapp.texteffects

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.time.Duration.Companion.milliseconds

/** The text size a reading view shows, and the [modifier] that zooms it with the pointer. */
@Stable
class TextZoom internal constructor(
    textSize: State<Float>,
    val modifier: Modifier,
) {
    val textSize by textSize
}

/**
 * Ctrl/Cmd + wheel (or a trackpad swipe), a trackpad pinch or two touch contacts zoom the text between [minTextSize]
 * and [maxTextSize]: shown at once while zooming, given to [onTextSize] once the gesture rests. Other changes of
 * [textSize] are animated when [animate].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberTextZoom(
    textSize: Float,
    minTextSize: Float,
    maxTextSize: Float,
    onTextSize: (Float) -> Unit,
    animate: Boolean = true,
    onZoomingChange: (Boolean) -> Unit = {},
): TextZoom {
    val rawTextSize by rememberUpdatedState(textSize)
    val currentOnTextSize by rememberUpdatedState(onTextSize)
    val currentOnPointerZoomInProgressChange by rememberUpdatedState(onZoomingChange)
    val pointerZoomScope = rememberCoroutineScope()
    var pointerZoomRenderJob by remember { mutableStateOf<Job?>(null) }
    var pointerZoomCommitJob by remember { mutableStateOf<Job?>(null) }
    var pointerZoomEndJob by remember { mutableStateOf<Job?>(null) }
    var isPointerZooming by remember { mutableStateOf(false) }
    var pointerZoomRenderedTextSize by remember { mutableFloatStateOf(rawTextSize) }
    val pointerZoomAccumulator = remember { PointerZoomAccumulator(rawTextSize) }

    fun setPointerZooming(value: Boolean) {
        if (isPointerZooming != value) {
            isPointerZooming = value
            currentOnPointerZoomInProgressChange(value)
        }
    }

    fun beginPointerZoom() {
        if (!isPointerZooming) {
            val currentTextSize = rawTextSize
            pointerZoomAccumulator.targetTextSize = currentTextSize
            pointerZoomRenderedTextSize = currentTextSize
            setPointerZooming(true)
        }
        pointerZoomEndJob?.cancel()
        pointerZoomEndJob = null
    }

    fun applyPointerZoomTargetToMainContent() {
        val targetTextSize =
            pointerZoomAccumulator.targetTextSize.coerceIn(minTextSize, maxTextSize)
        if (abs(targetTextSize - pointerZoomRenderedTextSize) >= 0.01f) {
            pointerZoomRenderedTextSize = targetTextSize
        }
    }

    fun schedulePointerZoomRenderUpdate() {
        if (pointerZoomRenderJob?.isActive == true) return

        pointerZoomRenderJob =
            pointerZoomScope.launch {
                withFrameNanos { }
                applyPointerZoomTargetToMainContent()
                pointerZoomRenderJob = null
            }
    }

    fun finishPointerZoom() {
        pointerZoomEndJob?.cancel()
        pointerZoomEndJob =
            pointerZoomScope.launch {
                delay(50.milliseconds)
                setPointerZooming(false)
            }
    }

    fun commitPointerZoom(cancelPendingJob: Boolean = true) {
        if (!isPointerZooming) return
        if (cancelPendingJob) {
            pointerZoomCommitJob?.cancel()
        }
        pointerZoomCommitJob = null
        pointerZoomRenderJob?.cancel()
        pointerZoomRenderJob = null

        val targetTextSize =
            pointerZoomAccumulator.targetTextSize.coerceIn(minTextSize, maxTextSize)
        if (abs(targetTextSize - pointerZoomRenderedTextSize) >= 0.01f) {
            pointerZoomRenderedTextSize = targetTextSize
        }
        if (abs(targetTextSize - rawTextSize) >= 0.01f) {
            currentOnTextSize(targetTextSize)
        }
        pointerZoomAccumulator.targetTextSize = targetTextSize
        finishPointerZoom()
    }

    fun schedulePointerZoomCommit() {
        pointerZoomCommitJob?.cancel()
        pointerZoomCommitJob =
            pointerZoomScope.launch {
                delay(120.milliseconds)
                commitPointerZoom(cancelPendingJob = false)
            }
    }

    fun applyPointerZoomFactor(factor: Float) {
        if (!factor.isFinite() || factor <= 0f) return

        beginPointerZoom()
        val currentTextSize = pointerZoomAccumulator.targetTextSize
        val newTextSize =
            (currentTextSize * factor)
                .coerceIn(minTextSize, maxTextSize)

        if (abs(newTextSize - currentTextSize) >= 0.01f) {
            pointerZoomAccumulator.targetTextSize = newTextSize
            schedulePointerZoomRenderUpdate()
        }
    }

    val applyPointerZoomFactorState = rememberUpdatedState<(Float) -> Unit> { factor -> applyPointerZoomFactor(factor) }

    LaunchedEffect(rawTextSize, isPointerZooming) {
        if (!isPointerZooming) {
            pointerZoomAccumulator.targetTextSize = rawTextSize
            pointerZoomRenderedTextSize = rawTextSize
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pointerZoomRenderJob?.cancel()
            pointerZoomCommitJob?.cancel()
            pointerZoomEndJob?.cancel()
            currentOnPointerZoomInProgressChange(false)
        }
    }

    // Animated only when asked (a background tab snaps)
    val shownTextSize =
        animateFloatAsState(
            targetValue = if (isPointerZooming) pointerZoomRenderedTextSize else rawTextSize,
            animationSpec = if (animate && !isPointerZooming) tween(durationMillis = 300) else snap(),
            label = "textSizeAnimation",
        )

    // Ctrl/Cmd + wheel (or trackpad swipe) zooms the text.
    fun zoomScroll(
        event: PointerEvent,
        delta: Offset?,
    ) {
        val isZoomScroll = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
        if (!isZoomScroll) return
        val scrollDelta = delta ?: Offset.Zero
        val zoomDelta =
            if (abs(scrollDelta.y) >= abs(scrollDelta.x)) {
                scrollDelta.y
            } else {
                scrollDelta.x
            }
        if (zoomDelta == 0f) return

        val exponent = (-zoomDelta * 0.08f).coerceIn(-0.25f, 0.25f)
        applyPointerZoomFactorState.value(exp(exponent.toDouble()).toFloat())
        schedulePointerZoomCommit()
        event.changes.forEach { it.consume() }
    }

    val modifier =
        Modifier
            .onPointerEvent(PointerEventType.Scroll) { event -> zoomScroll(event, event.changes.firstOrNull()?.scrollDelta) }
            // A trackpad swipe arrives as Pan on macOS (Nucleus 2.6): AWT's delta × 10 dp.
            .onPointerEvent(PointerEventType.PanMove) { event ->
                zoomScroll(
                    event,
                    event.changes
                        .firstOrNull()
                        ?.panOffset
                        ?.div(PAN_DP_PER_NOTCH * density),
                )
            }
            // A trackpad pinch the platform recognized (Nucleus 2.6: Scale events at the
            // cursor, each carrying its ratio to the last one).
            .onPointerEvent(PointerEventType.ScaleChange) { event ->
                val factor = event.changes.firstOrNull()?.scaleFactor ?: return@onPointerEvent
                if (abs(factor - 1f) > 0.0005f) applyPointerZoomFactorState.value(factor.coerceIn(0.85f, 1.18f))
                schedulePointerZoomCommit()
                event.changes.forEach { it.consume() }
            }.onPointerEvent(PointerEventType.ScaleEnd) { commitPointerZoom() }
            // Two real touch contacts (a touchscreen).
            .pointerInput(Unit) {
                awaitEachGesture {
                    var previousDistance = 0f
                    var hasZoomed = false

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressedChanges = event.changes.filter { it.pressed }

                        if (pressedChanges.size >= 2) {
                            val distance = averageDistanceToCentroid(pressedChanges)
                            if (previousDistance > 0f && distance > 0f) {
                                val zoomFactor = (distance / previousDistance).coerceIn(0.85f, 1.18f)
                                if (abs(zoomFactor - 1f) > 0.002f) {
                                    applyPointerZoomFactorState.value(zoomFactor)
                                    hasZoomed = true
                                }
                            }
                            previousDistance = distance

                            if (hasZoomed) {
                                event.changes.forEach { it.consume() }
                            }
                        } else {
                            previousDistance = 0f
                        }
                    } while (event.changes.any { it.pressed })

                    if (hasZoomed) {
                        commitPointerZoom()
                    }
                }
            }
    return TextZoom(shownTextSize, modifier)
}

private const val PAN_DP_PER_NOTCH = 10f

private fun averageDistanceToCentroid(changes: List<PointerInputChange>): Float {
    if (changes.isEmpty()) return 0f

    var centroid = Offset.Zero
    changes.forEach { change ->
        centroid += change.position
    }
    centroid /= changes.size.toFloat()

    var totalDistance = 0f
    changes.forEach { change ->
        totalDistance += (change.position - centroid).getDistance()
    }
    return totalDistance / changes.size.toFloat()
}

private class PointerZoomAccumulator(
    initialTextSize: Float,
) {
    var targetTextSize: Float = initialTextSize
}
