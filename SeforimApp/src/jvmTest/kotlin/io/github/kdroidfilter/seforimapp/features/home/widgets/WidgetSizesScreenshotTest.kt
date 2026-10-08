package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.createGraph
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.testAppSettings
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders every widget at every size the grid lets it take, and measures the widest empty band across each card: a
 * widget must never be resizable to a size it can't fill. Every size and the report go to build/screenshots/sizes. The Filament widgets (the Earth, the sky, the solar system) need a real window and fill
 * their card with their view; they're left out. So is the dictionary: it reads its books on first use, and the tests have
 * no library DB, so its card holds its title and field over the room its words would take.
 */
@OptIn(ExperimentalTestApi::class)
class WidgetSizesScreenshotTest {
    private val graph by lazy {
        FileKit.init("seforimapp-tests")
        createGraph<AppGraph>().also(::seed)
    }

    /** Enough history, favorites and notes in the tests' own user DB for their widgets to fill every size. */
    private fun seed(graph: AppGraph) =
        runBlocking {
            val now = System.currentTimeMillis()
            repeat(40) { i ->
                if (i % 4 == 3) {
                    graph.historyStore.recordSearchVisit(SEARCHES[i % SEARCHES.size], SearchScope.Global, false, null, now - i * HOUR)
                } else {
                    graph.historyStore.recordBookVisit(1000L + i, TITLES[i % TITLES.size], now - i * HOUR)
                }
            }
            val folder =
                graph.favoritesStore
                    .folders()
                    .firstOrNull()
                    ?.id ?: graph.favoritesStore.createFolder("תיקייה", now)
            repeat(40) { i ->
                graph.favoritesStore.add(2000L + i, TITLES[i % TITLES.size], now - i * HOUR, folderId = folder.takeIf { i % 3 == 0 })
            }
            graph.noteStore.recent(100).forEach { graph.noteStore.removeNote(it.bookId, it.note.id) }
            repeat(20) { i ->
                graph.noteStore.addNote(3000L + i, 1L, 0, 5, NOTES[i % NOTES.size], now - i * HOUR, quote = QUOTES[i % QUOTES.size])
            }
        }

    private val pitch = CellPitch(MAX_GRID_WIDTH)
    private val out =
        File("build/screenshots/sizes").apply {
            deleteRecursively()
            mkdirs()
        }

    @Test
    fun `no widget size leaves its card half empty`() {
        val faults = mutableListOf<String>()
        val report = StringBuilder()
        for (widget in availableHomeWidgets.filterNot { it.id in FILAMENT_WIDGETS || it.id in LIBRARY_DB_WIDGETS }) {
            for (w in widget.minSpan.w..minOf(widget.maxSpan.w, HOME_GRID_COLUMNS)) {
                val minRows = widget.minRows(w, pitch)
                for (h in minRows..maxOf(widget.maxSpan.h, minRows)) {
                    val image = shot(widget, w, h)
                    val (rows, columns) = emptyBands(image)
                    val line = "${widget.id} ${w}x$h: empty band ${rows}dp high, ${columns}dp wide"
                    report.appendLine(line)
                    ImageIO.write(image, "png", File(out, "${widget.id}-${w}x$h.png"))
                    val maxEmptyHeight = if (widget.id == "zmanim") ZMANIM_CARD_ROWS_GAP else MAX_EMPTY_HEIGHT
                    if (rows > maxEmptyHeight || columns > MAX_EMPTY_WIDTH) {
                        faults += line
                    }
                }
            }
        }
        File(out, "report.txt").writeText(report.toString())
        assertTrue(faults.isEmpty(), faults.joinToString("\n"))
    }

    private fun shot(
        widget: HomeWidget,
        w: Int,
        h: Int,
    ): BufferedImage {
        var image: BufferedImage? = null
        val width = pitch.width(w)
        val height = maxOf(pitch.height(h), widget.heightAt(width) ?: 0.dp)
        runDesktopComposeUiTest(width = width.value.toInt(), height = height.value.toInt()) {
            setContent {
                IntUiTheme(isDark = true) {
                    CompositionLocalProvider(
                        LocalAppGraph provides graph,
                        LocalTabSelected provides false,
                        LocalLayoutDirection provides LayoutDirection.Rtl,
                    ) {
                        val state = HomeWidgetsState(HomeUserLocation.preview, Community.SEPHARADE, HomeWidgetsLayout(testAppSettings()))
                        Box(Modifier.background(JewelTheme.globalColors.panelBackground)) {
                            val modifier = Modifier.size(width, height)
                            widget.Content(state, modifier)
                        }
                    }
                }
            }
            waitForIdle()
            // ponytail: the lists read their stores on Dispatchers.IO, which waitForIdle doesn't wait for; a pause
            // lets them land. A wait on the store's result if it ever proves too short
            Thread.sleep(STORE_READ_MS)
            waitForIdle()
            image = onRoot().captureToImage().toAwtImage()
        }
        return image!!
    }

    /** The highest run of rows, and the widest run of columns, with nothing drawn on them, in dp, inside the frame. */
    private fun emptyBands(image: BufferedImage): Pair<Int, Int> {
        val inset = FRAME_INSET
        val xs = inset until image.width - inset
        val ys = inset until image.height - inset

        fun luminance(
            x: Int,
            y: Int,
        ): Int {
            val rgb = image.getRGB(x, y)
            return ((rgb shr 16 and 0xFF) * 3 + (rgb shr 8 and 0xFF) * 6 + (rgb and 0xFF)) / 10
        }

        fun flat(values: Sequence<Int>): Boolean {
            var min = 255
            var max = 0
            for (v in values) {
                min = minOf(min, v)
                max = maxOf(max, v)
            }
            return max - min < FLAT_RANGE
        }

        fun longestRun(flags: List<Boolean>): Int =
            flags
                .fold(0 to 0) { (best, run), empty ->
                    if (empty) {
                        maxOf(best, run + 1) to run + 1
                    } else {
                        best to
                            0
                    }
                }.first
        val rows = longestRun(ys.map { y -> flat(xs.asSequence().map { x -> luminance(x, y) }) })
        val columns = longestRun(xs.map { x -> flat(ys.asSequence().map { y -> luminance(x, y) }) })
        return rows to columns
    }

    private companion object {
        val FILAMENT_WIDGETS = setOf("earth", "sky", "solar_system")
        val LIBRARY_DB_WIDGETS = setOf("dictionary")

        const val HOUR = 3_600_000L
        const val STORE_READ_MS = 150L

        // As long as real entries: a book, its part and its chapter
        val TITLES =
            listOf(
                "שולחן ערוך, אורח חיים, סימן קכח",
                "משנה ברורה, סימן רסג",
                "ברכות, דף כו.",
                "רמב״ם, הלכות תפילה, פרק ד",
                "בראשית, פרק כח",
                "מסילת ישרים, פרק יט",
            )
        val SEARCHES = listOf("תפילת הדרך", "קריאת שמע על המטה", "ברכת הלבנה")
        val NOTES = listOf("לעיין בשיטת הרמב״ם כאן", "השווה למשנה ברורה ס״ק ב", "קושיא: למה לא הביא את הירושלמי?")
        val QUOTES = listOf("והוא רחום יכפר עון", "מאימתי קורין את שמע בערבין", "ויצא יעקב מבאר שבע")

        /** The frame, its rounded corners and the cards' own padding: not content, never counted. */
        const val FRAME_INSET = 16

        /** Below this luminance spread a line holds no text, only backgrounds. */
        const val FLAT_RANGE = 28

        // Wider than a gap between two lines of text or two columns of it
        const val MAX_EMPTY_HEIGHT = 40

        // The zmanim are rows of cards: the gap between two rows and the cards' padding read as one empty band
        const val ZMANIM_CARD_ROWS_GAP = 56
        const val MAX_EMPTY_WIDTH = 120
    }
}
