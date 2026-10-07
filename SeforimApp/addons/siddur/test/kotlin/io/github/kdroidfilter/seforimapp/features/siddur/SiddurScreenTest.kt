package io.github.kdroidfilter.seforimapp.features.siddur

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforimapp.siddur.*
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The siddur as the app shows it: its screen's titles, table of contents, links and saved state. */
class SiddurScreenTest {
    private val ashkenaz = SiddurPrefs(Nusach.ASHKENAZ, inIsrael = true, minyan = true)

    private fun text(
        date: String,
        part: SiddurPart,
        prefs: SiddurPrefs = ashkenaz,
        options: Set<String> = emptySet(),
    ): String =
        SiddurTemplates
            .render(prefs.nusach, part, siddurFlags(LocalDate.parse(date), part, prefs, options))
            .joinToString("\n") { it.html.filterNot { c -> c == MARK_START || c == MARK_END } }

    // The sources of the halachot link into the library with zayit:// deep links, each one parsed by the app
    @Test
    fun sourceLinks() {
        // The templates, in the SeforimSiddur package's jar (or its classes, built beside)
        val uri = javaClass.getResource("/siddur")!!.toURI()
        val root =
            if (uri.scheme == "jar") {
                java.nio.file.FileSystems
                    .newFileSystem(uri, emptyMap<String, Any>())
                    .getPath("/siddur")
            } else {
                java.nio.file.Path
                    .of(uri)
            }
        val links =
            java.nio.file.Files
                .walk(root)
                .toList()
                .filter { it.toString().endsWith(".txt") }
                .flatMap {
                    Regex("""<a href="([^"]+)">""")
                        .findAll(
                            java.nio.file.Files
                                .readString(it),
                        ).map { m -> m.groupValues[1] }
                }
        assertTrue(links.size > 100, "only ${links.size} links")
        for (link in links) {
            val destination =
                io.github.kdroidfilter.seforimapp.core.deeplink
                    .parseZayitDeepLink(link)
            assertTrue(destination is io.github.kdroidfilter.seforim.tabs.TabsDestination.BookContent && destination.lineId != null, link)
        }
        // Rendered, a link keeps its text and becomes a link annotation
        val opened = mutableListOf<String>()
        val text =
            htmlWithLinks(
                """<small>אמרו (<a href="zayit://book/1/line/2">שו"ע או"ח נ, א</a>) כך</small>""",
                20f,
                androidx.compose.ui.graphics.Color.Red,
                open = { opened += it },
            )
        assertEquals("אמרו (שו\"ע או\"ח נ, א) כך", text.text)
        assertEquals(1, text.getLinkAnnotations(0, text.length).size)
    }

    // A siddur tab's state goes through the session file (ProtoBuf), and an older file without it still reads
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Test
    fun persistedState() {
        val proto = kotlinx.serialization.protobuf.ProtoBuf
        val state =
            io.github.kdroidfilter.seforimapp.framework.session.TabPersistedState(
                siddur =
                    io.github.kdroidfilter.seforimapp.framework.session.SiddurPersistedState(
                        epochDay = 20000,
                        part = "MINCHA",
                        scrollIndex = 42,
                        showDiacritics = false,
                    ),
            )
        val serializer =
            io.github.kdroidfilter.seforimapp.framework.session.TabPersistedState
                .serializer()
        assertEquals(state, proto.decodeFromByteArray(serializer, proto.encodeToByteArray(serializer, state)))
        val old =
            io.github.kdroidfilter.seforimapp.framework.session
                .TabPersistedState()
        assertEquals(null, proto.decodeFromByteArray(serializer, proto.encodeToByteArray(serializer, old)).siddur)
    }

    // Every part has a title on screen (PART_TITLES), or the chooser fails
    @Test
    fun everyPartHasATitle() {
        assertEquals(SiddurPart.entries.toSet(), PART_TITLES.keys)
    }

    // The day's book shown: a tefila per root of its table of contents, its headings under it
    @Test
    fun bookToc() {
        val friday = hebrew(5787, HebrewMonth.CHESHVAN, 5)
        val names = dayBookParts(friday, ashkenaz).map { it.first }
        val items = bookItems(buildDayBook(friday, ashkenaz, emptySet(), withSkipped = false))
        val toc = bookToc(items) { it.part.name }
        // Friday night's meals are Shabbat's: their blessings again, for the night
        val meals = listOf(SiddurPart.BIRKAT_HAMAZON, SiddurPart.MEEIN_SHALOSH)
        assertEquals(names.flatMap { if (it in meals) listOf(it, it) else listOf(it) }.map { it.name }, toc.roots.map { it.text })
        assertTrue(toc.children[toc.roots.first().id].orEmpty().isNotEmpty())
        assertTrue(toc.roots.first().hasChildren)
    }

    private fun hebrew(
        year: Int,
        month: HebrewMonth,
        day: Int,
    ): LocalDate = JewishCalendar(year, month, day).gregorianLocalDate
}
