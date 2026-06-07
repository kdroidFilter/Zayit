package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.annotations.NoteStore
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.delete_note
import seforimapp.seforimapp.generated.resources.no_notes_for_selection
import seforimapp.seforimapp.generated.resources.no_notes_saved
import seforimapp.seforimapp.generated.resources.note_body_placeholder
import seforimapp.seforimapp.generated.resources.notes_info_word_level
import seforimapp.seforimapp.generated.resources.notes_pane
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.jetbrains.jewel.ui.component.ContextMenuRepresentation as JewelContextMenuRepresentation
import org.jetbrains.jewel.ui.component.TextContextMenu as JewelTextContextMenu

/**
 * A pending (not-yet-persisted) note anchored to a resolved character range. Created from the
 * "add note" context-menu action, or implicitly as a blank whole-line note when the selected line
 * carries none yet.
 *
 * @property quote The anchored passage, shown as a greyed preview above the editor.
 */
data class NoteDraftAnchor(
    val lineId: Long,
    val startOffset: Int,
    val endOffset: Int,
    val quote: String,
)

/** Compact "dd.MM.yy · HH:mm" stamp shown in each note's footer. Locale-neutral on purpose. */
private val NOTE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy · HH:mm")

private fun formatNoteTime(millis: Long): String = NOTE_TIME_FORMATTER.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/**
 * Side pane showing the notes of the currently selected line(s) — same scoping as the commentaries
 * pane (a TOC selection or a multi-selection surfaces all their notes). Notes live in [noteStore].
 * Editing auto-saves (debounced); a trash button deletes. The draft editor keeps editing the note
 * it creates in place (no focus jump), and its trash removes that note and clears the field.
 *
 * Visuals follow the IntelliJ tool-window idiom: each note is a bordered card with a leading accent
 * stripe (VCS-annotate style), a quoted-passage header, and a footer carrying the last-edit stamp
 * plus a hover-revealed delete action. The list is ordered oldest-first; the draft sits at the end.
 */
@Composable
fun NotesPanel(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    bookId: Long,
    noteStore: NoteStore,
    selectedLineIds: Set<Long>,
    primarySelectedLine: Line?,
    draft: NoteDraftAnchor?,
    onConsumeDraft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paneHoverSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val currentOnEvent by rememberUpdatedState(onEvent)
    val focusManager = LocalFocusManager.current

    // Idempotent: keeps the pane self-contained instead of relying on BookContentView's load.
    LaunchedEffect(bookId, noteStore) { noteStore.loadBook(bookId) }

    val notesByBook by noteStore.notesByBook.collectAsState()
    val allNotes =
        remember(notesByBook, bookId, selectedLineIds) {
            notesByBook[bookId]
                .orEmpty()
                .filter { it.lineId in selectedLineIds }
                // Oldest first: [id] is AUTOINCREMENT, so it grows in insertion order.
                .sortedBy { it.id }
        }

    val primaryLineId = primarySelectedLine?.id

    // The note the draft editor has created. It is excluded from the list and kept inside the draft
    // editor so editing it does not tear down/recreate the field (which would drop focus). Reset
    // when the line or the explicit draft changes.
    var draftCreatedId by remember(primaryLineId, draft) { mutableStateOf<Long?>(null) }

    val notes = remember(allNotes, draftCreatedId) { allNotes.filter { it.id != draftCreatedId } }

    // With no explicit draft and no saved note (other than the one being drafted), offer a blank
    // whole-line note on the primary selected line, ready to edit. Never persisted until typed.
    val autoDraft =
        remember(draft, notes, primarySelectedLine) {
            if (draft != null || notes.isNotEmpty() || primarySelectedLine == null) {
                null
            } else {
                val plain = buildAnnotatedFromHtml(primarySelectedLine.content, baseTextSize = 16f, boldScale = 1f).text
                if (plain.isEmpty()) null else NoteDraftAnchor(primarySelectedLine.id, 0, plain.length, plain.trim())
            }
        }
    val effectiveDraft = draft ?: autoDraft
    val isExplicitDraft = draft != null

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .hoverable(paneHoverSource)
                // A tap anywhere in the pane outside the field clears focus, which flushes the
                // pending note via the editor's focus-loss save.
                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
    ) {
        PaneHeader(
            label = stringResource(Res.string.notes_pane),
            interactionSource = paneHoverSource,
            onHide = { onEvent(BookContentEvent.ToggleNotes) },
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (notes.isEmpty() && effectiveDraft == null) {
                NotesEmptyState()
            } else {
                val listState =
                    rememberLazyListState(
                        initialFirstVisibleItemIndex = uiState.notes.scrollIndex,
                        initialFirstVisibleItemScrollOffset = uiState.notes.scrollOffset,
                    )
                LaunchedEffect(listState) {
                    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                        .distinctUntilChanged()
                        .catch { }
                        .collect { (index, offset) -> currentOnEvent(BookContentEvent.NotesScrolled(index, offset)) }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            quote = note.quote.takeIf { it.isNotBlank() },
                            accentStrong = false,
                        ) { hovered ->
                            NoteEditor(
                                initialText = note.note,
                                timestampMillis = note.updatedAt,
                                autoFocus = false,
                                clearOnDelete = false,
                                cardHovered = hovered,
                                onPersist = { text ->
                                    scope.launch {
                                        // A blank body deletes the note (NoteStore.updateNote contract).
                                        noteStore.updateNote(bookId, note.id, text, System.currentTimeMillis())
                                    }
                                },
                                onDelete = { scope.launch { noteStore.removeNote(bookId, note.id) } },
                            )
                        }
                    }
                    // Draft editor last: the new (most recent) note belongs after the existing ones,
                    // which are listed oldest-first. A brighter stripe marks it as the live draft.
                    if (effectiveDraft != null) {
                        item(
                            key =
                                "draft-${effectiveDraft.lineId}-${effectiveDraft.startOffset}-${effectiveDraft.endOffset}",
                        ) {
                            NoteCard(
                                quote = effectiveDraft.quote.takeIf { it.isNotBlank() },
                                accentStrong = true,
                            ) { hovered ->
                                NoteEditor(
                                    initialText = "",
                                    timestampMillis = null,
                                    autoFocus = isExplicitDraft,
                                    clearOnDelete = true,
                                    cardHovered = hovered,
                                    onPersist = { text ->
                                        scope.launch {
                                            val id = draftCreatedId
                                            val now = System.currentTimeMillis()
                                            when {
                                                text.isBlank() && id != null -> {
                                                    noteStore.removeNote(bookId, id)
                                                    draftCreatedId = null
                                                }
                                                text.isNotBlank() && id == null -> {
                                                    draftCreatedId =
                                                        noteStore.addNote(
                                                            bookId = bookId,
                                                            lineId = effectiveDraft.lineId,
                                                            startOffset = effectiveDraft.startOffset,
                                                            endOffset = effectiveDraft.endOffset,
                                                            note = text,
                                                            timestamp = now,
                                                            quote = effectiveDraft.quote,
                                                        )
                                                }
                                                text.isNotBlank() && id != null -> {
                                                    noteStore.updateNote(bookId, id, text, now)
                                                }
                                            }
                                        }
                                    },
                                    onDelete = {
                                        scope.launch {
                                            draftCreatedId?.let { noteStore.removeNote(bookId, it) }
                                            draftCreatedId = null
                                        }
                                        if (isExplicitDraft) onConsumeDraft()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Hint shown while drafting the first note of a line. Skipped when no line is selected
        // (effectiveDraft == null) — the centered "no selection" message already covers that, so
        // the two empty-state messages never stack.
        if (allNotes.isEmpty() && effectiveDraft != null) {
            Text(
                text = stringResource(Res.string.no_notes_saved),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
            InlineInformationBanner(
                text = stringResource(Res.string.notes_info_word_level),
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
        }
    }
}

/** Centered empty state: muted annotate glyph above the "no notes for selection" hint. */
@Composable
private fun NotesEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                key = AllIconsKeys.Actions.Annotate,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint =
                    JewelTheme.globalColors.text.info
                        .copy(alpha = 0.55f),
            )
            Text(
                text = stringResource(Res.string.no_notes_for_selection),
                textAlign = TextAlign.Center,
                color = JewelTheme.globalColors.text.info,
            )
        }
    }
}

/**
 * Bordered note card with a leading accent stripe (drawn on the start edge — the right side under
 * RTL). The whole card reacts to hover: brighter background, accent-tinted border, stronger stripe.
 * [accentStrong] paints the stripe at full accent (used to flag the live draft). [content] receives
 * the current hover state so the editor can reveal its delete action only while hovered.
 */
@Composable
private fun NoteCard(
    quote: String?,
    accentStrong: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (hovered: Boolean) -> Unit,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused

    val stripeColor =
        when {
            accentStrong -> accent
            hovered -> accent.copy(alpha = 0.7f)
            else -> accent.copy(alpha = 0.4f)
        }
    val cardBackground =
        if (hovered) {
            JewelTheme.globalColors.panelBackground
        } else {
            JewelTheme.globalColors.panelBackground.copy(alpha = 0.55f)
        }
    val borderColor =
        if (hovered || accentStrong) {
            accent.copy(alpha = 0.5f)
        } else {
            JewelTheme.globalColors.borders.normal
        }

    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(shape)
                .background(cardBackground)
                .border(1.dp, borderColor, shape)
                .hoverable(hoverSource)
                .drawBehind {
                    val stripeWidth = 3.dp.toPx()
                    val verticalInset = 8.dp.toPx()
                    val edgeInset = 5.dp.toPx()
                    // Stripe hugs the start edge: right under RTL, left under LTR.
                    val x =
                        if (layoutDirection == LayoutDirection.Rtl) {
                            size.width - edgeInset - stripeWidth
                        } else {
                            edgeInset
                        }
                    drawRoundRect(
                        color = stripeColor,
                        topLeft = Offset(x, verticalInset),
                        size = Size(stripeWidth, (size.height - 2 * verticalInset).coerceAtLeast(0f)),
                        cornerRadius = CornerRadius(stripeWidth / 2, stripeWidth / 2),
                    )
                }
                // Reserve the stripe gutter on the start side (right under RTL).
                .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
    ) {
        if (quote != null) {
            Text(
                text = quote,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.info,
                textAlign = TextAlign.Justify,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )
        }
        content(hovered)
    }
}

/**
 * Auto-saving note editor: a multi-line field plus a footer carrying the last-edit stamp and a
 * hover-revealed trash button. Text changes are debounced and pushed to [onPersist] (no explicit
 * save). [onDelete] removes the note (or the draft's created note); when [clearOnDelete] is true the
 * field is also emptied so the draft editor stays in place. [timestampMillis] (when > 0) renders the
 * footer stamp; [cardHovered] gates the trash button's visibility.
 */
@OptIn(FlowPreview::class, ExperimentalFoundationApi::class)
@Composable
private fun NoteEditor(
    initialText: String,
    timestampMillis: Long?,
    autoFocus: Boolean,
    clearOnDelete: Boolean,
    cardHovered: Boolean,
    onPersist: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val state = rememberTextFieldState(initialText)
    val focusRequester = remember { FocusRequester() }
    val currentOnPersist by rememberUpdatedState(onPersist)
    var hadFocus by remember { mutableStateOf(false) }
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { state.text.toString() }
            .drop(1) // skip the initial value; only react to edits
            .debounce(500)
            .distinctUntilChanged()
            .catch { }
            .collect { currentOnPersist(it) }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        // Restore Jewel's default text context menu (copy/cut/paste, with icons and shortcuts):
        // inside the editor the book-content menu (highlight, add-note…) is irrelevant. Both the
        // items and the representation are reset, since the screen overrides both.
        CompositionLocalProvider(
            LocalTextContextMenu provides JewelTextContextMenu,
            LocalContextMenuRepresentation provides JewelContextMenuRepresentation,
        ) {
            TextArea(
                state = state,
                placeholder = { Text(stringResource(Res.string.note_body_placeholder)) },
                // A bounded max height is required inside the LazyColumn (infinite incoming height).
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp, max = 220.dp)
                        .focusRequester(focusRequester)
                        // Persist immediately when the field loses focus (clicking outside it),
                        // instead of waiting for the debounce.
                        .onFocusChanged { focusState ->
                            if (hadFocus && !focusState.isFocused) {
                                currentOnPersist(state.text.toString())
                            }
                            hadFocus = focusState.isFocused
                        },
            )
        }

        // Fixed-height footer so revealing the trash on hover never reflows the card.
        Row(
            modifier = Modifier.fillMaxWidth().height(26.dp).padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val stamp = timestampMillis?.takeIf { it > 0L }
            if (stamp != null) {
                Text(
                    text = formatNoteTime(stamp),
                    fontSize = 11.sp,
                    color =
                        JewelTheme.globalColors.text.info
                            .copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            val canDelete = state.text.isNotBlank()
            if (cardHovered && canDelete) {
                IconActionButton(
                    key = AllIconsKeys.General.Delete,
                    onClick = {
                        if (clearOnDelete) state.clearText()
                        onDelete()
                    },
                    contentDescription = stringResource(Res.string.delete_note),
                )
            }
        }
    }
}
