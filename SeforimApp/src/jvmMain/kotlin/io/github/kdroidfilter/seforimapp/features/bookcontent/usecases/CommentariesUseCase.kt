package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import androidx.paging.Pager
import androidx.paging.PagingData
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * UseCase pour gérer les commentaires et liens
 */
class CommentariesUseCase(
    private val repository: SeforimRepository,
    private val stateManager: BookContentStateManager,
    private val scope: CoroutineScope,
) {
    private companion object {
        private const val MAX_COMMENTATORS = 4
        private val YEAR_REGEX = Regex("""-?\d{3,4}""")
        private const val MAX_BASE_LINES_PER_REQUEST = 128
    }

    private val commentatorBookCache: MutableMap<Long, Book> = ConcurrentHashMap()
    private val defaultTargumCache: MutableMap<Long, List<Long>> = ConcurrentHashMap()

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
        val loaded = runSuspendCatching { repository.getBookWithPubDates(bookId) }.getOrNull() ?: return null
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
    ): Flow<PagingData<CommentaryWithText>> {
        val ids = commentatorId?.let { setOf(it) } ?: emptySet()

        return Pager(
            config = PagingDefaults.COMMENTS.config(placeholders = false),
            pagingSourceFactory = {
                CommentsForLineOrTocPagingSource(repository, lineId, ids)
            },
        ).flow.cachedIn(scope)
    }

    /**
     * Construit un Pager pour les liens/targum d'une ligne
     */
    fun buildLinksPager(
        lineId: Long,
        sourceBookId: Long? = null,
    ): Flow<PagingData<CommentaryWithText>> {
        val ids = sourceBookId?.let { setOf(it) } ?: emptySet()

        return Pager(
            config = PagingDefaults.COMMENTS.config(placeholders = false),
            pagingSourceFactory = {
                LineTargumPagingSource(repository, lineId, ids, setOf(ConnectionType.TARGUM))
            },
        ).flow.cachedIn(scope)
    }

    fun buildSourcesPager(
        lineId: Long,
        sourceBookId: Long? = null,
    ): Flow<PagingData<CommentaryWithText>> {
        val ids = sourceBookId?.let { setOf(it) } ?: emptySet()

        return Pager(
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
    ): Flow<PagingData<CommentaryWithText>> {
        val ids = commentatorId?.let { setOf(it) } ?: emptySet()

        return Pager(
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
    ): Flow<PagingData<CommentaryWithText>> {
        val ids = sourceBookId?.let { setOf(it) } ?: emptySet()

        return Pager(
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
    ): Flow<PagingData<CommentaryWithText>> {
        val ids = sourceBookId?.let { setOf(it) } ?: emptySet()

        return Pager(
            config = PagingDefaults.COMMENTS.config(placeholders = false),
            pagingSourceFactory = {
                MultiLineLinksPagingSource(repository, lineIds, ids, setOf(ConnectionType.SOURCE))
            },
        ).flow.cachedIn(scope)
    }

    /**
     * Récupère les commentateurs disponibles pour plusieurs lignes (union)
     */
    suspend fun getAvailableCommentatorsForLines(lineIds: List<Long>): Map<String, Long> {
        if (lineIds.isEmpty()) return emptyMap()
        return try {
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
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Récupère les groupes de commentateurs pour plusieurs lignes (union)
     */
    suspend fun getCommentatorGroupsForLines(lineIds: List<Long>): List<CommentatorGroup> {
        if (lineIds.isEmpty()) return emptyList()
        return try {
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
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Récupère les liens disponibles pour plusieurs lignes (union)
     */
    suspend fun getAvailableLinksForLines(lineIds: List<Long>): Map<String, Long> {
        if (lineIds.isEmpty()) return emptyMap()
        return try {
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
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Récupère les sources disponibles pour plusieurs lignes (union)
     */
    suspend fun getAvailableSourcesForLines(lineIds: List<Long>): Map<String, Long> {
        if (lineIds.isEmpty()) return emptyMap()
        return try {
            val map = LinkedHashMap<String, Long>()
            for (lineId in lineIds) {
                val sources = getAvailableSources(lineId)
                sources.forEach { (name, id) ->
                    if (!map.containsKey(name)) {
                        map[name] = id
                    }
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Récupère les commentateurs disponibles pour une ligne
     */
    suspend fun getAvailableCommentators(lineId: Long): Map<String, Long> {
        return try {
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
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Regroupe les commentateurs par catégorie (type) et triés par date de publication (plus ancien d'abord).
     */
    suspend fun getCommentatorGroups(lineId: Long): List<CommentatorGroup> {
        return try {
            val entries = loadCommentatorEntries(lineId)
            if (entries.isEmpty()) return emptyList()

            val categoryCache = mutableMapOf<Long, Category?>()
            groupCommentatorEntries(entries, categoryCache)
        } catch (e: Exception) {
            emptyList()
        }
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

        var currentId: Long? = book.categoryId
        while (currentId != null) {
            val category = loadCategory(currentId) ?: break
            val title = category.title

            // Prefer high-level "commentaries on ..." buckets
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

            // Broad families (e.g., חסידות, מילונים, מחברי זמננו)
            if (title == "חסידות" || title.contains("חסידות")) {
                return title
            }
            if (title.contains("מילונים")) {
                return title
            }
            if (title == "ראשונים") {
                return title
            }
            if (title == "מחברי זמננו") {
                return title
            }
            if (title == "ביאור חברותא" || title == "הערות על ביאור חברותא") {
                return "חברותא"
            }

            // Generic "מפרשים" bucket (e.g., for משנה תורה)
            if (title == "מפרשים") {
                val parent =
                    category.parentId?.let { parentId ->
                        loadCategory(parentId)
                    }
                if (parent != null && parent.title.isNotBlank()) {
                    return "מפרשים על ${parent.title}"
                }
                return title
            }

            currentId = category.parentId
        }

        val baseCategory = loadCategory(book.categoryId)
        return baseCategory?.title ?: ""
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
            val entries: List<CommentatorEntry>,
            val earliestYear: Int,
        )

        val tempGroups =
            groupsByLabel.map { (label, groupEntries) ->
                val sortedEntries =
                    groupEntries.sortedWith(
                        compareBy(
                            { if (categoryCache[it.book?.categoryId]?.title?.startsWith("הערות על") == true) 1 else 0 },
                            { it.book?.pubDates?.let { d -> extractEarliestYear(d) } ?: Int.MAX_VALUE },
                            { it.displayName },
                        ),
                    )
                val groupEarliestYear =
                    sortedEntries
                        .firstOrNull()
                        ?.book
                        ?.pubDates
                        ?.let { extractEarliestYear(it) }
                        ?: Int.MAX_VALUE

                TempGroup(
                    label = label,
                    entries = sortedEntries,
                    earliestYear = groupEarliestYear,
                )
            }

        return tempGroups
            .sortedWith(
                compareBy<TempGroup> { it.earliestYear }
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
        try {
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
        } catch (e: Exception) {
            emptyMap()
        }

    suspend fun getAvailableSources(lineId: Long): Map<String, Long> =
        try {
            val baseIds = resolveBaseLineIds(lineId)
            val links =
                repository
                    .getCommentarySummariesForLines(baseIds)
                    .filter { it.link.connectionType == ConnectionType.SOURCE }

            val currentBookTitle =
                stateManager.state
                    .first()
                    .navigation.selectedBook
                    ?.title
                    ?.trim()
                    .orEmpty()

            buildSourceMap(links, currentBookTitle)
        } catch (e: Exception) {
            emptyMap()
        }

    suspend fun loadLineConnections(lineIds: List<Long>): Map<Long, LineConnectionsSnapshot> {
        if (lineIds.isEmpty()) return emptyMap()

        val distinctIds = lineIds.distinct()
        val resolutionCache = LinkedHashMap<Long, BaseLineResolution>()
        val tocLinesCache = mutableMapOf<Long, List<Long>>()
        val headingCache = mutableMapOf<Long, TocEntry?>()

        distinctIds.forEach { id ->
            resolutionCache[id] = resolveBaseLineResolution(id, tocLinesCache, headingCache)
        }

        val allBaseIds = resolutionCache.values.flatMap { it.baseLineIds }.distinct()
        if (allBaseIds.isEmpty()) return distinctIds.associateWith { LineConnectionsSnapshot() }

        val allConnections = repository.getCommentarySummariesForLines(allBaseIds)
        if (allConnections.isEmpty()) return distinctIds.associateWith { LineConnectionsSnapshot() }

        val connectionsBySource = allConnections.groupBy { it.link.sourceLineId }
        val currentState = stateManager.state.first()
        val currentBookTitle =
            currentState.navigation.selectedBook
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

        return resolutionCache.mapValues { (_, resolution) ->
            val aggregated = resolution.baseLineIds.flatMap { baseId -> connectionsBySource[baseId].orEmpty() }
            val defaultTargumId = resolution.headingBookId?.let { defaultTargumByBookId[it] }
            val filtered = filterTargumConnections(aggregated, resolution, defaultTargumId)
            buildLineConnectionsSnapshot(filtered, currentBookTitle, bookCache, categoryCache)
        }
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

        val limited = defaults.take(MAX_COMMENTATORS).toSet()

        stateManager.updateContent {
            val byBook = selectedCommentatorsByBook.toMutableMap()
            byBook[bookId] = limited
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

            val desired = mutableListOf<Long>()
            for ((_, id) in available) {
                if (id in sticky) desired.add(id)
                if (desired.size >= MAX_COMMENTATORS) break
            }

            if (desired.isNotEmpty()) {
                updateSelectedCommentatorsForLine(line.id, desired.toSet())
            }
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
        } catch (e: Exception) {
            // Ignorer les erreurs silencieusement
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

            val desired = mutableListOf<Long>()
            for ((_, id) in available) {
                if (id in sticky) desired.add(id)
                if (desired.size >= MAX_COMMENTATORS) break
            }

            if (desired.isNotEmpty()) {
                updateSelectedCommentatorsForLine(primaryLineId, desired.toSet())
            }
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
        stateManager.updateContent {
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
