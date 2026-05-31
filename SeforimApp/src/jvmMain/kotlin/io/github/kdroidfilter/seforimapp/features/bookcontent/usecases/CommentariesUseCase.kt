package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorGroup
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorItem
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.LineConnectionsSnapshot
import io.github.kdroidfilter.seforimapp.pagination.CommentsForLineOrTocPagingSource
import io.github.kdroidfilter.seforimapp.pagination.LineTargumPagingSource
import io.github.kdroidfilter.seforimapp.pagination.MultiLineCommentsPagingSource
import io.github.kdroidfilter.seforimapp.pagination.MultiLineLinksPagingSource
import io.github.kdroidfilter.seforimapp.pagination.PagingDefaults
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.PubDate
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentarySummary
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min

/**
 * UseCase pour gérer les commentaires et liens
 */
private val YEAR_REGEX = Regex("""-?\d{3,4}""")
private const val MAX_BASE_LINES_PER_REQUEST = 128

class CommentariesUseCase(
    private val repository: SeforimRepository,
    private val stateManager: BookContentStateManager,
    private val scope: CoroutineScope,
) {
    private val commentatorBookCache: MutableMap<Long, Book> = ConcurrentHashMap()
    private val defaultTargumCache: MutableMap<Long, List<Long>> = ConcurrentHashMap()

    // Memoizes the cached pager flows per (kind, line(s), commentator) so the SAME cachedIn flow
    // is reused across composition teardown/rebuild (e.g. when switching tabs). Without this, each
    // rebuild builds a fresh cachedIn flow and reloads commentaries from the DB — the cause of the
    // visible delay before commentaries reappear on tab switch. Bounded (access-order LRU) so
    // visited-but-stale pagers don't accumulate unbounded heap.
    private val pagerFlowCache =
        object : LinkedHashMap<String, Flow<PagingData<CommentaryWithText>>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Flow<PagingData<CommentaryWithText>>>?): Boolean =
                size > MAX_CACHED_PAGERS
        }

    // Read-through cache of commentator GROUPS per base line. Survives tab switches (the use
    // case lives in viewModelScope), so returning to a tab no longer re-queries the DB for the
    // groups. Bounded (access-order LRU).
    private val lineConnectionsCache =
        object : LinkedHashMap<Long, LineConnectionsSnapshot>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, LineConnectionsSnapshot>?): Boolean =
                size > MAX_CACHED_LINE_CONNECTIONS
        }

    @Synchronized
    private fun cachedPager(
        key: String,
        create: () -> Flow<PagingData<CommentaryWithText>>,
    ): Flow<PagingData<CommentaryWithText>> = pagerFlowCache.getOrPut(key, create)

    private data class BaseLineResolution(
        val baseLineIds: List<Long>,
        val headingTocEntryId: Long? = null,
        val headingBookId: Long? = null,
    )

    private suspend fun loadBookMetadata(
        bookId: Long,
        localCache: MutableMap<Long, Book>,
    ): Book? {
        localCache[bookId]?.let { return it }
        commentatorBookCache[bookId]?.let { cached ->
            localCache[bookId] = cached
            return cached
        }
        // Load authors + pubDates so commentator ordering can use canonical author ranks
        // before falling back to publication-date heuristics.
        val loaded = runSuspendCatching { repository.getBook(bookId) }.getOrNull() ?: return null
        commentatorBookCache[bookId] = loaded
        localCache[bookId] = loaded
        return loaded
    }

    /**
     * Construit un Pager pour les commentaires d'une ligne
     */
    fun buildCommentariesPager(
        lineId: Long,
        commentatorId: Long? = null,
    ): Flow<PagingData<CommentaryWithText>> =
        cachedPager("com:$lineId:${commentatorId ?: -1L}") {
            val ids = commentatorId?.let { setOf(it) } ?: emptySet()
            Pager(
                config = PagingDefaults.COMMENTS.config(placeholders = false),
                pagingSourceFactory = {
                    CommentsForLineOrTocPagingSource(repository, lineId, ids)
                },
            ).flow.cachedIn(scope)
        }

    /**
     * Warms the first page of each open commentator's column for [lineId] ahead of display
     * (e.g. while a tab is preloaded on hover). Reuses the exact [CommentsForLineOrTocPagingSource]
     * the UI builds so the TOC-section resolution and SQL query are identical — the rows land in
     * SQLite's page cache, making the on-demand pager load instant once the tab is shown.
     */
    suspend fun prefetchCommentaries(
        lineId: Long,
        commentatorIds: Set<Long>,
    ) {
        if (lineId <= 0 || commentatorIds.isEmpty()) return
        for (commentatorId in commentatorIds) {
            currentCoroutineContext().ensureActive()
            runSuspendCatching {
                CommentsForLineOrTocPagingSource(repository, lineId, setOf(commentatorId))
                    .load(
                        PagingSource.LoadParams.Refresh(
                            key = 0,
                            loadSize = PagingDefaults.COMMENTS.INITIAL_LOAD_SIZE,
                            placeholdersEnabled = false,
                        ),
                    )
            }
        }
    }

    /**
     * Construit un Pager pour les liens/targum d'une ligne
     */
    fun buildLinksPager(
        lineId: Long,
        sourceBookId: Long? = null,
    ): Flow<PagingData<CommentaryWithText>> =
        cachedPager("tgm:$lineId:${sourceBookId ?: -1L}") {
            val ids = sourceBookId?.let { setOf(it) } ?: emptySet()
            Pager(
                config = PagingDefaults.COMMENTS.config(placeholders = false),
                pagingSourceFactory = {
                    LineTargumPagingSource(repository, lineId, ids, setOf(ConnectionType.TARGUM))
                },
            ).flow.cachedIn(scope)
        }

    fun buildSourcesPager(
        lineId: Long,
        sourceBookId: Long? = null,
    ): Flow<PagingData<CommentaryWithText>> =
        cachedPager("src:$lineId:${sourceBookId ?: -1L}") {
            val ids = sourceBookId?.let { setOf(it) } ?: emptySet()
            Pager(
                config = PagingDefaults.COMMENTS.config(placeholders = false),
                pagingSourceFactory = {
                    LineTargumPagingSource(repository, lineId, ids, setOf(ConnectionType.SOURCE))
                },
            ).flow.cachedIn(scope)
        }

    // ========== Multi-line pagers for multi-selection ==========

    /**
     * Construit un Pager pour les commentaires de plusieurs lignes
     */
    fun buildCommentariesPagerForLines(
        lineIds: List<Long>,
        commentatorId: Long? = null,
    ): Flow<PagingData<CommentaryWithText>> =
        cachedPager("comL:${lineIds.joinToString(",")}:${commentatorId ?: -1L}") {
            val ids = commentatorId?.let { setOf(it) } ?: emptySet()
            Pager(
                config = PagingDefaults.COMMENTS.config(placeholders = false),
                pagingSourceFactory = {
                    MultiLineCommentsPagingSource(repository, lineIds, ids)
                },
            ).flow.cachedIn(scope)
        }

    /**
     * Construit un Pager pour les liens/targum de plusieurs lignes
     */
    fun buildLinksPagerForLines(
        lineIds: List<Long>,
        sourceBookId: Long? = null,
    ): Flow<PagingData<CommentaryWithText>> =
        cachedPager("tgmL:${lineIds.joinToString(",")}:${sourceBookId ?: -1L}") {
            val ids = sourceBookId?.let { setOf(it) } ?: emptySet()
            Pager(
                config = PagingDefaults.COMMENTS.config(placeholders = false),
                pagingSourceFactory = {
                    MultiLineLinksPagingSource(repository, lineIds, ids, setOf(ConnectionType.TARGUM))
                },
            ).flow.cachedIn(scope)
        }

    /**
     * Construit un Pager pour les sources de plusieurs lignes
     */
    fun buildSourcesPagerForLines(
        lineIds: List<Long>,
        sourceBookId: Long? = null,
    ): Flow<PagingData<CommentaryWithText>> =
        cachedPager("srcL:${lineIds.joinToString(",")}:${sourceBookId ?: -1L}") {
            val ids = sourceBookId?.let { setOf(it) } ?: emptySet()
            Pager(
                config = PagingDefaults.COMMENTS.config(placeholders = false),
                pagingSourceFactory = {
                    MultiLineLinksPagingSource(repository, lineIds, ids, setOf(ConnectionType.SOURCE))
                },
            ).flow.cachedIn(scope)
        }

    /**
     * Ordered char-count vector for the commentary pager built from a single base line
     * (or its TOC section). Mirrors [buildCommentariesPager]'s ordering, so the scrollbar
     * can derive accurate visual-line metrics. Returns an empty list on failure.
     */
    suspend fun getCommentaryCharCountsForLine(
        lineId: Long,
        commentatorId: Long,
    ): List<Int> =
        runSuspendCatching {
            repository.getCommentaryCharCountsForLineOrSection(
                baseLineId = lineId,
                activeCommentatorIds = setOf(commentatorId),
            )
        }.getOrElse { emptyList() }

    /**
     * Ordered char-count vector for the multi-line commentary pager. Mirrors
     * [buildCommentariesPagerForLines]'s ordering. Returns an empty list on failure.
     */
    suspend fun getCommentaryCharCountsForLines(
        lineIds: List<Long>,
        commentatorId: Long,
    ): List<Int> =
        runSuspendCatching {
            repository.getCommentaryCharCountsForLines(
                lineIds = lineIds,
                activeCommentatorIds = setOf(commentatorId),
            )
        }.getOrElse { emptyList() }

    /**
     * Ordered char-count vector for a single source/targum section attached to a base
     * line (or its TOC section). Mirrors [buildLinksPager] / [buildSourcesPager] row
     * ordering (`b.orderIndex, l.targetLineIndex`). Returns an empty list on failure.
     */
    suspend fun getLinkCharCountsForLine(
        lineId: Long,
        sourceBookId: Long,
        connectionType: ConnectionType,
    ): List<Int> =
        runSuspendCatching {
            repository.getCommentaryCharCountsForLineOrSection(
                baseLineId = lineId,
                activeCommentatorIds = setOf(sourceBookId),
                connectionTypes = setOf(connectionType),
            )
        }.getOrElse { emptyList() }

    /**
     * Ordered char-count vector for a single source/targum section across multiple
     * selected lines. Mirrors [buildLinksPagerForLines] / [buildSourcesPagerForLines]
     * ordering. Returns an empty list on failure.
     */
    suspend fun getLinkCharCountsForLines(
        lineIds: List<Long>,
        sourceBookId: Long,
        connectionType: ConnectionType,
    ): List<Int> =
        runSuspendCatching {
            repository.getCommentaryCharCountsForLines(
                lineIds = lineIds,
                activeCommentatorIds = setOf(sourceBookId),
                connectionTypes = setOf(connectionType),
            )
        }.getOrElse { emptyList() }

    /**
     * Récupère les commentateurs disponibles pour plusieurs lignes (union)
     */
    suspend fun getAvailableCommentatorsForLines(lineIds: List<Long>): Map<String, Long> {
        if (lineIds.isEmpty()) return emptyMap()
        return runSuspendCatching {
            val allGroups = lineIds.flatMap { getCommentatorGroups(it) }
            val map = LinkedHashMap<String, Long>()
            allGroups.forEach { group ->
                group.commentators.forEach { item ->
                    if (!map.containsKey(item.name)) {
                        map[item.name] = item.bookId
                    }
                }
            }
            map
        }.getOrElse { emptyMap() }
    }

    /**
     * Récupère les groupes de commentateurs pour plusieurs lignes (union)
     */
    suspend fun getCommentatorGroupsForLines(lineIds: List<Long>): List<CommentatorGroup> {
        if (lineIds.isEmpty()) return emptyList()
        return runSuspendCatching {
            // Aggregate all commentator groups from all lines, deduplicated by book ID
            val seenBookIds = mutableSetOf<Long>()
            val allGroups = mutableListOf<CommentatorGroup>()

            for (lineId in lineIds) {
                val groups = getCommentatorGroups(lineId)
                for (group in groups) {
                    val newCommentators = group.commentators.filter { seenBookIds.add(it.bookId) }
                    if (newCommentators.isNotEmpty()) {
                        val existingGroup = allGroups.find { it.label == group.label }
                        if (existingGroup != null) {
                            val idx = allGroups.indexOf(existingGroup)
                            allGroups[idx] =
                                existingGroup.copy(
                                    commentators = existingGroup.commentators + newCommentators,
                                )
                        } else {
                            allGroups.add(group.copy(commentators = newCommentators))
                        }
                    }
                }
            }
            allGroups
        }.getOrElse { emptyList() }
    }

    /**
     * Récupère les liens disponibles pour plusieurs lignes (union)
     */
    suspend fun getAvailableLinksForLines(lineIds: List<Long>): Map<String, Long> {
        if (lineIds.isEmpty()) return emptyMap()
        return runSuspendCatching {
            val map = LinkedHashMap<String, Long>()
            for (lineId in lineIds) {
                val links = getAvailableLinks(lineId)
                links.forEach { (name, id) ->
                    if (!map.containsKey(name)) {
                        map[name] = id
                    }
                }
            }
            map
        }.getOrElse { emptyMap() }
    }

    /**
     * Récupère les sources disponibles pour plusieurs lignes (union).
     *
     * Issues a single inverse-direction repository query against the union of
     * all base lines so the order returned by the underlying SQL
     * (`l.isDeclaredBase DESC, b.orderIndex, sl.lineIndex`) is respected
     * globally. Per-lineId iteration would interleave per-source-book entries
     * by lineId rather than by catalog position.
     */
    suspend fun getAvailableSourcesForLines(lineIds: List<Long>): Map<String, Long> {
        if (lineIds.isEmpty()) return emptyMap()
        return runSuspendCatching {
            val selectedBook =
                stateManager.state
                    .first()
                    .navigation.selectedBook
            if (selectedBook?.hasSourceConnection != true) return@runSuspendCatching emptyMap<String, Long>()

            val allBaseIds =
                lineIds
                    .flatMap { resolveBaseLineIds(it) }
                    .distinct()
            if (allBaseIds.isEmpty()) return@runSuspendCatching emptyMap<String, Long>()

            val links =
                repository
                    .getCommentarySummariesForLines(allBaseIds, includeSources = true)
                    .filter { it.link.connectionType == ConnectionType.SOURCE }

            buildSourceMap(links, selectedBook.title.trim())
        }.getOrElse { emptyMap() }
    }

    /**
     * Récupère les commentateurs disponibles pour une ligne
     */
    suspend fun getAvailableCommentators(lineId: Long): Map<String, Long> {
        return runSuspendCatching {
            val groups = getCommentatorGroups(lineId)
            if (groups.isEmpty()) return emptyMap()

            val map = LinkedHashMap<String, Long>()
            groups.forEach { group ->
                group.commentators.forEach { item ->
                    if (!map.containsKey(item.name)) {
                        map[item.name] = item.bookId
                    }
                }
            }
            map
        }.getOrElse { emptyMap() }
    }

    /**
     * Regroupe les commentateurs par catégorie (type) et triés par date de publication (plus ancien d'abord).
     */
    suspend fun getCommentatorGroups(lineId: Long): List<CommentatorGroup> {
        return runSuspendCatching {
            val entries = loadCommentatorEntries(lineId)
            if (entries.isEmpty()) return emptyList()

            val categoryCache = mutableMapOf<Long, Category?>()
            groupCommentatorEntries(entries, categoryCache)
        }.getOrElse { emptyList() }
    }

    private suspend fun resolveGroupLabel(
        book: Book?,
        cache: MutableMap<Long, Category?>,
    ): String {
        if (book == null) return ""

        suspend fun loadCategory(id: Long): Category? {
            cache[id]?.let { return it }
            val loaded = runSuspendCatching { repository.getCategory(id) }.getOrNull()
            cache[id] = loaded
            return loaded
        }

        // Collect the full ancestor chain (book.categoryId → root). Cap depth as
        // a safety net against accidental cycles in the closure table.
        val chain = mutableListOf<Category>()
        var currentId: Long? = book.categoryId
        var depth = 0
        while (currentId != null && depth < 32) {
            currentCoroutineContext().ensureActive()
            val cat = loadCategory(currentId) ?: break
            chain.add(cat)
            currentId = cat.parentId
            depth++
        }
        if (chain.isEmpty()) return ""

        // Pass 1: the strongest, most-informative bucket — "X על Y" labels
        // anchored on a primary corpus (Tanakh / Talmud / Mishna / Shas) win
        // over everything else, scanned across the whole ancestor chain. This
        // is important because a sub-commentary on Rif (chain:
        // `מפרשים < רי״ף < ראשונים על התלמוד`) must surface as
        // `ראשונים על התלמוד`, NOT as `מפרשים על רי״ף`.
        for (category in chain) {
            currentCoroutineContext().ensureActive()
            val title = category.title
            if (
                title.contains("על התנ״ך") ||
                title.contains("על התלמוד") ||
                title.contains("על המשנה") ||
                title.contains("על המשניות") ||
                title.contains("על הש\"ס") ||
                title.contains("על השס")
            ) {
                return title
            }
        }

        // Pass 2: hard-coded multi-book families (chevruta, dictionaries,
        // contemporary authors).
        for (category in chain) {
            currentCoroutineContext().ensureActive()
            val title = category.title
            if (title == "ביאור חברותא" || title == "הערות על ביאור חברותא") return "חברותא"
            if (title.contains("מילונים")) return title
            if (title == "מחברי זמננו") return title
        }

        // Pass 3: "מפרשים" — only when no higher-level "X על Y" anchor exists
        //   (e.g. Mishneh Torah's super-commentaries). Check if the parent's
        //   ancestors include a corpus reference. If so, use that. Otherwise use
        //   immediate parent.
        for ((idx, category) in chain.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (category.title == "מפרשים") {
                // Check if "ראשונים" appears further up the chain; if so, use its corpus parent.
                for (ancestorIdx in (idx + 1) until chain.size) {
                    val ancestor = chain[ancestorIdx]
                    if (ancestor.title == "ראשונים") {
                        // Found "ראשונים" — use its parent corpus
                        val corpus = chain.getOrNull(ancestorIdx + 1)
                        if (corpus != null) {
                            return "ראשונים על ${when (corpus.title) {
                                "בבלי", "בבל" -> "התלמוד"
                                "תנ״ך", "תנך" -> "התנ״ך"
                                "משנה" -> "המשנה"
                                "ש\"ס", "ש״ס", "שס" -> "הש\"ס"
                                else -> corpus.title
                            }}"
                        }
                        break
                    }
                }
                // Fallback: use immediate parent
                val parent = chain.getOrNull(idx + 1)
                if (parent != null && parent.title.isNotBlank()) {
                    return "מפרשים על ${parent.title}"
                }
                return "מפרשים"
            }
        }

        // Pass 4: bare "ראשונים" / "אחרונים" anywhere in the chain. Look for corpus
        //   references further up; if found, use those. Otherwise use immediate parent.
        for ((idx, category) in chain.withIndex()) {
            currentCoroutineContext().ensureActive()
            val title = category.title
            if (title == "ראשונים" || title == "אחרונים") {
                // Check ancestors for corpus references (בבלי, תנ״ך, משנה, etc.)
                for (ancestorIdx in (idx + 1) until chain.size) {
                    val ancestor = chain[ancestorIdx]
                    val corpusLabel =
                        when (ancestor.title) {
                            "בבלי", "בבל" -> "התלמוד"
                            "תנ״ך", "תנך" -> "התנ״ך"
                            "משנה" -> "המשנה"
                            "ש\"ס", "ש״ס", "שס" -> "הש\"ס"
                            else -> null
                        }
                    if (corpusLabel != null) {
                        return "$title על $corpusLabel"
                    }
                }
                // Fallback: use immediate parent
                val parent = chain.getOrNull(idx + 1)
                if (parent != null && parent.title.isNotBlank()) {
                    val parentLabel =
                        when (parent.title) {
                            "תנ״ך", "תנך" -> "התנ״ך"
                            "תלמוד" -> "התלמוד"
                            "משנה" -> "המשנה"
                            "ש\"ס", "ש״ס", "שס" -> "הש\"ס"
                            else -> parent.title
                        }
                    return "$title על $parentLabel"
                }
                return title
            }
        }

        // Second pass: collapse fragmented sub-trees (Targumim, Midrash, Kabbalah, Chasidut)
        // into a single top-level bucket so multi-volume editorial families don't
        // create one group per book.
        for (category in chain) {
            currentCoroutineContext().ensureActive()
            val title = category.title
            // Targums: "תורה < תרגום אונקלוס < תרגומים < תנ״ך"; also bare "תרגום ירושלמי" etc.
            if (title == "תרגומים" || title.startsWith("תרגום ") || title.startsWith("תפסיר ")) {
                return "תרגומים"
            }
            // Midrash: "מדרש לקח טוב < אגדה < מדרש", "מדרש רבה < אגדה < מדרש".
            if (title == "מדרש") return "מדרש"
            // Kabbalah: "ספרי קבלה נוספים < קבלה", "זהר < קבלה".
            if (title == "קבלה") return "קבלה"
            // Chasidut sub-tree.
            if (title == "חסידות" || title.contains("חסידות")) return "חסידות"
        }

        // Fallback: use the deepest reasonable bucket from the root side
        // (avoid single-author categories like חזקוני/מהר״ל that produce singleton groups).
        val rootLevel = chain.lastOrNull()?.title
        return rootLevel ?: chain.first().title
    }

    /**
     * Canonical editorial rank for top-level commentator groups. Lower wins.
     * Anything not listed falls into [GROUP_RANK_DEFAULT] and is sorted by
     * pub-date / alphabet within that bucket.
     */
    private fun groupRank(label: String): Int {
        // Tanach buckets
        if (label == "תרגומים") return 10
        if (label.startsWith("ראשונים על המשנה")) return 20
        if (label.startsWith("ראשונים על התלמוד") || label.startsWith("ראשונים על הש")) return 25
        if (label.startsWith("ראשונים על התנ״ך")) return 30
        if (label.startsWith("אחרונים על המשנה")) return 40
        if (label.startsWith("אחרונים על התלמוד") || label.startsWith("אחרונים על הש")) return 45
        if (label.startsWith("אחרונים על התנ״ך")) return 50
        if (label.startsWith("מפרשים על")) return 55
        if (label == "ראשונים") return 60
        if (label == "אחרונים") return 65
        if (label.startsWith("ראשונים על ")) return 67
        if (label.startsWith("אחרונים על ")) return 68
        if (label == "מדרש") return 70
        if (label == "חסידות") return 80
        if (label == "קבלה") return 90
        if (label == "חברותא") return 100
        if (label.contains("מילונים")) return 110
        if (label == "מחברי זמננו") return 120
        return GROUP_RANK_DEFAULT
    }

    /**
     * Approximate canonical year (birth) for major Rishonim/Acharonim. Used as the
     * primary intra-group sort key — the printed first-edition dates stored in
     * `pub_date` are too noisy (Rashi at 1476, Hadar Zekenim at 1840) to give a
     * stable chronological order on their own.
     *
     * Match against [Book.authors] first, then against [Book.title] / displayName.
     */
    private fun canonicalRank(
        book: Book?,
        displayName: String,
    ): Int? {
        if (book == null) return null

        fun normalize(s: String): String = s.replace('"', '״').replace('\'', '׳').trim()

        val authorNames = book.authors.map { normalize(it.name) }
        for (author in authorNames) {
            CANONICAL_AUTHOR_YEAR[author]?.let { return it }
        }
        val title = normalize(book.title)
        for ((pattern, year) in CANONICAL_TITLE_YEAR) {
            if (title.contains(pattern)) return year
        }
        val display = normalize(displayName)
        for ((pattern, year) in CANONICAL_TITLE_YEAR) {
            if (display.contains(pattern)) return year
        }
        return null
    }

    private companion object {
        const val GROUP_RANK_DEFAULT = 1_000

        // Upper bound on memoized commentary/link/source pager flows (across all lines and
        // commentators visited in this book tab). Each retains its loaded pages, so keep it
        // modest; the least-recently-used pager is evicted past this size.
        const val MAX_CACHED_PAGERS = 32

        // Upper bound on cached commentator-group snapshots (one per base line). Snapshots are
        // light (group/commentator metadata, no commentary text), so this can be generous.
        const val MAX_CACHED_LINE_CONNECTIONS = 512

        // Canonical (approximate) author birth years for the dominant Rishonim
        // and Acharonim that ship with the corpus. Keys are normalized to gershayim
        // form (״). Add to this list when new "VIP" commentators surface.
        val CANONICAL_AUTHOR_YEAR: Map<String, Int> =
            mapOf(
                // Geonim
                "ר' סעדיה גאון" to 882,
                "סעדיה גאון" to 882,
                "רב סעדיה גאון" to 882,
                // Rishonim – Ashkenaz / Tsarfat
                "רש״י" to 1040,
                "רשב״ם" to 1085,
                "ר' יוסף קרא" to 1065,
                "יוסף בכור שור" to 1140,
                "אבן עזרא" to 1089,
                "ר' אברהם אבן עזרא" to 1089,
                "רד״ק" to 1160,
                "רמב״ן" to 1194,
                "חזקוני" to 1240,
                "רא״ש" to 1250,
                "רבנו בחיי" to 1255,
                "בחיי בן אשר" to 1255,
                "יעקב בן אשר" to 1269,
                "רלב״ג" to 1288,
                "מנחם ריקנטי" to 1290,
                "אברבנאל" to 1437,
                "עובדיה מברטנורא" to 1445,
                "אליהו בן אברהם מזרחי" to 1455,
                "ספורנו" to 1475,
                // Acharonim
                "אלשיך" to 1508,
                "מהר״ל" to 1520,
                "שלמה אפרים מלונטשיץ" to 1550,
                "ש״ך" to 1622,
                "חיים בן עטר" to 1696,
                "אליהו בן שלמה זלמן מווילנה" to 1720,
                "משה סופר" to 1762,
                "נתן נטע שפירא" to 1585,
                "יעקב צבי מקלנבורג" to 1785,
                "מלבי״ם" to 1809,
                "נפתלי צבי יהודה ברלין" to 1816,
                "יוסף חיים" to 1834,
                "מאיר שמחה הכהן" to 1843,
                "רב יוסף דוב הלוי סולובייצ'ק" to 1903,
            )

        // Title fragments (normalized to gershayim) → canonical year. Acts as a
        // fallback when the author table is sparse for older works.
        val CANONICAL_TITLE_YEAR: List<Pair<String, Int>> =
            listOf(
                "רס״ג" to 882,
                "ר' סעדיה גאון" to 882,
                "רש״י" to 1040,
                "רשב״ם" to 1085,
                "אבן עזרא" to 1089,
                "אב״ע" to 1089,
                "ראב״ע" to 1089,
                "בכור שור" to 1140,
                "רד״ק" to 1160,
                "רמב״ן" to 1194,
                "חזקוני" to 1240,
                "רא״ש" to 1250,
                "רבנו בחיי" to 1255,
                "רבינו בחיי" to 1255,
                "בעל הטורים" to 1269,
                "הטור הארוך" to 1269,
                "רלב״ג" to 1288,
                "פענח רזא" to 1295,
                "ריקנטי" to 1290,
                "רקנאטי" to 1290,
                "דעת זקנים" to 1290,
                "הדר זקנים" to 1290,
                "אברבנאל" to 1437,
                "ברטנורא" to 1445,
                "מזרחי" to 1455,
                "ספורנו" to 1475,
                "צרור המור" to 1440,
                "תולדות יצחק" to 1458,
                "אלשיך" to 1508,
                "מהר״ל" to 1520,
                "גור אריה" to 1520,
                "כלי יקר" to 1550,
                "שפתי כהן" to 1622,
                "אור החיים" to 1696,
                "מנחת שי" to 1565,
                "שפתי חכמים" to 1641,
                "אבי עזר" to 1750,
                "אדרת אליהו" to 1720,
                "הגר״א" to 1720,
                "חתם סופר" to 1762,
                "הכתב והקבלה" to 1785,
                "תפארת יהונתן" to 1690,
                "אהבת יהונתן" to 1690,
                "מלבי״ם" to 1809,
                "העמק דבר" to 1816,
                "נצי״ב" to 1816,
                "רש״ר הירש" to 1808,
                "בן איש חי" to 1834,
                "תורה תמימה" to 1860,
                "פרדס יוסף" to 1880,
                "משך חכמה" to 1843,
                "בית הלוי" to 1820,
                "נתינה לגר" to 1875,
                "תרגום אונקלוס" to -100,
                "תרגום יונתן" to 100,
                "תרגום ירושלמי" to 200,
                "תפסיר רס״ג" to 882,
                "מדרש" to 500,
                "בראשית רבה" to 400,
                "שמות רבה" to 400,
                "ויקרא רבה" to 400,
                "מדרש תנחומא" to 500,
                "תנחומא בובר" to 500,
                "פסיקתא" to 600,
                "מכילתא" to 200,
                "ספרי" to 200,
            )
    }

    private fun sanitizeCommentatorName(
        raw: String,
        currentBookTitle: String,
    ): String {
        if (currentBookTitle.isBlank()) return raw

        // Punctuation characters that can appear around/within the title
        val punct = "[\\s,.\\-־–—:;'\"׳״!?()\\[\\]{}]*"

        // Split title into words, ignoring punctuation
        val titleWords =
            currentBookTitle
                .trim()
                .split(Regex("[\\s,.\\-־–—:;'\"׳״!?()\\[\\]{}]+"))
                .filter { it.isNotBlank() }

        if (titleWords.isEmpty()) return raw

        // Join words with flexible punctuation/whitespace pattern between them
        val flexibleTitle = titleWords.joinToString(punct) { Regex.escape(it) }

        // Build pattern for " על ספר <title>" or " על <title>" with flexible punctuation
        val pattern = Regex(" על ספר$punct$flexibleTitle$punct| על\\s+$punct$flexibleTitle$punct")

        return raw.replace(pattern, "").trim()
    }

    private data class CommentatorEntry(
        val bookId: Long,
        val displayName: String,
        val book: Book?,
    )

    private suspend fun buildCommentatorEntries(
        commentaries: List<CommentarySummary>,
        currentBookTitle: String,
        bookCache: MutableMap<Long, Book>,
    ): List<CommentatorEntry> {
        val displayNameByBookId = LinkedHashMap<Long, String>()

        commentaries.forEach { commentary ->
            val bookId = commentary.link.targetBookId
            if (!displayNameByBookId.containsKey(bookId)) {
                val raw = commentary.targetBookTitle
                val display = sanitizeCommentatorName(raw, currentBookTitle)
                displayNameByBookId[bookId] = display
            }
        }

        val booksById: Map<Long, Book?> =
            displayNameByBookId.keys.associateWith { id ->
                loadBookMetadata(id, bookCache)
            }

        return displayNameByBookId.map { (bookId, displayName) ->
            CommentatorEntry(
                bookId = bookId,
                displayName = displayName,
                book = booksById[bookId],
            )
        }
    }

    private suspend fun groupCommentatorEntries(
        entries: List<CommentatorEntry>,
        categoryCache: MutableMap<Long, Category?>,
    ): List<CommentatorGroup> {
        if (entries.isEmpty()) return emptyList()

        val groupsByLabel = LinkedHashMap<String, MutableList<CommentatorEntry>>()
        entries.forEach { entry ->
            val label = resolveGroupLabel(entry.book, categoryCache)
            val list = groupsByLabel.getOrPut(label) { mutableListOf() }
            list.add(entry)
        }

        data class TempGroup(
            val label: String,
            val rank: Int,
            val entries: List<CommentatorEntry>,
            val earliestYear: Int,
        )

        // Resolve a chronological year per entry, falling back through:
        //  1. canonicalRank() — hand-curated author/title → birth year table.
        //  2. earliest pub_date year — first known printing.
        //  3. Int.MAX_VALUE  — pushes undated entries to the tail.
        fun entryYear(entry: CommentatorEntry): Int {
            canonicalRank(entry.book, entry.displayName)?.let { return it }
            entry.book
                ?.pubDates
                ?.let { extractEarliestYear(it) }
                ?.let { return it }
            return Int.MAX_VALUE
        }

        val tempGroups =
            groupsByLabel.map { (label, groupEntries) ->
                val sortedEntries =
                    groupEntries.sortedWith(
                        compareBy(
                            // "הערות על X" companion notes always trail their parent book.
                            { if (categoryCache[it.book?.categoryId]?.title?.startsWith("הערות על") == true) 1 else 0 },
                            { entryYear(it) },
                            { it.displayName },
                        ),
                    )
                val groupEarliestYear = sortedEntries.minOfOrNull { entryYear(it) } ?: Int.MAX_VALUE

                TempGroup(
                    label = label,
                    rank = groupRank(label),
                    entries = sortedEntries,
                    earliestYear = groupEarliestYear,
                )
            }

        return tempGroups
            .sortedWith(
                // Editorial rank dominates (Targums → Rishonim → Acharonim → Midrash → …);
                // within the same rank, earliest year then label keep results deterministic.
                compareBy<TempGroup> { it.rank }
                    .thenBy { it.earliestYear }
                    .thenBy { it.label },
            ).map { group ->
                CommentatorGroup(
                    label = group.label,
                    commentators =
                        group.entries.map { entry ->
                            CommentatorItem(
                                name = entry.displayName,
                                bookId = entry.bookId,
                            )
                        },
                )
            }.filter { it.commentators.isNotEmpty() }
    }

    private fun buildSourceMap(
        links: List<CommentarySummary>,
        currentBookTitle: String,
    ): Map<String, Long> {
        if (links.isEmpty()) return emptyMap()

        val map = LinkedHashMap<String, Long>()
        links.forEach { link ->
            val display = sanitizeCommentatorName(link.targetBookTitle, currentBookTitle)
            if (!map.containsKey(display)) {
                map[display] = link.link.targetBookId
            }
        }
        return map
    }

    private suspend fun buildLineConnectionsSnapshot(
        connections: List<CommentarySummary>,
        currentBookTitle: String,
        bookCache: MutableMap<Long, Book>,
        categoryCache: MutableMap<Long, Category?>,
    ): LineConnectionsSnapshot {
        if (connections.isEmpty()) return LineConnectionsSnapshot()

        val commentaries = connections.filter { it.link.connectionType == ConnectionType.COMMENTARY }
        val targumLinks = connections.filter { it.link.connectionType == ConnectionType.TARGUM }
        val sourceLinks = connections.filter { it.link.connectionType == ConnectionType.SOURCE }

        val commentatorGroups =
            if (commentaries.isNotEmpty()) {
                val entries = buildCommentatorEntries(commentaries, currentBookTitle, bookCache)
                groupCommentatorEntries(entries, categoryCache)
            } else {
                emptyList()
            }

        return LineConnectionsSnapshot(
            commentatorGroups = commentatorGroups,
            targumSources = buildSourceMap(targumLinks, currentBookTitle),
            sources = buildSourceMap(sourceLinks, currentBookTitle),
        )
    }

    private suspend fun loadCommentatorEntries(lineId: Long): List<CommentatorEntry> {
        val baseIds = resolveBaseLineIds(lineId)

        val commentaries =
            repository
                .getCommentarySummariesForLines(baseIds)
                .filter { it.link.connectionType == ConnectionType.COMMENTARY }

        if (commentaries.isEmpty()) return emptyList()

        val currentBookTitle =
            stateManager.state
                .first()
                .navigation.selectedBook
                ?.title
                ?.trim()
                .orEmpty()
        val bookCache = mutableMapOf<Long, Book>()
        return buildCommentatorEntries(commentaries, currentBookTitle, bookCache)
    }

    private fun extractEarliestYear(pubDates: List<PubDate>): Int? {
        var best: Int? = null
        for (pub in pubDates) {
            val raw = pub.date
            val candidate =
                YEAR_REGEX.find(raw)?.value?.toIntOrNull()
                    ?: if (raw.contains("Ancient", ignoreCase = true)) Int.MIN_VALUE else null
            if (candidate != null) {
                best = if (best == null || candidate < best) candidate else best
            }
        }
        return best
    }

    /**
     * Récupère les sources de liens disponibles pour une ligne
     */
    suspend fun getAvailableLinks(lineId: Long): Map<String, Long> =
        runSuspendCatching {
            val resolution = resolveBaseLineResolution(lineId)
            val defaultTargumId =
                resolution.headingBookId?.let { bookId ->
                    loadDefaultTargumIds(bookId).firstOrNull()
                }
            val links =
                repository
                    .getCommentarySummariesForLines(resolution.baseLineIds)
                    .filter { it.link.connectionType == ConnectionType.TARGUM }
                    .let { targumLinks ->
                        if (resolution.headingTocEntryId != null && defaultTargumId != null) {
                            targumLinks.filter { it.link.targetBookId == defaultTargumId }
                        } else {
                            targumLinks
                        }
                    }

            val currentBookTitle =
                stateManager.state
                    .first()
                    .navigation.selectedBook
                    ?.title
                    ?.trim()
                    .orEmpty()

            buildSourceMap(links, currentBookTitle)
        }.getOrElse { emptyMap() }

    suspend fun getAvailableSources(lineId: Long): Map<String, Long> =
        runSuspendCatching {
            val selectedBook =
                stateManager.state
                    .first()
                    .navigation.selectedBook
            // Fast path: book has no inbound oriented links — no need to hit DB.
            if (selectedBook?.hasSourceConnection != true) return@runSuspendCatching emptyMap<String, Long>()

            val baseIds = resolveBaseLineIds(lineId)
            val links =
                repository
                    .getCommentarySummariesForLines(baseIds)
                    .filter { it.link.connectionType == ConnectionType.SOURCE }

            val currentBookTitle = selectedBook.title.trim()
            buildSourceMap(links, currentBookTitle)
        }.getOrElse { emptyMap() }

    suspend fun loadLineConnections(lineIds: List<Long>): Map<Long, LineConnectionsSnapshot> {
        if (lineIds.isEmpty()) return emptyMap()
        val distinctIds = lineIds.distinct()

        // Read-through cache: only query the DB for lines we have not resolved yet.
        val cached = LinkedHashMap<Long, LineConnectionsSnapshot>()
        val missing = ArrayList<Long>()
        synchronized(lineConnectionsCache) {
            distinctIds.forEach { id ->
                val hit = lineConnectionsCache[id]
                if (hit != null) cached[id] = hit else missing.add(id)
            }
        }
        if (missing.isEmpty()) return cached

        fun storeAndMerge(loaded: Map<Long, LineConnectionsSnapshot>): Map<Long, LineConnectionsSnapshot> {
            synchronized(lineConnectionsCache) { loaded.forEach { (id, snap) -> lineConnectionsCache[id] = snap } }
            return cached + loaded
        }

        val resolutionCache = LinkedHashMap<Long, BaseLineResolution>()
        val tocLinesCache = mutableMapOf<Long, List<Long>>()
        val headingCache = mutableMapOf<Long, TocEntry?>()

        missing.forEach { id ->
            resolutionCache[id] = resolveBaseLineResolution(id, tocLinesCache, headingCache)
        }

        val allBaseIds = resolutionCache.values.flatMap { it.baseLineIds }.distinct()
        if (allBaseIds.isEmpty()) return storeAndMerge(missing.associateWith { LineConnectionsSnapshot() })

        val currentState = stateManager.state.first()
        val selectedBook = currentState.navigation.selectedBook

        val allConnections = repository.getCommentarySummariesForLines(allBaseIds)
        if (allConnections.isEmpty()) return storeAndMerge(missing.associateWith { LineConnectionsSnapshot() })

        val connectionsBySource = allConnections.groupBy { it.link.sourceLineId }
        val currentBookTitle =
            selectedBook
                ?.title
                ?.trim()
                .orEmpty()
        val bookCache = mutableMapOf<Long, Book>()
        val categoryCache = mutableMapOf<Long, Category?>()

        val defaultTargumByBookId = mutableMapOf<Long, Long?>()
        resolutionCache.values.mapNotNull { it.headingBookId }.distinct().forEach { bookId ->
            val ids = loadDefaultTargumIds(bookId)
            defaultTargumByBookId[bookId] = ids.firstOrNull()
        }

        val snapshots =
            resolutionCache.mapValues { (_, resolution) ->
                val aggregated = resolution.baseLineIds.flatMap { baseId -> connectionsBySource[baseId].orEmpty() }
                val defaultTargumId = resolution.headingBookId?.let { defaultTargumByBookId[it] }
                val filtered = filterTargumConnections(aggregated, resolution, defaultTargumId)
                buildLineConnectionsSnapshot(filtered, currentBookTitle, bookCache, categoryCache)
            }
        return storeAndMerge(snapshots)
    }

    /**
     * Applique les commentateurs par défaut pour un livre donné, s'ils existent dans la base.
     * Ne remplace pas une configuration déjà mémorisée pour ce livre.
     */
    suspend fun applyDefaultCommentatorsForBook(bookId: Long) {
        val currentState = stateManager.state.first()
        val existing = currentState.content.selectedCommentatorsByBook[bookId]
        if (!existing.isNullOrEmpty()) return

        val defaults =
            runSuspendCatching {
                repository.getDefaultCommentatorIdsForBook(bookId)
            }.getOrDefault(emptyList())

        if (defaults.isEmpty()) return

        stateManager.updateContent {
            val byBook = selectedCommentatorsByBook.toMutableMap()
            byBook[bookId] = defaults.toSet()
            copy(selectedCommentatorsByBook = byBook)
        }
    }

    /**
     * Met à jour les commentateurs sélectionnés pour une ligne
     */
    suspend fun updateSelectedCommentators(
        lineId: Long,
        selectedIds: Set<Long>,
    ) {
        val currentState = stateManager.state.first()
        val currentContent = currentState.content
        val bookId = currentState.navigation.selectedBook?.id ?: return

        val prevLineSelected = currentContent.selectedCommentatorsByLine[lineId] ?: emptySet()
        val oldSticky = currentContent.selectedCommentatorsByBook[bookId] ?: emptySet()

        val additions = selectedIds.minus(prevLineSelected)
        val removals = prevLineSelected.minus(selectedIds)

        val newSticky =
            oldSticky
                .plus(additions)
                .minus(removals)

        val byLine =
            currentContent.selectedCommentatorsByLine.toMutableMap().apply {
                if (selectedIds.isEmpty()) remove(lineId) else this[lineId] = selectedIds
            }
        val byBook =
            currentContent.selectedCommentatorsByBook.toMutableMap().apply {
                if (newSticky.isEmpty()) remove(bookId) else this[bookId] = newSticky
            }

        stateManager.updateContent {
            copy(
                selectedCommentatorsByLine = byLine,
                selectedCommentatorsByBook = byBook,
            )
        }
    }

    private suspend fun resolveBaseLineIds(lineId: Long): List<Long> = resolveBaseLineResolution(lineId).baseLineIds

    private suspend fun resolveBaseLineIds(
        lineId: Long,
        tocLinesCache: MutableMap<Long, List<Long>>,
        headingCache: MutableMap<Long, TocEntry?> = mutableMapOf(),
    ): List<Long> = resolveBaseLineResolution(lineId, tocLinesCache, headingCache).baseLineIds

    private suspend fun resolveBaseLineResolution(lineId: Long): BaseLineResolution =
        resolveBaseLineResolution(lineId, mutableMapOf(), mutableMapOf())

    private suspend fun resolveBaseLineResolution(
        lineId: Long,
        tocLinesCache: MutableMap<Long, List<Long>>,
        headingCache: MutableMap<Long, TocEntry?>,
    ): BaseLineResolution {
        val headingToc =
            headingCache.getOrPut(lineId) {
                repository.getHeadingTocEntryByLineId(lineId)
            }
        if (headingToc != null) {
            val lines =
                tocLinesCache.getOrPut(headingToc.id) {
                    repository.getLineIdsForTocEntry(headingToc.id)
                }
            val idx = lines.indexOf(lineId)
            val trimmed =
                if (idx >= 0) {
                    val halfWindow = MAX_BASE_LINES_PER_REQUEST / 2
                    val start = max(0, idx - halfWindow)
                    val end = min(lines.size, start + MAX_BASE_LINES_PER_REQUEST)
                    lines.subList(start, end)
                } else {
                    lines.take(MAX_BASE_LINES_PER_REQUEST)
                }
            return BaseLineResolution(
                baseLineIds = trimmed.filter { it != lineId },
                headingTocEntryId = headingToc.id,
                headingBookId = headingToc.bookId,
            )
        }
        return BaseLineResolution(baseLineIds = listOf(lineId))
    }

    private suspend fun loadDefaultTargumIds(bookId: Long): List<Long> {
        defaultTargumCache[bookId]?.let { return it }
        val ids =
            runSuspendCatching { repository.getDefaultTargumIdsForBook(bookId) }
                .getOrElse { emptyList() }
        defaultTargumCache[bookId] = ids
        return ids
    }

    private fun filterTargumConnections(
        connections: List<CommentarySummary>,
        resolution: BaseLineResolution,
        defaultTargumId: Long?,
    ): List<CommentarySummary> {
        if (resolution.headingTocEntryId == null || defaultTargumId == null) return connections
        return connections.filter { summary ->
            summary.link.connectionType != ConnectionType.TARGUM || summary.link.targetBookId == defaultTargumId
        }
    }

    /**
     * Met à jour les sources de liens sélectionnées pour une ligne
     */
    suspend fun updateSelectedLinkSources(
        lineId: Long,
        selectedIds: Set<Long>,
    ) {
        val currentContent = stateManager.state.first().content
        val bookId =
            stateManager.state
                .first()
                .navigation.selectedBook
                ?.id ?: return

        // Mettre à jour par ligne
        val byLine = currentContent.selectedLinkSourcesByLine.toMutableMap()
        if (selectedIds.isEmpty()) {
            byLine.remove(lineId)
        } else {
            byLine[lineId] = selectedIds
        }

        // Mettre à jour par livre
        val byBook = currentContent.selectedLinkSourcesByBook.toMutableMap()
        if (selectedIds.isEmpty()) {
            byBook.remove(bookId)
        } else {
            byBook[bookId] = selectedIds
        }

        stateManager.updateContent {
            copy(
                selectedLinkSourcesByLine = byLine,
                selectedLinkSourcesByBook = byBook,
                selectedTargumSourceIds = selectedIds,
            )
        }
    }

    suspend fun updateSelectedSources(
        lineId: Long,
        selectedIds: Set<Long>,
    ) {
        val currentContent = stateManager.state.first().content
        val bookId =
            stateManager.state
                .first()
                .navigation.selectedBook
                ?.id ?: return

        val byLine = currentContent.selectedSourcesByLine.toMutableMap()
        if (selectedIds.isEmpty()) {
            byLine.remove(lineId)
        } else {
            byLine[lineId] = selectedIds
        }

        val byBook = currentContent.selectedSourcesByBook.toMutableMap()
        if (selectedIds.isEmpty()) {
            byBook.remove(bookId)
        } else {
            byBook[bookId] = selectedIds
        }

        stateManager.updateContent {
            copy(
                selectedSourcesByLine = byLine,
                selectedSourcesByBook = byBook,
                selectedSourceIds = selectedIds,
            )
        }
    }

    /**
     * Réapplique les commentateurs sélectionnés pour une nouvelle ligne
     */
    suspend fun reapplySelectedCommentators(line: Line) {
        val currentState = stateManager.state.first()
        val bookId = currentState.navigation.selectedBook?.id ?: line.bookId
        val sticky = currentState.content.selectedCommentatorsByBook[bookId] ?: emptySet()

        if (sticky.isEmpty()) return

        try {
            val available = getAvailableCommentators(line.id)
            if (available.isEmpty()) return

            val desired = available.values.filter { it in sticky }

            if (desired.isNotEmpty()) {
                updateSelectedCommentatorsForLine(line.id, desired.toSet())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun updateSelectedCommentatorsForLine(
        lineId: Long,
        selectedIds: Set<Long>,
    ) {
        val currentState = stateManager.state.first()
        val byLine =
            currentState.content.selectedCommentatorsByLine.toMutableMap().apply {
                if (selectedIds.isEmpty()) remove(lineId) else this[lineId] = selectedIds
            }
        stateManager.updateContent {
            copy(selectedCommentatorsByLine = byLine)
        }
    }

    /**
     * Réapplique les sources de liens sélectionnées pour une nouvelle ligne
     */
    suspend fun reapplySelectedLinkSources(line: Line) {
        val currentState = stateManager.state.first()
        val bookId = currentState.navigation.selectedBook?.id ?: line.bookId
        val remembered = currentState.content.selectedLinkSourcesByBook[bookId] ?: emptySet()

        if (remembered.isEmpty()) return

        try {
            val available = getAvailableLinks(line.id)
            val availableIds = available.values.toSet()
            val intersection = remembered.intersect(availableIds)

            if (intersection.isNotEmpty()) {
                updateSelectedLinkSources(line.id, intersection)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun reapplySelectedSources(line: Line) {
        val currentState = stateManager.state.first()
        val bookId = currentState.navigation.selectedBook?.id ?: line.bookId
        val remembered = currentState.content.selectedSourcesByBook[bookId] ?: emptySet()

        if (remembered.isEmpty()) return

        try {
            val available = getAvailableSources(line.id)
            val availableIds = available.values.toSet()
            val intersection = remembered.intersect(availableIds)

            if (intersection.isNotEmpty()) {
                updateSelectedSources(line.id, intersection)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /**
     * Réapplique les commentateurs sélectionnés pour plusieurs lignes (multi-sélection).
     * Ne sélectionne des commentateurs que s'il y en a de mémorisés pour ce livre.
     * Par défaut, seul le targum est affiché pour les TOC entries.
     */
    suspend fun reapplySelectedCommentatorsForLines(
        lineIds: List<Long>,
        primaryLineId: Long,
        bookId: Long,
    ) {
        val currentState = stateManager.state.first()
        val sticky = currentState.content.selectedCommentatorsByBook[bookId] ?: emptySet()

        // Si aucun commentateur mémorisé, ne rien sélectionner (seul le targum par défaut)
        if (sticky.isEmpty()) return

        try {
            // Obtenir l'union des commentateurs disponibles pour toutes les lignes
            val available = getAvailableCommentatorsForLines(lineIds)
            if (available.isEmpty()) return

            val desired = available.values.filter { it in sticky }

            if (desired.isNotEmpty()) {
                updateSelectedCommentatorsForLine(primaryLineId, desired.toSet())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /**
     * Réapplique les sources de liens sélectionnées pour plusieurs lignes (multi-sélection)
     */
    suspend fun reapplySelectedLinkSourcesForLines(
        lineIds: List<Long>,
        primaryLineId: Long,
        bookId: Long,
    ) {
        val currentState = stateManager.state.first()
        val remembered = currentState.content.selectedLinkSourcesByBook[bookId] ?: emptySet()

        if (remembered.isEmpty()) return

        try {
            val available = getAvailableLinksForLines(lineIds)
            val availableIds = available.values.toSet()
            val intersection = remembered.intersect(availableIds)

            if (intersection.isNotEmpty()) {
                updateSelectedLinkSources(primaryLineId, intersection)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /**
     * Réapplique les sources sélectionnées pour plusieurs lignes (multi-sélection)
     */
    suspend fun reapplySelectedSourcesForLines(
        lineIds: List<Long>,
        primaryLineId: Long,
        bookId: Long,
    ) {
        val currentState = stateManager.state.first()
        val remembered = currentState.content.selectedSourcesByBook[bookId] ?: emptySet()

        if (remembered.isEmpty()) return

        try {
            val available = getAvailableSourcesForLines(lineIds)
            val availableIds = available.values.toSet()
            val intersection = remembered.intersect(availableIds)

            if (intersection.isNotEmpty()) {
                updateSelectedSources(primaryLineId, intersection)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /**
     * Met à jour l'onglet sélectionné des commentaires
     */
    fun updateCommentariesTab(index: Int) {
        stateManager.updateContent {
            copy(
                commentariesSelectedTab = index,
            )
        }
    }

    /**
     * Met à jour la position de scroll des commentaires
     */
    fun updateCommentariesScrollPosition(
        index: Int,
        offset: Int,
    ) {
        stateManager.updateContent {
            copy(
                commentariesScrollIndex = index,
                commentariesScrollOffset = offset,
            )
        }
    }

    /**
     * Met à jour la position de scroll de la liste des commentateurs
     */
    fun updateCommentatorsListScrollPosition(
        index: Int,
        offset: Int,
    ) {
        stateManager.updateContent {
            copy(
                commentatorsListScrollIndex = index,
                commentatorsListScrollOffset = offset,
            )
        }
    }

    /**
     * Met à jour la position de scroll d'une colonne de commentaires (par commentateur)
     */
    fun updateCommentaryColumnScrollPosition(
        commentatorId: Long,
        index: Int,
        offset: Int,
    ) {
        stateManager.updateContent(save = false) {
            if (
                commentariesColumnScrollIndexByCommentator[commentatorId] == index &&
                commentariesColumnScrollOffsetByCommentator[commentatorId] == offset
            ) {
                return@updateContent this
            }

            val idxMap = commentariesColumnScrollIndexByCommentator.toMutableMap()
            val offMap = commentariesColumnScrollOffsetByCommentator.toMutableMap()
            idxMap[commentatorId] = index
            offMap[commentatorId] = offset
            copy(
                commentariesColumnScrollIndexByCommentator = idxMap,
                commentariesColumnScrollOffsetByCommentator = offMap,
            )
        }
    }
}
