package io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import dev.nucleusframework.application.LocalNucleusApplicationScope
import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.window.BasicTitleBar
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.LocalWindowChromeInsets
import dev.nucleusframework.window.TitleBarPlacement
import dev.nucleusframework.window.WindowBackground
import dev.nucleusframework.window.WindowControls
import dev.nucleusframework.window.WindowScaffold
import dev.nucleusframework.window.jewel.JewelDecoratedWindow
import dev.nucleusframework.window.newFullscreenControls
import dev.nucleusframework.window.styling.LocalTitleBarStyle
import dev.nucleusframework.window.styling.TitleBarStyle
import dev.nucleusframework.window.utils.linux.rememberLinuxButtonLayout
import dev.nucleusframework.window.windowDragArea
import io.github.kdroidfilter.seforimapp.core.e2e.E2ePerf
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.earthwidget.LocalWidgetAntiAliasing
import io.github.kdroidfilter.seforimapp.earthwidget.SolarSystemWidgetView
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.LocalFollowsPlay
import io.github.kdroidfilter.seforimapp.features.home.widgets.earth.EarthWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.features.home.widgets.sky.SkyWidget
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_solar_system_title
import java.awt.Cursor
import kotlin.math.roundToInt

/**
 * Height of the title bar band: the traffic lights are centred in it, so it matches the widget header's centre in a
 * full window (10 dp padding + a 28 dp row, both ×1.4 text scale → ~34 dp).
 */
private val OVERLAY_BAR_HEIGHT = 68.dp

// The Earth and sky cards' default frames, in dp
private const val CORNER_CARD_WIDTH = 340f
private const val EARTH_CARD_HEIGHT = 300f
private const val SKY_CARD_HEIGHT = 215f
private const val CARD_GAP = 12f
private const val MIN_CARD_WIDTH = 200f
private const val MIN_CARD_HEIGHT = 140f
private val CARD_SHAPE = RoundedCornerShape(18.dp)

/**
 * The Home solar system widget alone in its own maximised window, with the Earth and sky widgets stacked in a corner —
 * all three on the Home widgets' [state], so a date picked on one moves the others.
 */
@Composable
internal fun SolarSystemWindow(
    state: HomeWidgetsState,
    onClose: () -> Unit,
) {
    val title = stringResource(Res.string.home_solar_system_title)
    val windowState =
        remember {
            WindowState(
                placement = WindowPlacement.Maximized,
                position = WindowPosition(Alignment.Center),
                size = DpSize(1200.dp, 720.dp),
            )
        }
    with(LocalNucleusApplicationScope.current) {
        JewelDecoratedWindow(
            onCloseRequest = onClose,
            title = title,
            state = windowState,
            minimumSize = SolarSystemWidget.toolWindowMinSize,
        ) {
            // No title bar chrome: the scene fills the whole window and the widget's own header sits in the title bar
            // band — an empty, click-through overlay whose height centres the traffic lights on that header. The
            // header row drags the window; black behind, so a live resize never flashes white.
            WindowBackground(Color.Black)
            val baseBarStyle = LocalTitleBarStyle.current
            // Invisible bar (the scene shows through) kept only for the window controls — also in macOS fullscreen,
            // where newFullscreenControls brings the traffic lights back
            val transparentBarStyle =
                remember(baseBarStyle) {
                    baseBarStyle.copy(
                        colors =
                            baseBarStyle.colors.copy(
                                background = Color.Transparent,
                                inactiveBackground = Color.Transparent,
                                border = Color.Transparent,
                            ),
                        metrics = baseBarStyle.metrics.copy(height = OVERLAY_BAR_HEIGHT),
                    )
                }
            // Footprint of the window controls, so the header sits beside them (fullscreen included)
            var controlsPadding by remember { mutableStateOf(PaddingValues(0.dp)) }
            WindowScaffold(
                titleBar = {
                    if (Platform.Current == Platform.MacOS) {
                        // AppKit traffic lights, reserved by controlsInsets
                        BasicTitleBar(
                            modifier = Modifier.newFullscreenControls(),
                            style = transparentBarStyle,
                            controlButtonsDirection = ControlButtonsDirection.SystemNative,
                        )
                    } else {
                        OverlayWindowControls(style = transparentBarStyle, onPadding = { controlsPadding = it })
                    }
                },
                titleBarPlacement = TitleBarPlacement.Overlay(autoHideInFullscreen = false, passThroughToContent = true),
                // Same side as the main window's traffic lights
                controlButtonsDirection = ControlButtonsDirection.SystemNative,
            ) { _ ->
                // A window of its own: always "selected", so the Filament views keep rendering
                CompositionLocalProvider(
                    LocalTabSelected provides true,
                    LocalFollowsPlay provides true,
                    LocalWidgetAntiAliasing provides solarSystemOptions(state).antiAliasing,
                ) {
                    // In the window itself, not a dialog: in macOS fullscreen a dialog would open on another Space
                    var optionsShown by remember { mutableStateOf(false) }
                    // The one clock, run here while the window shows the play; closed, it stops where it is
                    val daysPerSecond by rememberUpdatedState(solarSystemOptions(state).daysPerSecond)
                    // Keyed on the state too: the Home may build a new one while the window is open
                    LaunchedEffect(state, state.playing) { if (state.playing) state.runPlayClock { daysPerSecond } }
                    DisposableEffect(state) { onDispose { state.stopPlay() } }
                    E2ePerf.Record()
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        SolarSystemWidgetView(
                            modifier = Modifier.fillMaxSize(),
                            date = state.selectedDate,
                            timeMillis = state.skyTimeMillis,
                            inIsrael = state.userInIsrael,
                            fullWindow = true,
                            onDateSelect = state::selectDate,
                            // The window's chrome, its date picker included, is dark
                            accentColor = rememberAccentColor(isDark = true),
                            onOptions = { optionsShown = !optionsShown },
                            playMillis = state.playingMillis,
                            onPlayToggle = state::togglePlay,
                            timeZone = state.location.timeZone,
                            spinSecondsPerTurn = solarSystemOptions(state).spinSecondsPerTurn,
                            kiddushLevanaEarliestOpinion = state.kiddushLevanaEarliest,
                            kiddushLevanaLatestOpinion = state.kiddushLevanaLatest,
                            // Beside the window controls, not below them. controlsInsets is start/end in the controls'
                            // own direction — read it as LTR to get the physical side, which the RTL page would flip
                            chromePadding =
                                if (Platform.Current == Platform.MacOS) {
                                    LocalWindowChromeInsets.current.controlsInsets.let {
                                        PaddingValues.Absolute(
                                            left = it.calculateLeftPadding(LayoutDirection.Ltr),
                                            right = it.calculateRightPadding(LayoutDirection.Ltr),
                                        )
                                    }
                                } else {
                                    controlsPadding
                                },
                            headerModifier = Modifier.windowDragArea(),
                        )
                        val options = solarSystemOptions(state)
                        // Below the header band, clear of the window's edges
                        BoxWithConstraints(
                            Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = OVERLAY_BAR_HEIGHT, bottom = 16.dp),
                        ) {
                            val bounds = DpSize(maxWidth, maxHeight)
                            // Default: stacked on the top end corner (the left in the app's RTL)
                            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                            val defaultX = if (rtl) 0f else maxWidth.value - CORNER_CARD_WIDTH
                            if (options.showEarth) {
                                FloatingCard(
                                    saved = options.earth,
                                    default = CardFrame(defaultX, 0f, CORNER_CARD_WIDTH, EARTH_CARD_HEIGHT),
                                    bounds = bounds,
                                    // Its offset is from the left: anchored there, not on the start (the right in RTL)
                                    modifier = Modifier.align(AbsoluteAlignment.TopLeft),
                                    onSave = { state.setSolarSystemOptions(solarOptionsNow(state).copy(earth = it)) },
                                ) { EarthWidget.Content(state, Modifier.fillMaxSize()) }
                            }
                            if (options.showSky) {
                                FloatingCard(
                                    saved = options.sky,
                                    default =
                                        CardFrame(
                                            defaultX,
                                            if (options.showEarth) EARTH_CARD_HEIGHT + CARD_GAP else 0f,
                                            CORNER_CARD_WIDTH,
                                            SKY_CARD_HEIGHT,
                                        ),
                                    bounds = bounds,
                                    // Its offset is from the left: anchored there, not on the start (the right in RTL)
                                    modifier = Modifier.align(AbsoluteAlignment.TopLeft),
                                    onSave = { state.setSolarSystemOptions(solarOptionsNow(state).copy(sky = it)) },
                                ) { SkyWidget.Content(state, Modifier.fillMaxSize()) }
                            }
                        }
                        if (options.showFps) {
                            FpsCounter(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
                        }
                        if (optionsShown) OptionsPanel(state, onDismiss = { optionsShown = false })
                    }
                }
            }
        }
    }
}

/**
 * The Linux / Windows window controls alone in the title bar band, on their native side, reporting their footprint as
 * an absolute padding through [onPadding] — the Nucleus insets reserve nothing for controls drawn in Compose.
 */
@Composable
private fun DecoratedWindowScope.OverlayWindowControls(
    style: TitleBarStyle,
    onPadding: (PaddingValues) -> Unit,
) {
    val onRight =
        if (Platform.Current == Platform.Linux) {
            rememberLinuxButtonLayout().controlsOnRight
        } else {
            ControlButtonsDirection.SystemNative.resolve() == LayoutDirection.Ltr
        }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalTitleBarStyle provides style) {
        Box(Modifier.fillMaxWidth().height(OVERLAY_BAR_HEIGHT)) {
            WindowControls(
                modifier =
                    Modifier
                        .align(if (onRight) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft)
                        .onSizeChanged { size ->
                            val width = with(density) { size.width.toDp() }
                            onPadding(if (onRight) PaddingValues.Absolute(right = width) else PaddingValues.Absolute(left = width))
                        },
                direction = ControlButtonsDirection.SystemNative,
            )
        }
    }
}

/** The options as saved right now, outside composition: a drag ends after its composition read them. */
private fun solarOptionsNow(state: HomeWidgetsState) = SolarSystemOptions.decode(state.layout.options.value[SolarSystemWidget.id])

/** Inside [bounds], no smaller than the minimum. */
private fun CardFrame.clampedTo(bounds: DpSize): CardFrame {
    val maxW = bounds.width.value
    val maxH = bounds.height.value
    val w = w.coerceIn(MIN_CARD_WIDTH, maxOf(MIN_CARD_WIDTH, maxW))
    val h = h.coerceIn(MIN_CARD_HEIGHT, maxOf(MIN_CARD_HEIGHT, maxH))
    return CardFrame(x.coerceIn(0f, maxOf(0f, maxW - w)), y.coerceIn(0f, maxOf(0f, maxH - h)), w, h)
}

/**
 * A widget card floating over the scene at [saved] (or [default]): its handle on top moves it, its grip on the bottom
 * corner facing the window's middle resizes it; both shown on hover, the frame saved when the drag ends.
 */
@Composable
private fun FloatingCard(
    saved: CardFrame?,
    default: CardFrame,
    bounds: DpSize,
    onSave: (CardFrame) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // The frame while dragging, until the saved one catches up
    var live by remember { mutableStateOf<CardFrame?>(null) }
    LaunchedEffect(saved) { live = null }
    val frame = (live ?: saved ?: default).clampedTo(bounds)
    val current by rememberUpdatedState(frame)
    val currentBounds by rememberUpdatedState(bounds)
    val currentOnSave by rememberUpdatedState(onSave)
    // The grip sits on the side facing the middle: resizing grows the card inward
    // Held for the length of a drag: crossing the middle mid-resize mustn't flip the side it grows from
    var heldGripSide by remember { mutableStateOf<Boolean?>(null) }
    val gripOnLeft = heldGripSide ?: (frame.x + frame.w / 2 > bounds.width.value / 2)
    val currentGripOnLeft by rememberUpdatedState(gripOnLeft)
    val currentSaved by rememberUpdatedState(saved)

    // Compose maps both ends of each drag step into the handle's current place, so moving it along stays exact
    @Composable
    fun Modifier.dragging(update: CardFrame.(dx: Float, dy: Float) -> CardFrame): Modifier {
        val currentUpdate by rememberUpdatedState(update)
        return pointerInput(Unit) {
            // The scope's own density: it follows the window onto a screen of another scale
            val scope = this

            fun end() {
                heldGripSide = null
                live?.let(currentOnSave)
                // Ended where it already was saved: no new value to clear it (and hide the chrome) when it comes back
                if (live == currentSaved) live = null
            }
            detectDragGestures(
                onDragStart = {
                    live = current
                    heldGripSide = currentGripOnLeft
                },
                onDragEnd = ::end,
                onDragCancel = ::end,
            ) { change, amount ->
                change.consume()
                live = (live ?: current).currentUpdate(amount.x / scope.density, amount.y / scope.density).clampedTo(currentBounds)
            }
        }
    }

    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val chromeAlpha by animateFloatAsState(if (hovered || live != null) 1f else 0f, label = "cardChrome")
    Box(
        modifier
            .absoluteOffset(frame.x.dp, frame.y.dp)
            .size(frame.w.dp, frame.h.dp)
            .shadow(24.dp, CARD_SHAPE, ambientColor = Color.Black, spotColor = Color.Black)
            .hoverable(hover),
    ) {
        content()
        // Move handle: a pill on the top edge
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(72.dp, 16.dp)
                .alpha(chromeAlpha)
                // ponytail: the window bridge has no move cursor (MOVE_CURSOR crashes it natively): the hand
                .pointerHoverIcon(PointerIcon.Hand)
                .dragging { dx, dy -> copy(x = x + dx, y = y + dy) },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(36.dp, 5.dp).background(Color.White.copy(alpha = 0.55f), CircleShape))
        }
        // Resize grip: a quarter ring in the bottom corner
        // NE/NW as the Home's widget frames: the window bridge crashes on SW/SE (same arrows anyway)
        val gripCursor = if (gripOnLeft) Cursor.NE_RESIZE_CURSOR else Cursor.NW_RESIZE_CURSOR
        Box(
            Modifier
                .align(if (gripOnLeft) AbsoluteAlignment.BottomLeft else AbsoluteAlignment.BottomRight)
                .size(22.dp)
                .alpha(chromeAlpha)
                .pointerHoverIcon(PointerIcon(Cursor(gripCursor)))
                .dragging { dx, dy ->
                    // Only the dragged edges move: grown up to the window's edge, never pushing the card back
                    val h = (h + dy).coerceAtMost(currentBounds.height.value - y)
                    if (gripOnLeft) {
                        // The right edge stays put: no further left than the window, no narrower than the minimum
                        val d = dx.coerceIn(-x, w - MIN_CARD_WIDTH)
                        copy(x = x + d, w = w - d, h = h)
                    } else {
                        copy(w = (w + dx).coerceAtMost(currentBounds.width.value - x), h = h)
                    }
                }.drawBehind {
                    val stroke = 2.dp.toPx()
                    val r = size.minDimension * 0.9f
                    drawArc(
                        color = Color.White.copy(alpha = 0.6f),
                        startAngle = if (gripOnLeft) 90f else 0f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(if (gripOnLeft) size.width * 0.3f else size.width * 0.7f - r, size.height * 0.7f - r),
                        size = Size(r, r),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                },
        )
    }
}

/** The widget's options over the scene, under the header on the end side; a click outside closes them. */
@Composable
private fun OptionsPanel(
    state: HomeWidgetsState,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = OVERLAY_BAR_HEIGHT, end = 16.dp)
                .width(320.dp)
                .shadow(24.dp, RoundedCornerShape(12.dp))
                .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(12.dp))
                .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(12.dp))
                // Taps inside stay inside
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
        ) {
            SolarSystemOptionsPage(state)
        }
    }
}

/** Frames drawn a second, refreshed twice a second. It draws a frame each vsync itself while shown: a debug readout. */
@Composable
private fun FpsCounter(modifier: Modifier = Modifier) {
    var fps by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var windowStart = 0L
        var frames = 0
        while (true) {
            withFrameNanos { now ->
                if (windowStart == 0L) windowStart = now
                frames++
                if (now - windowStart >= 500_000_000L) {
                    fps = (frames * 1e9 / (now - windowStart)).roundToInt()
                    windowStart = now
                    frames = 0
                }
            }
        }
    }
    Text(
        "$fps FPS",
        color = Color.White.copy(alpha = 0.85f),
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
