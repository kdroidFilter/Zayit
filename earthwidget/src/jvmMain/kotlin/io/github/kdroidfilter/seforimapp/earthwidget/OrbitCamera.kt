package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.pow

/** Higher = the camera stops sooner after a fling. */
private const val FLING_FRICTION = 1.5f

private const val MIN_VIEW_ZOOM = 0.6f
private const val MAX_VIEW_ZOOM = 3f

/** Zoom factor per Ctrl+wheel tick. */
private const val ZOOM_PER_WHEEL_TICK = 1.1f

/**
 * The user's camera around a 3D widget scene: [yaw] around the scene's pole, [pitch] (clamped to [pitchRange])
 * and [zoom]. [isGesturing] is on while a gesture drives it, so the scene follows directly instead of easing.
 */
@Stable
internal class OrbitCameraState {
    var yaw by mutableFloatStateOf(0f)
        private set
    var pitch by mutableFloatStateOf(0f)
        private set
    var zoom by mutableFloatStateOf(1f)
        private set
    var isGesturing by mutableStateOf(false)
        internal set

    /** Allowed [pitch] offsets; set by the widget from its default elevation. */
    var pitchRange: ClosedFloatingPointRange<Float> = -90f..90f

    internal var fling: Job? = null

    val isMoved: Boolean get() = yaw != 0f || pitch != 0f || zoom != 1f

    fun rotate(
        yawDegrees: Float,
        pitchDegrees: Float,
    ) {
        yaw = (yaw + yawDegrees + 180f).mod(360f) - 180f
        pitch = (pitch + pitchDegrees).coerceIn(pitchRange)
    }

    fun zoomBy(factor: Float) {
        zoom = (zoom * factor).coerceIn(MIN_VIEW_ZOOM, MAX_VIEW_ZOOM)
    }

    fun reset() {
        fling?.cancel()
        yaw = 0f
        pitch = 0f
        zoom = 1f
    }
}

@Composable
internal fun rememberOrbitCameraState(): OrbitCameraState = remember { OrbitCameraState() }

/**
 * Mouse drag (with inertia), trackpad pinch / two-finger scroll, Ctrl+wheel and touchscreen pinch / twist
 * driving [camera]. [degreesPerPx] is the rotation per dragged pixel at zoom 1 (finer when zoomed in).
 */
internal fun Modifier.orbitCameraGestures(
    camera: OrbitCameraState,
    degreesPerPx: () -> Float,
): Modifier =
    pointerInput(camera) {
        // Nucleus 2.6 (Tao) delivers a trackpad pinch as Scale events, a two-finger scroll as Pan events
        // and a mouse wheel as Scroll (same handling as BookContentView).
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                when (event.type) {
                    // Positive panOffset = "scroll down / right": the scene follows the fingers, like a drag.
                    PointerEventType.PanMove -> {
                        val k = degreesPerPx() / camera.zoom
                        camera.rotate(-change.panOffset.x * k, -change.panOffset.y * k)
                    }
                    // A gesture follows the fingers directly (like a drag): no spring chasing each step,
                    // which trembled on fast swipes and spun the long way round at ±180°.
                    PointerEventType.PanStart, PointerEventType.ScaleStart -> {
                        camera.fling?.cancel()
                        camera.isGesturing = true
                    }
                    PointerEventType.PanEnd, PointerEventType.ScaleEnd -> camera.isGesturing = false
                    // Each event carries its ratio to the previous one
                    PointerEventType.ScaleChange -> camera.zoomBy(change.scaleFactor)
                    // Ctrl+wheel zooms; a plain wheel is left to the page
                    PointerEventType.Scroll ->
                        if (event.keyboardModifiers.isCtrlPressed) {
                            camera.zoomBy(ZOOM_PER_WHEEL_TICK.pow(-change.scrollDelta.y))
                        } else {
                            continue
                        }
                    else -> continue
                }
                change.consume()
            }
        }
    }.pointerInput(camera) {
        // One pointer drags the camera (with inertia). Two pointers are a touchscreen pinch / twist.
        val velocityTracker = VelocityTracker()
        coroutineScope {
            val flingScope = this
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                velocityTracker.resetTracking()
                var slop = Offset.Zero
                var dragging = false
                var transformed = false
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        if (!transformed && !dragging) {
                            camera.fling?.cancel()
                            camera.isGesturing = true
                        }
                        transformed = true
                        camera.zoomBy(event.calculateZoom())
                        // A clockwise twist turns the scene clockwise as seen from above the pole
                        camera.rotate(-event.calculateRotation(), 0f)
                        event.changes.forEach { it.consume() }
                    } else if (!transformed) {
                        val change = pressed.firstOrNull() ?: break
                        val delta = change.positionChange()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        if (!dragging) {
                            slop += delta
                            if (slop.getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                                camera.fling?.cancel()
                                camera.isGesturing = true
                            }
                        }
                        if (dragging) {
                            // Horizontal: turn around the pole; vertical: tilt toward it.
                            val k = degreesPerPx() / camera.zoom
                            camera.rotate(delta.x * k, delta.y * k)
                            change.consume()
                        }
                    }
                } while (event.changes.any { it.pressed })

                if (dragging && !transformed) {
                    // Inertia: keep turning with the release velocity, decaying to a stop.
                    // isGesturing stays on meanwhile so the rotation isn't re-smoothed.
                    val velocity = velocityTracker.calculateVelocity()
                    val speed = hypot(velocity.x, velocity.y)
                    camera.fling =
                        flingScope.launch {
                            // Decay the release speed along the release direction, split back into yaw/pitch.
                            if (speed > 0f) {
                                var previous = 0f
                                AnimationState(0f, speed).animateDecay(exponentialDecay(FLING_FRICTION)) {
                                    val px = (value - previous) * degreesPerPx() / camera.zoom
                                    camera.rotate(velocity.x / speed * px, velocity.y / speed * px)
                                    previous = value
                                }
                            }
                            camera.isGesturing = false
                        }
                } else if (dragging || transformed) {
                    camera.isGesturing = false
                }
            }
        }
    }
