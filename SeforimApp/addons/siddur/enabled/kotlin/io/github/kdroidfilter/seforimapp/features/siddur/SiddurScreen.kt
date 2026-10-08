package io.github.kdroidfilter.seforimapp.features.siddur

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforimapp.core.deeplink.parseZayitDeepLink
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBar
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBarPosition
import io.github.kdroidfilter.seforimapp.core.presentation.components.rememberAppTextZoom
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetLocation
import io.github.kdroidfilter.seforimapp.earthwidget.KiddushLevanaEarliestOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.KiddushLevanaLatestOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.ZmanimOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.computeZmanimTimes
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.DiacriticsButton
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.SafeSelectionContainer
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.ZoomButtons
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.LineItemVerticalPaddingPerSide
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.booktoc.BookTocView
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.PaneCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeUserLocationViewModel
import io.github.kdroidfilter.seforimapp.features.home.widgets.luach.hebrewFormatter
import io.github.kdroidfilter.seforimapp.features.home.widgets.luach.hebrewWeekday
import io.github.kdroidfilter.seforimapp.features.home.widgets.luach.moladInfo
import io.github.kdroidfilter.seforimapp.features.home.widgets.luach.rememberOpenInLibrary
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.session.SiddurPersistedState
import io.github.kdroidfilter.seforimapp.icons.TableOfContents
import io.github.kdroidfilter.seforimapp.luach.LuachPopup
import io.github.kdroidfilter.seforimapp.siddur.*
import io.github.kdroidfilter.seforimapp.siddur.pirkeiAvos
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.siddur_at_night
import seforimapp.seforimapp.generated.resources.siddur_birkat_hachodesh
import seforimapp.seforimapp.generated.resources.siddur_chanuka_candle
import seforimapp.seforimapp.generated.resources.siddur_chanuka_candles
import seforimapp.seforimapp.generated.resources.siddur_hl_al_hanisim
import seforimapp.seforimapp.generated.resources.siddur_hl_aneinu
import seforimapp.seforimapp.generated.resources.siddur_hl_avinu
import seforimapp.seforimapp.generated.resources.siddur_hl_ayt
import seforimapp.seforimapp.generated.resources.siddur_hl_chatzi_hallel
import seforimapp.seforimapp.generated.resources.siddur_hl_hallel_shalem
import seforimapp.seforimapp.generated.resources.siddur_hl_havdala
import seforimapp.seforimapp.generated.resources.siddur_hl_ledavid
import seforimapp.seforimapp.generated.resources.siddur_hl_mashiv
import seforimapp.seforimapp.generated.resources.siddur_hl_morid
import seforimapp.seforimapp.generated.resources.siddur_hl_musaf
import seforimapp.seforimapp.generated.resources.siddur_hl_no_lamenatzeach
import seforimapp.seforimapp.generated.resources.siddur_hl_no_mizmor_letoda
import seforimapp.seforimapp.generated.resources.siddur_hl_no_tachanun
import seforimapp.seforimapp.generated.resources.siddur_hl_omer
import seforimapp.seforimapp.generated.resources.siddur_hl_retzeh
import seforimapp.seforimapp.generated.resources.siddur_hl_tachanun
import seforimapp.seforimapp.generated.resources.siddur_hl_tal_umatar
import seforimapp.seforimapp.generated.resources.siddur_hl_torah
import seforimapp.seforimapp.generated.resources.siddur_hl_vihi_noam
import seforimapp.seforimapp.generated.resources.siddur_hl_vten_bracha
import seforimapp.seforimapp.generated.resources.siddur_hl_yaale
import seforimapp.seforimapp.generated.resources.siddur_levana_open
import seforimapp.seforimapp.generated.resources.siddur_levana_window
import seforimapp.seforimapp.generated.resources.siddur_minyan
import seforimapp.seforimapp.generated.resources.siddur_next_day
import seforimapp.seforimapp.generated.resources.siddur_night_of
import seforimapp.seforimapp.generated.resources.siddur_nusach_ashkenaz
import seforimapp.seforimapp.generated.resources.siddur_nusach_edot
import seforimapp.seforimapp.generated.resources.siddur_nusach_missing
import seforimapp.seforimapp.generated.resources.siddur_nusach_sefard
import seforimapp.seforimapp.generated.resources.siddur_occasion_pesach
import seforimapp.seforimapp.generated.resources.siddur_occasion_rosh_hashana
import seforimapp.seforimapp.generated.resources.siddur_occasion_shabbat_and
import seforimapp.seforimapp.generated.resources.siddur_occasion_shavuot
import seforimapp.seforimapp.generated.resources.siddur_occasion_shmini_atzeret
import seforimapp.seforimapp.generated.resources.siddur_occasion_shmini_atzeret_simchat_torah
import seforimapp.seforimapp.generated.resources.siddur_occasion_shvii_shel_pesach
import seforimapp.seforimapp.generated.resources.siddur_occasion_simchat_torah
import seforimapp.seforimapp.generated.resources.siddur_occasion_sukkot
import seforimapp.seforimapp.generated.resources.siddur_occasion_yom_kippur
import seforimapp.seforimapp.generated.resources.siddur_omer_sefira
import seforimapp.seforimapp.generated.resources.siddur_option_beit_avel
import seforimapp.seforimapp.generated.resources.siddur_option_first_lulav
import seforimapp.seforimapp.generated.resources.siddur_option_fruit
import seforimapp.seforimapp.generated.resources.siddur_option_mezonot
import seforimapp.seforimapp.generated.resources.siddur_option_seudat_mila
import seforimapp.seforimapp.generated.resources.siddur_option_wine
import seforimapp.seforimapp.generated.resources.siddur_option_zimun
import seforimapp.seforimapp.generated.resources.siddur_option_zimun_10
import seforimapp.seforimapp.generated.resources.siddur_part_birkat_hamazon
import seforimapp.seforimapp.generated.resources.siddur_part_havdala
import seforimapp.seforimapp.generated.resources.siddur_part_kabbalat_shabbat
import seforimapp.seforimapp.generated.resources.siddur_part_kiddush_levana
import seforimapp.seforimapp.generated.resources.siddur_part_kriat_shema_al_hamita
import seforimapp.seforimapp.generated.resources.siddur_part_leil_shabbat
import seforimapp.seforimapp.generated.resources.siddur_part_leil_yom_tov
import seforimapp.seforimapp.generated.resources.siddur_part_maariv
import seforimapp.seforimapp.generated.resources.siddur_part_maariv_shabbat
import seforimapp.seforimapp.generated.resources.siddur_part_maariv_yom_tov
import seforimapp.seforimapp.generated.resources.siddur_part_meein_shalosh
import seforimapp.seforimapp.generated.resources.siddur_part_mincha
import seforimapp.seforimapp.generated.resources.siddur_part_mincha_shabbat
import seforimapp.seforimapp.generated.resources.siddur_part_mincha_yom_tov
import seforimapp.seforimapp.generated.resources.siddur_part_neila
import seforimapp.seforimapp.generated.resources.siddur_part_nerot_chanuka
import seforimapp.seforimapp.generated.resources.siddur_part_of
import seforimapp.seforimapp.generated.resources.siddur_part_seudat_shabbat
import seforimapp.seforimapp.generated.resources.siddur_part_seudat_yom_tov
import seforimapp.seforimapp.generated.resources.siddur_part_sfirat_haomer
import seforimapp.seforimapp.generated.resources.siddur_part_shacharit
import seforimapp.seforimapp.generated.resources.siddur_part_shacharit_shabbat
import seforimapp.seforimapp.generated.resources.siddur_part_shacharit_yom_tov
import seforimapp.seforimapp.generated.resources.siddur_part_tefilat_haderech
import seforimapp.seforimapp.generated.resources.siddur_previous_day
import seforimapp.seforimapp.generated.resources.siddur_show_all
import seforimapp.seforimapp.generated.resources.siddur_title
import seforimapp.seforimapp.generated.resources.table_of_contents
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

internal val PART_TITLES =
    mapOf(
        SiddurPart.SHACHARIT to Res.string.siddur_part_shacharit,
        SiddurPart.MINCHA to Res.string.siddur_part_mincha,
        SiddurPart.MAARIV to Res.string.siddur_part_maariv,
        SiddurPart.SFIRAT_HAOMER to Res.string.siddur_part_sfirat_haomer,
        SiddurPart.BIRKAT_HAMAZON to Res.string.siddur_part_birkat_hamazon,
        SiddurPart.MEEIN_SHALOSH to Res.string.siddur_part_meein_shalosh,
        SiddurPart.TEFILAT_HADERECH to Res.string.siddur_part_tefilat_haderech,
        SiddurPart.KRIAT_SHEMA_AL_HAMITA to Res.string.siddur_part_kriat_shema_al_hamita,
        SiddurPart.KIDDUSH_LEVANA to Res.string.siddur_part_kiddush_levana,
        SiddurPart.NEROT_CHANUKA to Res.string.siddur_part_nerot_chanuka,
        SiddurPart.KABBALAT_SHABBAT to Res.string.siddur_part_kabbalat_shabbat,
        SiddurPart.MAARIV_SHABBAT to Res.string.siddur_part_maariv_shabbat,
        SiddurPart.LEIL_SHABBAT to Res.string.siddur_part_leil_shabbat,
        SiddurPart.SHACHARIT_SHABBAT to Res.string.siddur_part_shacharit_shabbat,
        SiddurPart.SEUDAT_SHABBAT to Res.string.siddur_part_seudat_shabbat,
        SiddurPart.MINCHA_SHABBAT to Res.string.siddur_part_mincha_shabbat,
        SiddurPart.HAVDALA to Res.string.siddur_part_havdala,
        SiddurPart.MAARIV_YOM_TOV to Res.string.siddur_part_maariv_yom_tov,
        SiddurPart.LEIL_YOM_TOV to Res.string.siddur_part_leil_yom_tov,
        SiddurPart.SHACHARIT_YOM_TOV to Res.string.siddur_part_shacharit_yom_tov,
        SiddurPart.SEUDAT_YOM_TOV to Res.string.siddur_part_seudat_yom_tov,
        SiddurPart.MINCHA_YOM_TOV to Res.string.siddur_part_mincha_yom_tov,
        SiddurPart.NEILA to Res.string.siddur_part_neila,
    )

private val OPTION_TITLES =
    mapOf(
        "zimun" to Res.string.siddur_option_zimun,
        "zimun_10" to Res.string.siddur_option_zimun_10,
        "mezonot" to Res.string.siddur_option_mezonot,
        "wine" to Res.string.siddur_option_wine,
        "fruit" to Res.string.siddur_option_fruit,
        "first_lulav" to Res.string.siddur_option_first_lulav,
        "seudat_mila" to Res.string.siddur_option_seudat_mila,
    )

private val NUSACH_TITLES =
    listOf(
        Nusach.ASHKENAZ to Res.string.siddur_nusach_ashkenaz,
        Nusach.SEFARD to Res.string.siddur_nusach_sefard,
        Nusach.EDOT_HAMIZRACH to Res.string.siddur_nusach_edot,
    )

/** The nusach picked in the siddur, or the one of the user's community. */
internal fun nusachOf(
    code: String?,
    community: String?,
): Nusach =
    code?.let { c -> Nusach.entries.firstOrNull { it.name == c } }
        ?: when (community?.let { runCatching { Community.valueOf(it) }.getOrNull() }) {
            Community.SEFARD -> Nusach.SEFARD
            Community.SEPHARADE -> Nusach.EDOT_HAMIZRACH
            else -> Nusach.ASHKENAZ
        }

/**
 * The smart siddur: the day's tefilot as one book, for the user's nusach and place, with only what is said that day
 * (the day's additions marked), its table of contents unfolding each tefila, opened on the tefila of the hour.
 */
@OptIn(FlowPreview::class)
@Composable
fun SiddurTabContent(
    tabId: String,
    initialPart: String?,
    initialEpochDay: Long? = null,
    initialHeading: String? = null,
) {
    val appGraph = LocalAppGraph.current
    val appSettings = appGraph.appSettings
    val title = stringResource(Res.string.siddur_title)
    LaunchedEffect(tabId, title) { appGraph.tabTitleUpdateManager.updateTabTitle(tabId, title, TabType.SIDDUR) }

    val locationViewModel: HomeUserLocationViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val user by locationViewModel.state.collectAsState()
    val location =
        remember(user) {
            EarthWidgetLocation(
                latitude = user.userPlace.lat,
                longitude = user.userPlace.lng,
                elevationMeters = user.userPlace.elevation,
                timeZone = user.userPlace.timeZone,
            )
        }
    val communityCode by appSettings.userCommunityCodeFlow.collectAsState()
    val sephardi = communityCode == Community.SEPHARADE.name
    val nusachCode by appSettings.siddurNusachFlow.collectAsState()
    val minyan by appSettings.siddurMinyanFlow.collectAsState()
    val beitAvel by appSettings.siddurBeitAvelFlow.collectAsState()
    val nusach = nusachOf(nusachCode, communityCode)
    val available = SiddurTemplates.exists(nusach, SiddurPart.SHACHARIT.template)
    val shownNusach = if (available) nusach else Nusach.ASHKENAZ
    val prefs =
        SiddurPrefs(
            nusach = shownNusach,
            inIsrael = user.inIsrael,
            minyan = minyan,
            beitAvel = beitAvel,
            // ponytail: Jerusalem is the walled city the cities list has; add one when it lists another
            mukafChoma = user.inIsrael && user.userCityLabel == "ירושלים",
        )

    // The tefila of the hour, and the civil day whose book holds it (tonight's Maariv is in today's)
    // The minute, so that today and the tefila of the hour follow the clock in a tab left open
    val minute by produceState(System.currentTimeMillis() / 60_000) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = System.currentTimeMillis() / 60_000
        }
    }
    val ofTheHour =
        remember(location, user.inIsrael, sephardi, prefs, minute) {
            val today = java.time.LocalDate.now(location.timeZone.toZoneId())
            val opinion = if (sephardi) ZmanimOpinion.OHR_HACHAIM else ZmanimOpinion.ITIM_LABINA
            val zmanim = computeZmanimTimes(today, location, opinion, user.inIsrael)
            val (day, part) =
                siddurOfTheHour(
                    Clock.System.now(),
                    today.toKotlinLocalDate(),
                    zmanim.alosHashachar.instant(),
                    zmanim.chatzosHayom.instant(),
                    zmanim.sunset.instant(),
                )
            val dayPart = dayPartFor(day, part, prefs)
            civilDayOf(day, dayPart) to dayPart
        }

    // The tab's state, kept with the session: restored after a restart
    val store = appGraph.tabPersistedStateStore
    val saved = remember(tabId) { store.get(tabId)?.siddur }
    val askedPart = initialPart?.let { p -> SiddurPart.entries.firstOrNull { it.name == p } }
    var epochDay by remember {
        mutableStateOf(
            saved?.epochDay
                ?: initialEpochDay?.let { d -> civilDayOf(LocalDate.fromEpochDays(d), askedPart ?: SiddurPart.SHACHARIT).toEpochDays() }
                ?: ofTheHour.first.toEpochDays(),
        )
    }
    var showAll by remember { mutableStateOf(saved?.showAll ?: false) }
    var optionsSaved by remember { mutableStateOf(saved?.options.orEmpty()) }
    var tocVisible by remember { mutableStateOf(saved?.tocVisible ?: true) }
    var showDiacritics by remember { mutableStateOf(saved?.showDiacritics ?: true) }
    var expanded by remember {
        mutableStateOf(
            saved
                ?.tocExpanded
                .orEmpty()
                .split(',')
                .filter { it.isNotEmpty() }
                .toSet(),
        )
    }
    val civil = LocalDate.fromEpochDays(epochDay)
    val options = optionsSaved.split(',').filter { it.isNotEmpty() }.toSet()

    // The book with the day it is of: a scroll waits for the day asked for, not the one still shown
    val loaded by produceState<Pair<LocalDate, List<BookSection>>?>(null, civil, prefs, optionsSaved, showAll) {
        value = civil to withContext(Dispatchers.Default) { buildDayBook(civil, prefs, options, showAll) }
    }
    val book = loaded?.second
    val items = remember(book) { bookItems(book.orEmpty()) }

    val accent = rememberAccentColor(JewelTheme.isDark)
    val listState = rememberLazyListState(saved?.scrollIndex ?: 0, saved?.scrollOffset ?: 0)
    val scope = rememberCoroutineScope()
    // Where to open: a restored tab where it was read; else the part (and heading) asked for, or the tefila of the hour
    var target by remember {
        mutableStateOf<Pair<SiddurPart?, String?>?>(
            if (saved !=
                null
            ) {
                null
            } else {
                (askedPart ?: ofTheHour.second.takeIf { epochDay == ofTheHour.first.toEpochDays() }) to initialHeading
            },
        )
    }
    LaunchedEffect(items, target) {
        val (part, heading) = target ?: return@LaunchedEffect
        if (items.isEmpty() || loaded?.first != civil) return@LaunchedEffect
        val partStart =
            part?.let { p -> items.indexOfFirst { it is BookItem.Title && it.section.part.familyOf() == p.familyOf() } }?.takeIf { it >= 0 }
                ?: 0
        val index =
            heading?.let { h ->
                items
                    .withIndex()
                    .firstOrNull { (i, item) ->
                        i >= partStart &&
                            item is BookItem.Text &&
                            item.line.kind == SiddurLine.Kind.HEADING &&
                            h in item.line.html
                    }?.index
            } ?: partStart
        listState.scrollToItem(index)
        target = null
    }
    // Saved as it changes, the scroll once it rests
    LaunchedEffect(tabId) {
        snapshotFlow {
            SiddurPersistedState(
                epochDay = epochDay,
                showAll = showAll,
                options = optionsSaved,
                scrollIndex = listState.firstVisibleItemIndex,
                scrollOffset = listState.firstVisibleItemScrollOffset,
                tocVisible = tocVisible,
                showDiacritics = showDiacritics,
                tocExpanded = expanded.joinToString(","),
            )
        }.debounce(300).collect { state -> store.update(tabId) { it.copy(siddur = state) } }
    }

    val tabs = LocalOpenWindow.current.tabsViewModel
    val openLink: (String) -> Unit = remember(tabs) { { href -> parseZayitDeepLink(href)?.let(tabs::openTab) } }
    // Each tefila's title, a festival's with its day: "שחרית ומוסף · שבת וראש השנה"
    val titles = book.orEmpty().associateWith { sectionTitle(it) }
    val toc = remember(items, titles) { bookToc(items) { titles[it].orEmpty() } }
    // The heading being read: the last one at or above the top of the text
    val current by remember(toc) {
        derivedStateOf {
            toc.anchors.keys
                .lastOrNull { it <= listState.firstVisibleItemIndex }
                ?.let { toc.anchors[it] }
        }
    }
    CompositionLocalProvider(LocalSiddurLinks provides openLink) {
        Row(Modifier.fillMaxSize()) {
            SiddurStartBar(tocVisible = tocVisible, onToc = { tocVisible = !tocVisible })
            if (tocVisible) {
                Box(Modifier.width(260.dp).fillMaxHeight()) {
                    PaneCard {
                        Column(Modifier.fillMaxSize()) {
                            PaneHeader(label = stringResource(Res.string.table_of_contents), onHide = { tocVisible = false })
                            Box(Modifier.padding(horizontal = 8.dp)) {
                                BookTocView(
                                    tocEntries = toc.roots,
                                    expandedEntries = toc.entryIds(expanded),
                                    tocChildren = toc.children,
                                    scrollIndex = 0,
                                    scrollOffset = 0,
                                    onEntryClick = { entry -> toc.itemOf[entry.id]?.let { scope.launch { listState.scrollToItem(it) } } },
                                    onEntryExpand = { entry ->
                                        val key = toc.keyOf[entry.id] ?: return@BookTocView
                                        expanded = if (key in expanded) expanded - key else expanded + key
                                        toc.itemOf[entry.id]?.let { scope.launch { listState.scrollToItem(it) } }
                                    },
                                    onScroll = { _, _ -> },
                                    selectedTocEntryId = current,
                                )
                            }
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                PaneCard {
                    Column(Modifier.fillMaxSize()) {
                        BookHeader(
                            date = civil,
                            inIsrael = user.inIsrael,
                            today = ofTheHour.first,
                            onDay = { epochDay = it.toEpochDays() },
                            onToday = {
                                epochDay = ofTheHour.first.toEpochDays()
                                target = ofTheHour.second to null
                            },
                            nusach = nusach,
                            onNusach = { appSettings.setSiddurNusachCode(it.name) },
                            minyan = minyan,
                            onMinyan = appSettings::setSiddurMinyan,
                            beitAvel = beitAvel,
                            onBeitAvel = appSettings::setSiddurBeitAvel,
                            showAll = showAll,
                            onShowAll = { showAll = it },
                        )
                        if (!available) {
                            Text(
                                stringResource(Res.string.siddur_nusach_missing),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                color = JewelTheme.globalColors.text.info,
                            )
                        }
                        Divider(Orientation.Horizontal, Modifier.fillMaxWidth())
                        // What changes today in the tefila being read (משיב הרוח, ותן ברכה...), always in sight
                        val reading by remember(items) { derivedStateOf { items.getOrNull(listState.firstVisibleItemIndex)?.section } }
                        reading?.takeIf { highlights(it.flags, it.part).isNotEmpty() }?.let { section ->
                            Highlights(section.flags, section.part, accent, titles[section].orEmpty())
                            Divider(Orientation.Horizontal, Modifier.fillMaxWidth())
                        }
                        VerticallyScrollableContainer(listState as ScrollableState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                            SafeSelectionContainer(Modifier.fillMaxSize()) {
                                SiddurText(
                                    items = items,
                                    listState = listState,
                                    accent = accent,
                                    showDiacritics = showDiacritics,
                                    titles = titles,
                                    inIsrael = user.inIsrael,
                                    options = options,
                                    onOption = { part, option, on ->
                                        val key = "${part.name}:$option"
                                        optionsSaved = (if (on) options + key else options - key).joinToString(",")
                                    },
                                    slot = { section, name ->
                                        SiddurSlot(
                                            name,
                                            section.flags,
                                            section.date,
                                            section.part,
                                            user.inIsrael,
                                            prefs.mukafChoma,
                                            location,
                                            shownNusach,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            SiddurEndBar(showDiacritics = showDiacritics, onDiacritics = { showDiacritics = !showDiacritics })
        }
    }
}

/** What the book's column shows: a tefila's title, what changes in it today, its choices, and its lines. */
@Immutable
internal sealed interface BookItem {
    val section: BookSection

    data class Title(
        override val section: BookSection,
    ) : BookItem

    data class Options(
        override val section: BookSection,
    ) : BookItem

    data class Text(
        override val section: BookSection,
        val line: SiddurLine,
    ) : BookItem
}

internal fun bookItems(book: List<BookSection>): List<BookItem> =
    book.flatMap { section ->
        buildList {
            add(BookItem.Title(section))
            if (section.part.options.isNotEmpty()) add(BookItem.Options(section))
            // The book titles each tefila itself: the template's own opening title ("מנחה לימות החול") would repeat it
            val opening = section.lines.firstOrNull()?.takeIf { it.kind == SiddurLine.Kind.HEADING }
            section.lines.forEach { if (it !== opening) add(BookItem.Text(section, it)) }
        }
    }

/** A section's name in the saved table of contents: its part, and tonight's meal apart from the day's. */
private val BookSection.key get() = if (night && !part.night) "${part.name}/night" else part.name

/** The book's table of contents, as the book view's: a tefila per root, its headings nested by level. */
@Immutable
internal class BookToc(
    val roots: ImmutableList<TocEntry>,
    val children: Map<Long, List<TocEntry>>,
    /** The item each entry scrolls to. */
    val itemOf: Map<Long, Int>,
    /** The entry each item index starts (the last one at or above the top is the one being read). */
    val anchors: java.util.SortedMap<Int, Long>,
    /** A stable key of each expandable entry (the tefila and its heading), for the session. */
    val keyOf: Map<Long, String>,
) {
    fun entryIds(keys: Set<String>): Set<Long> = keyOf.filterValues { it in keys }.keys
}

internal fun bookToc(
    items: List<BookItem>,
    title: (BookSection) -> String,
): BookToc {
    val roots = mutableListOf<TocEntry>()
    val children = mutableMapOf<Long, MutableList<TocEntry>>()
    val itemOf = mutableMapOf<Long, Int>()
    val anchors = java.util.TreeMap<Int, Long>()
    val keyOf = mutableMapOf<Long, String>()
    // The open entries above the next heading, by level (the tefila at level 0)
    val stack = ArrayDeque<Pair<Int, Long>>()
    var nextId = 1L
    items.forEachIndexed { index, item ->
        when {
            item is BookItem.Title -> {
                val id = nextId++
                roots += TocEntry(id = id, bookId = 0, text = title(item.section), level = 0)
                itemOf[id] = index
                anchors[index] = id
                keyOf[id] = item.section.key
                stack.clear()
                stack.addLast(0 to id)
            }
            item is BookItem.Text && item.line.kind == SiddurLine.Kind.HEADING && item.line.headingLevel <= 5 -> {
                val level = item.line.headingLevel
                while (stack.size > 1 && stack.last().first >= level) stack.removeLast()
                val parent = stack.lastOrNull()?.second ?: return@forEachIndexed
                val id = nextId++
                val text =
                    item.line.html
                        .replace(TAGS, "")
                        .trim()
                children.getOrPut(parent) { mutableListOf() } +=
                    TocEntry(id = id, bookId = 0, parentId = parent, text = text, level = level)
                itemOf[id] = index
                anchors[index] = id
                keyOf[id] = "${item.section.key}/$text/$index"
                stack.addLast(level to id)
            }
        }
    }

    // The book view's tree marks parents and last children itself from these flags
    fun fix(list: List<TocEntry>) =
        list.mapIndexed { i, e -> e.copy(hasChildren = children[e.id]?.isNotEmpty() == true, isLastChild = i == list.lastIndex) }
    val fixedChildren = children.mapValues { (_, list) -> fix(list) }
    return BookToc(fix(roots).toImmutableList(), fixedChildren, itemOf, anchors, keyOf)
}

/** The siddur's bar at the start of the window, as a book's: its table of contents. */
@Composable
private fun SiddurStartBar(
    tocVisible: Boolean,
    onToc: () -> Unit,
) {
    VerticalLateralBar(
        position = VerticalLateralBarPosition.Start,
        topContent = {
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.table_of_contents),
                onClick = onToc,
                isSelected = tocVisible,
                icon = TableOfContents,
                iconDescription = stringResource(Res.string.table_of_contents),
                label = stringResource(Res.string.table_of_contents),
            )
        },
        bottomContent = {},
    )
}

/** The siddur's bar at the end of the window, as a book's: the text's size and its diacritics. */
@Composable
private fun SiddurEndBar(
    showDiacritics: Boolean,
    onDiacritics: () -> Unit,
) {
    VerticalLateralBar(
        position = VerticalLateralBarPosition.End,
        topContent = {
            ZoomButtons()
            DiacriticsButton(showDiacritics, onDiacritics)
        },
        bottomContent = {},
    )
}

private val TAGS = Regex("<[^>]+>")

/** The book's day, a line above its text: the day and its occasion, the luach, today, and the user's choices. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookHeader(
    date: LocalDate,
    inIsrael: Boolean,
    today: LocalDate,
    onDay: (LocalDate) -> Unit,
    onToday: () -> Unit,
    nusach: Nusach,
    onNusach: (Nusach) -> Unit,
    minyan: Boolean,
    onMinyan: (Boolean) -> Unit,
    beitAvel: Boolean,
    onBeitAvel: (Boolean) -> Unit,
    showAll: Boolean,
    onShowAll: (Boolean) -> Unit,
) {
    val day = remember(date, inIsrael) { JewishCalendar(date, inIsrael) }
    val weekday = if (date.dayOfWeek == DayOfWeek.SATURDAY) "שבת" else "יום ${date.toJavaLocalDate().hebrewWeekday()}"
    val dayLabel = "$weekday, ${hebrewFormatter.format(day)}"
    val occasion =
        remember(day) {
            listOf(hebrewFormatter.formatYomTov(day), hebrewFormatter.formatRoshChodesh(day))
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · ")
        }
    // On a narrow pane the choices wrap under the day instead of squeezing their labels
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        DayNavigator(date, inIsrael, today, onDay, onToday, dayLabel, occasion)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val nusachLabels = NUSACH_TITLES.map { stringResource(it.second) }
            ListComboBox(
                items = nusachLabels,
                selectedIndex = NUSACH_TITLES.indexOfFirst { it.first == nusach },
                onSelectedItemChange = { onNusach(NUSACH_TITLES[it].first) },
                modifier = Modifier.width(140.dp),
            )
            CheckboxRow(text = stringResource(Res.string.siddur_minyan), checked = minyan, onCheckedChange = onMinyan)
            CheckboxRow(text = stringResource(Res.string.siddur_option_beit_avel), checked = beitAvel, onCheckedChange = onBeitAvel)
            CheckboxRow(text = stringResource(Res.string.siddur_show_all), checked = showAll, onCheckedChange = onShowAll)
        }
    }
}

/** The previous day, the day itself (a click opens the luach), and the next day. */
@Composable
private fun DayNavigator(
    date: LocalDate,
    inIsrael: Boolean,
    today: LocalDate,
    onDay: (LocalDate) -> Unit,
    onToday: () -> Unit,
    dayLabel: String,
    occasion: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // In a right-to-left page the previous day is on the right
        IconButton(onClick = { onDay(date.minus(1, DateTimeUnit.DAY)) }) {
            Icon(AllIconsKeys.General.ChevronRight, contentDescription = stringResource(Res.string.siddur_previous_day))
        }
        // The day: a click opens the luach to pick another, or come back to today
        var picking by remember { mutableStateOf(false) }
        Box {
            Text(
                if (occasion.isEmpty()) dayLabel else "$dayLabel · $occasion",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { picking = !picking }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            if (picking) {
                LuachPopup(
                    selected = date.toJavaLocalDate(),
                    today = today.toJavaLocalDate(),
                    inIsrael = inIsrael,
                    accent = rememberAccentColor(JewelTheme.isDark),
                    onSelect = { onDay(it.toKotlinLocalDate()) },
                    onToday = onToday,
                    onDismiss = { picking = false },
                )
            }
        }
        IconButton(onClick = { onDay(date.plus(1, DateTimeUnit.DAY)) }) {
            Icon(AllIconsKeys.General.ChevronLeft, contentDescription = stringResource(Res.string.siddur_next_day))
        }
    }
}

/** The moon's window, as the nusach blesses it ([levanaAfterSevenDays]), in the earth widget's terms. */
internal fun levanaOpinions(nusach: Nusach) =
    if (!nusach.levanaAfterSevenDays) {
        KiddushLevanaEarliestOpinion.DAYS_3 to KiddushLevanaLatestOpinion.BETWEEN_MOLDOS
    } else {
        KiddushLevanaEarliestOpinion.DAYS_7 to KiddushLevanaLatestOpinion.DAYS_15
    }

/** What changes today in this part, at a glance. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Highlights(
    flags: Set<String>,
    part: SiddurPart,
    accent: Color,
    title: String,
) {
    val items = remember(flags, part) { highlights(flags, part) }
    if (items.isEmpty()) return
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontWeight = FontWeight.Bold)
        items.forEach { (label, on) ->
            Text(
                text = stringResource(label),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (on) {
                                accent.copy(alpha = 0.15f)
                            } else {
                                JewelTheme.globalColors.borders.normal
                                    .copy(alpha = 0.3f)
                            },
                        ).padding(horizontal = 9.dp, vertical = 3.dp),
                color = if (on) accent else JewelTheme.globalColors.text.info,
                fontSize = 12.sp,
            )
        }
    }
}

/** The day's changes: each label, and whether it adds (true) or leaves out (false). */
private fun highlights(
    flags: Set<String>,
    part: SiddurPart,
): List<Pair<StringResource, Boolean>> =
    buildList {
        fun on(
            flag: String,
            label: StringResource,
        ) {
            if (flag in flags) add(label to true)
        }
        val tefila = part == SiddurPart.SHACHARIT || part == SiddurPart.MINCHA || part == SiddurPart.MAARIV
        if (tefila) {
            on("aseret_yemei_teshuva", Res.string.siddur_hl_ayt)
            if ("mashiv_haruach" in flags) {
                add(Res.string.siddur_hl_mashiv to true)
            } else if ("israel" in flags || "ashkenaz" !in flags) {
                add(Res.string.siddur_hl_morid to true)
            }
            add(if ("tal_umatar" in flags) Res.string.siddur_hl_tal_umatar to true else Res.string.siddur_hl_vten_bracha to true)
            on("yaale_veyavo", Res.string.siddur_hl_yaale)
            on("al_hanisim", Res.string.siddur_hl_al_hanisim)
            if ("taanit" in flags && part != SiddurPart.MAARIV) add(Res.string.siddur_hl_aneinu to true)
            if (part == SiddurPart.MAARIV) {
                on("havdala", Res.string.siddur_hl_havdala)
                if ("motzaei_shabbat" in flags) on("vihi_noam", Res.string.siddur_hl_vihi_noam)
                on("omer", Res.string.siddur_hl_omer)
            } else {
                add(if ("tachanun" in flags) Res.string.siddur_hl_tachanun to true else Res.string.siddur_hl_no_tachanun to false)
                on("avinu_malkenu", Res.string.siddur_hl_avinu)
                on("torah_reading", Res.string.siddur_hl_torah)
            }
            if (part == SiddurPart.SHACHARIT) {
                on("hallel_shalem", Res.string.siddur_hl_hallel_shalem)
                on("chatzi_hallel", Res.string.siddur_hl_chatzi_hallel)
                on("musaf", Res.string.siddur_hl_musaf)
                if ("mizmor_letoda" !in flags) add(Res.string.siddur_hl_no_mizmor_letoda to false)
                if ("lamenatzeach" !in flags) add(Res.string.siddur_hl_no_lamenatzeach to false)
            }
            if (part != SiddurPart.MINCHA || "ashkenaz" !in flags) on("ledavid_ori", Res.string.siddur_hl_ledavid)
        } else if (part == SiddurPart.BIRKAT_HAMAZON || part == SiddurPart.MEEIN_SHALOSH) {
            on("sat", Res.string.siddur_hl_retzeh)
            on("yaale_veyavo", Res.string.siddur_hl_yaale)
            if (part == SiddurPart.BIRKAT_HAMAZON) on("al_hanisim", Res.string.siddur_hl_al_hanisim)
        }
    }

@Composable
private fun SiddurText(
    items: List<BookItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    accent: Color,
    showDiacritics: Boolean,
    titles: Map<BookSection, String>,
    inIsrael: Boolean,
    options: Set<String>,
    onOption: (SiddurPart, String, Boolean) -> Unit,
    slot: @Composable (BookSection, String) -> Unit,
) {
    val appSettings = LocalAppGraph.current.appSettings
    // As the book view: Ctrl/Cmd + wheel, a pinch or two fingers zoom the text
    val zoom = rememberAppTextZoom()
    val textSize = zoom.textSize
    val lineHeight by appSettings.lineHeightFlow.collectAsState()
    val fontCode by appSettings.bookFontCodeFlow.collectAsState()
    val fontFamily = FontCatalog.familyFor(fontCode)
    val boldScale = FontCatalog.boldScaleFor(fontCode)
    val style = LineStyle(textSize, lineHeight, fontFamily, boldScale, showDiacritics, accent)
    // The book view's column: the text across the pane, each line between its marking bar and the scrollbar
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().then(zoom.modifier).padding(end = 16.dp, bottom = 8.dp)) {
        itemsIndexed(items) { _, item ->
            val line = (item as? BookItem.Text)?.line
            Row(
                Modifier
                    .fillMaxWidth()
                    .alpha(if (line?.skipped == true) 0.35f else 1f)
                    .padding(horizontal = 8.dp)
                    .height(IntrinsicSize.Min),
            ) {
                // The day's additions, marked as a book marks its selected line
                Box(Modifier.width(4.dp).fillMaxHeight().background(if (line?.special == true) accent else Color.Transparent))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).padding(vertical = LineItemVerticalPaddingPerSide)) {
                    when (item) {
                        is BookItem.Title -> PartTitle(item.section, titles[item.section].orEmpty(), inIsrael, style)
                        is BookItem.Options -> PartOptions(item.section, options, onOption)
                        is BookItem.Text ->
                            when (item.line.kind) {
                                SiddurLine.Kind.HEADING -> Heading(item.line, style)
                                SiddurLine.Kind.NOTE -> Note(item.line.html, style)
                                SiddurLine.Kind.SLOT -> slot(item.section, item.line.html)
                                SiddurLine.Kind.TEXT -> Paragraph(item.line, style)
                            }
                    }
                }
            }
        }
    }
}

/** A tefila's name; a festival's with the day: its holiday, and Shabbat when it is one too. */
@Composable
private fun sectionTitle(section: BookSection): String {
    val base = stringResource(PART_TITLES.getValue(section.part))
    // A blessing of any hour, at tonight's meal
    if (section.night &&
        !section.part.night
    ) {
        return stringResource(Res.string.siddur_part_of, base, stringResource(Res.string.siddur_at_night))
    }
    if (!section.part.festival || section.part == SiddurPart.NEILA) return base
    val flags = section.flags
    val occasion =
        when {
            "yom_kippur" in flags -> Res.string.siddur_occasion_yom_kippur
            "rosh_hashana" in flags -> Res.string.siddur_occasion_rosh_hashana
            "shvii_shel_pesach" in flags -> Res.string.siddur_occasion_shvii_shel_pesach
            "pesach" in flags -> Res.string.siddur_occasion_pesach
            "shavuot" in flags -> Res.string.siddur_occasion_shavuot
            "sukkot" in flags -> Res.string.siddur_occasion_sukkot
            // In Israel one day is both; abroad the second is Simchat Torah
            "simchat_torah" in flags && "israel" in flags -> Res.string.siddur_occasion_shmini_atzeret_simchat_torah
            "simchat_torah" in flags -> Res.string.siddur_occasion_simchat_torah
            "shmini_atzeret" in flags -> Res.string.siddur_occasion_shmini_atzeret
            else -> return base
        }
    val day = stringResource(occasion)
    val full = if ("sat" in flags) stringResource(Res.string.siddur_occasion_shabbat_and, day) else day
    return stringResource(Res.string.siddur_part_of, base, full)
}

/** A tefila's title in the book: its name, and for the night's, the night it is said. */
@Composable
private fun PartTitle(
    section: BookSection,
    name: String,
    inIsrael: Boolean,
    style: LineStyle,
) {
    val night =
        if (section.night) {
            val day = remember(section.date, inIsrael) { JewishCalendar(section.date, inIsrael) }
            stringResource(Res.string.siddur_night_of, hebrewFormatter.format(day))
        } else {
            null
        }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            name,
            fontFamily = style.fontFamily,
            fontSize = (style.textSize * 1.5f).sp,
            fontWeight = FontWeight.Bold,
        )
        night?.let { Text(it, color = JewelTheme.globalColors.text.info, fontSize = (style.textSize * 0.7f).sp) }
    }
}

/** A tefila's choices (a zimun, what was eaten...), in the book above its text. */
@Composable
private fun PartOptions(
    section: BookSection,
    options: Set<String>,
    onOption: (SiddurPart, String, Boolean) -> Unit,
) {
    // The lulav's option only in Sukkot
    val shown = section.part.options.filter { it != "first_lulav" || "sukkot" in section.flags }
    if (shown.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        shown.forEach { option ->
            CheckboxRow(
                text = stringResource(OPTION_TITLES.getValue(option)),
                checked = "${section.part.name}:$option" in options,
                onCheckedChange = { on -> onOption(section.part, option, on) },
            )
        }
    }
}

/** How the book view shows its text, for the siddur's lines. */
private data class LineStyle(
    val textSize: Float,
    val lineHeight: Float,
    val fontFamily: FontFamily,
    val boldScale: Float,
    val showDiacritics: Boolean,
    val accent: Color,
) {
    fun html(html: String) = if (showDiacritics) html else HebrewTextUtils.removeAllDiacritics(html)
}

/** A heading, as the book view shows its headings: bold, a little larger the higher it is. */
@Composable
private fun Heading(
    line: SiddurLine,
    style: LineStyle,
) {
    val scale =
        when (line.headingLevel) {
            1, 2, 3 -> 1.25f
            4 -> 1.125f
            else -> 1f
        }
    Text(
        style.html(line.html.replace(TAGS, "")),
        modifier = Modifier.fillMaxWidth().padding(top = if (line.headingLevel <= 4) 4.dp else 0.dp),
        fontFamily = style.fontFamily,
        fontSize = (style.textSize * scale).sp,
        lineHeight = (style.textSize * scale * style.lineHeight).sp,
        fontWeight = FontWeight.Bold,
    )
}

/** An instruction of the smart siddur: smaller, on the accent's tint. */
@Composable
private fun Note(
    html: String,
    style: LineStyle,
) {
    val openLink = LocalSiddurLinks.current
    val annotated =
        remember(html, style) { htmlWithLinks(style.html(html), style.textSize * 0.8f, style.accent, openLink, style.boldScale) }
    Text(
        annotated,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(style.accent.copy(alpha = 0.1f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        fontSize = (style.textSize * 0.8f).sp,
        lineHeight = (style.textSize * 0.8f * style.lineHeight).sp,
        color = JewelTheme.globalColors.text.normal,
    )
}

/** A line of the tefila, as the book view's LineItem: justified, in the book font and spacing. */
@Composable
private fun Paragraph(
    line: SiddurLine,
    style: LineStyle,
) {
    val openLink = LocalSiddurLinks.current
    val annotated =
        remember(line.html, style) {
            htmlWithLinks(style.html(line.html), style.textSize, style.accent, openLink, style.boldScale)
                .marked(SpanStyle(color = style.accent, fontWeight = FontWeight.Bold))
        }
    Text(
        annotated,
        modifier = Modifier.fillMaxWidth(),
        fontFamily = style.fontFamily,
        fontSize = style.textSize.sp,
        lineHeight = (style.textSize * style.lineHeight).sp,
        textAlign = TextAlign.Justify,
    )
}

/** Opens a zayit:// link of the siddur's text (a halacha's source): set by the screen, for its tab's window. */
internal val LocalSiddurLinks = staticCompositionLocalOf<(String) -> Unit> { {} }

private const val LINK_START = '\uE002'
private const val LINK_END = '\uE003'
private val ANCHOR = Regex("""<a href="([^"]+)">(.*?)</a>""")

/**
 * [html] as text, its `<a href="zayit://...">` sources underlined in [accent] and opening with [open] (the HTML parser
 * has no links: they are marked around their text, then made links once it is laid out).
 */
internal fun htmlWithLinks(
    html: String,
    textSize: Float,
    accent: Color,
    open: (String) -> Unit,
    boldScale: Float = 1f,
): AnnotatedString {
    val hrefs = mutableListOf<String>()
    val marked =
        ANCHOR.replace(html) { m ->
            hrefs += m.groupValues[1]
            "$LINK_START${m.groupValues[2]}$LINK_END"
        }
    val source = buildAnnotatedFromHtml(marked, textSize, boldScale)
    if (hrefs.isEmpty()) return source
    return buildAnnotatedString {
        var linkFrom = -1
        var link = 0
        for (i in source.indices) {
            when (source.text[i]) {
                LINK_START -> linkFrom = length
                LINK_END -> {
                    val href = hrefs.getOrNull(link++)
                    if (linkFrom >= 0 && href != null) {
                        addLink(
                            LinkAnnotation.Clickable(
                                tag = href,
                                styles = TextLinkStyles(SpanStyle(color = accent, textDecoration = TextDecoration.Underline)),
                            ) { open(href) },
                            linkFrom,
                            length,
                        )
                    }
                    linkFrom = -1
                }
                else -> append(source.subSequence(i, i + 1))
            }
        }
    }
}

/** The text between [MARK_START] and [MARK_END] in [style], the marks dropped. */
internal fun AnnotatedString.marked(style: SpanStyle): AnnotatedString {
    val source = this
    if (source.text.none { it == MARK_START || it == MARK_END }) return source
    return buildAnnotatedString {
        var markFrom = -1
        var i = 0
        while (i < source.length) {
            when (source.text[i]) {
                MARK_START -> {
                    markFrom = length
                    i++
                }
                MARK_END -> {
                    if (markFrom >= 0) addStyle(style, markFrom, length)
                    markFrom = -1
                    i++
                }
                else -> {
                    val next = source.text.indexOfAny(charArrayOf(MARK_START, MARK_END), i).let { if (it < 0) source.length else it }
                    append(source.subSequence(i, next))
                    i = next
                }
            }
        }
    }
}

/** What a template's @slot shows for the day. */
@Composable
private fun SiddurSlot(
    name: String,
    flags: Set<String>,
    date: LocalDate,
    part: SiddurPart,
    inIsrael: Boolean,
    mukafChoma: Boolean,
    location: EarthWidgetLocation,
    nusach: Nusach,
) {
    val accent = rememberAccentColor(JewelTheme.isDark)
    val textSize by LocalAppGraph.current.appSettings.textSizeFlow
        .collectAsState()
    val tabs = LocalOpenWindow.current.tabsViewModel
    val open = rememberOpenInLibrary(openTab = { tabs.openTab(it) })
    val small = (textSize * 0.85f).sp

    @Composable
    fun Line(label: String) = SlotRow(accent) { Text(label, fontSize = small) }
    when (name) {
        // The day's readings, in full in the page
        "torah_reading", "shabbat_reading", "shabbat_mincha_reading", "haftara" -> {
            val mincha = part == SiddurPart.MINCHA || part == SiddurPart.MINCHA_SHABBAT || part == SiddurPart.MINCHA_YOM_TOV
            val (torah, haftara) =
                remember(date, inIsrael, mincha, nusach, part, mukafChoma) {
                    readingOf(
                        date,
                        inIsrael,
                        mincha,
                        sephardi = nusach == Nusach.EDOT_HAMIZRACH,
                        night = part.night,
                        mukafChoma = mukafChoma,
                    )
                }
            val reading = (if (name == "haftara") haftara else torah) ?: return
            ReadingBlock(reading, accent)
        }
        "megila" -> {
            val reading = remember(date, inIsrael, mukafChoma) { megilaOf(date, inIsrael, mukafChoma) } ?: return
            ReadingBlock(reading, accent)
        }
        "pirkei_avot" -> {
            val perakim = remember(date, inIsrael) { pirkeiAvos(date, inIsrael) } ?: return
            val reading =
                remember(perakim) {
                    Reading(
                        "פרקי אבות",
                        perakim.map { Passage("פרק ${hebrewFormatter.formatHebrewNumber(it)}", "משנה אבות", it to 1, it to 999) },
                    )
                }
            ReadingBlock(reading, accent)
        }
        "birkat_hachodesh" -> {
            val molad =
                remember(date, inIsrael) {
                    moladInfo(
                        Date(),
                        date.toJavaLocalDate(),
                        inIsrael,
                        KiddushLevanaEarliestOpinion.DAYS_3,
                        KiddushLevanaLatestOpinion.BETWEEN_MOLDOS,
                    )
                }
            val days =
                remember(date, inIsrael) {
                    comingRoshChodesh(date, inIsrael).joinToString(" ו") { d ->
                        if (d.dayOfWeek == DayOfWeek.SATURDAY) "שבת" else "יום ${d.toJavaLocalDate().hebrewWeekday()}"
                    }
                }
            Line(stringResource(Res.string.siddur_birkat_hachodesh, molad.weekday, molad.time, molad.chalakim, molad.month, days))
        }
        "omer_day" -> {
            val omer = flags.numbered("omer_") ?: return
            SlotRow(accent) {
                Text(stringResource(Res.string.siddur_omer_sefira, omer, omerSefira(omer)), fontSize = (textSize * 0.85f).sp)
            }
        }
        "chanuka_candles" -> {
            val candles = flags.numbered("chanuka_") ?: return
            SlotRow(accent) {
                Text(
                    if (candles == 1) {
                        stringResource(Res.string.siddur_chanuka_candle)
                    } else {
                        stringResource(Res.string.siddur_chanuka_candles, candles)
                    },
                    fontSize = (textSize * 0.85f).sp,
                )
            }
        }
        "kiddush_levana" -> {
            val now = remember { Date() }
            val molad =
                remember(date, inIsrael, nusach) {
                    val (earliest, latest) = levanaOpinions(nusach)
                    moladInfo(now = now, date = date.toJavaLocalDate(), inIsrael = inIsrael, earliest = earliest, latest = latest)
                }
            val format =
                remember(location) {
                    SimpleDateFormat("EEEE d/M HH:mm", Locale.forLanguageTag("he")).apply { timeZone = location.timeZone }
                }
            SlotRow(accent) {
                Column {
                    Text(
                        stringResource(
                            Res.string.siddur_levana_window,
                            format.format(molad.kiddushLevanaStart),
                            format.format(molad.kiddushLevanaEnd),
                        ),
                        fontSize = (textSize * 0.85f).sp,
                    )
                    if (now.after(molad.kiddushLevanaStart) && now.before(molad.kiddushLevanaEnd)) {
                        Text(stringResource(Res.string.siddur_levana_open), color = accent, fontSize = (textSize * 0.85f).sp)
                    }
                }
            }
        }
    }
}

/** A reading in full, verse by verse under its aliyot's names; its title folds it away. */
@Composable
private fun ReadingBlock(
    reading: Reading,
    accent: Color,
) {
    val appGraph = LocalAppGraph.current
    val textSize by appGraph.appSettings.textSizeFlow.collectAsState()
    val lineHeight by appGraph.appSettings.lineHeightFlow.collectAsState()
    val fontCode by appGraph.appSettings.bookFontCodeFlow.collectAsState()
    val fontFamily = FontCatalog.familyFor(fontCode)
    var open by remember(reading) { mutableStateOf(true) }
    val verses by produceState<List<Pair<String?, List<String>>>?>(null, reading) {
        value = withContext(Dispatchers.IO) { passagesText(appGraph.repository, reading.passages) }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            (if (open) "▾ " else "◂ ") + reading.title,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent.copy(alpha = 0.15f))
                    .clickable { open = !open }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        if (open) {
            verses?.forEach { (label, lines) ->
                label?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(top = 8.dp),
                        fontWeight = FontWeight.Bold,
                        color = JewelTheme.globalColors.text.info,
                    )
                }
                lines.forEach { html ->
                    val annotated = remember(html, textSize) { buildAnnotatedFromHtml(html, textSize) }
                    Text(
                        annotated,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        fontFamily = fontFamily,
                        fontSize = textSize.sp,
                        lineHeight = (textSize * lineHeight).sp,
                        textAlign = TextAlign.Justify,
                    )
                }
            }
        }
    }
}

/** N of the flag [prefix]N: the day of the Omer, the night of Chanuka. */
private fun Set<String>.numbered(prefix: String): Int? =
    firstNotNullOfOrNull { flag -> if (flag.startsWith(prefix)) flag.removePrefix(prefix).toIntOrNull() else null }

@Composable
private fun SlotRow(
    accent: Color,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(accent))
        Spacer(Modifier.width(10.dp))
        content()
    }
}

private val bookLines = java.util.concurrent.ConcurrentHashMap<String, List<io.github.kdroidfilter.seforimlibrary.core.models.Line>>()

/** The library's verses of each passage, with its heading: one book read once, kept for the next readings. */
internal suspend fun passagesText(
    repository: io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository,
    passages: List<Passage>,
): List<Pair<String?, List<String>>> =
    passages.map { passage ->
        val lines =
            bookLines[passage.book] ?: run {
                val book = repository.getBookByTitle(passage.book) ?: return@map passage.label to emptyList()
                repository.getLines(book.id, 0, book.totalLines).also { bookLines[passage.book] = it }
            }
        val from = passage.from.first * 1000 + passage.from.second
        val to = passage.to.first * 1000 + passage.to.second
        passage.label to
            lines.mapNotNull { line ->
                verseOf(line.heRef, passage.book)?.let { (c, v) -> line.content.takeIf { c * 1000 + v in from..to } }
            }
    }

/** A zman of the app's (a Date) as the siddur takes it. */
private fun Date?.instant(): Instant? = this?.let { Instant.fromEpochMilliseconds(it.time) }
