package io.github.kdroidfilter.seforimapp.framework.update

import io.github.kdroidfilter.seforimlibrary.deltaupdater.DeltaUpdaterClient
import io.github.kdroidfilter.seforimlibrary.deltaupdater.LuceneUpdater
import io.github.kdroidfilter.seforimlibrary.deltaupdater.UpdatePath
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.IntPoint
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * SeforimApp's facade over the seforim.db delta-update flow.
 *
 *  - At app startup, call [recoverIfNeeded] BEFORE opening the SQLDelight
 *    repository: if the previous launch crashed mid-apply, the backup is
 *    restored before any client code touches the DB.
 *  - Periodically (or on user demand), call [checkAndApply]; if a chain
 *    of deltas is available, it's downloaded and applied. The live
 *    Lucene index is informed of the upserted / deleted line ids via
 *    caller-supplied sinks so the app stays in lock-step.
 *
 * Constructed once at app boot. Thread-safe to call from a background
 * coroutine.
 */
class DbDeltaUpdateService(
    private val seforimDb: Path,
    private val catalogPb: Path,
    private val workDir: Path,
    private val releaseMetaUrl: String,
    private val localDbVersionProvider: () -> Int,
    private val luceneIndexDir: Path? = null,
    private val luceneSinksProvider: () -> Pair<LuceneUpdater.DeleteSink, LuceneUpdater.UpsertSink> =
        defaultLuceneSinksProvider(luceneIndexDir),
) {

    private val log = LoggerFactory.getLogger(DbDeltaUpdateService::class.java)

    private val client by lazy {
        DeltaUpdaterClient(
            seforimDb = seforimDb,
            catalogPb = catalogPb,
            workDir = workDir,
            releaseMetaUrl = releaseMetaUrl,
            indexSinks = luceneSinksProvider,
            localVersionProvider = localDbVersionProvider,
        )
    }

    /**
     * Must be called BEFORE opening the SQLDelight repository at app
     * startup. If a marker file is present alongside seforim.db, the
     * file-level backup is restored.
     *
     * @return `true` if recovery happened, `false` if no marker was present.
     */
    fun recoverIfNeeded(): Boolean {
        val recovered = client.recoverIfNeeded()
        if (recovered) log.warn("Recovered from a half-applied delta update: live DB rolled back to backup.")
        return recovered
    }

    /**
     * Polls the release server and applies any available chain. Reports
     * progress via [onProgress] as `current/total: status`.
     */
    suspend fun checkAndApply(
        onProgress: (current: Int, total: Int, status: String) -> Unit = { _, _, _ -> },
    ): Outcome {
        return when (val path = client.checkForUpdate()) {
            UpdatePath.UpToDate -> Outcome.UpToDate
            is UpdatePath.FullBundle -> Outcome.NeedsFullBundle
            is UpdatePath.Chain -> {
                client.applyChain(path.deltas, onProgress)
                Outcome.Applied(path.deltas.size)
            }
        }
    }

    sealed interface Outcome {
        data object UpToDate : Outcome
        data class Applied(val deltaCount: Int) : Outcome
        data object NeedsFullBundle : Outcome
    }

    companion object {
        // Match the field names used by the generator's LuceneTextIndexWriter
        // so the index produced here stays compatible with the live search.
        private const val FIELD_TYPE = "type"
        private const val TYPE_LINE = "line"
        private const val FIELD_BOOK_ID = "book_id"
        private const val FIELD_CATEGORY_ID = "category_id"
        private const val FIELD_LINE_ID = "line_id"
        private const val FIELD_LINE_INDEX = "line_index"
        private const val FIELD_TEXT = "text"
        private const val FIELD_BOOK_TITLE = "book_title"
        private const val FIELD_ORDER_INDEX = "order_index"
        private const val FIELD_IS_BASE_BOOK = "is_base_book"

        /**
         * Default Lucene sinks: if a [luceneIndexDir] is supplied, open a
         * fresh IndexWriter on each `applyChain` call and route the patch's
         * delete/upsert line ops into it. The writer is opened on first use,
         * committed + closed when the calling scope ends.
         */
        fun defaultLuceneSinksProvider(
            luceneIndexDir: Path?,
        ): () -> Pair<LuceneUpdater.DeleteSink, LuceneUpdater.UpsertSink> = {
            if (luceneIndexDir == null) {
                LuceneUpdater.DeleteSink { } to LuceneUpdater.UpsertSink { }
            } else {
                val dir = FSDirectory.open(luceneIndexDir)
                val writer = IndexWriter(dir, IndexWriterConfig(StandardAnalyzer()))
                LuceneUpdater.DeleteSink { id ->
                    writer.deleteDocuments(IntPoint.newExactQuery(FIELD_LINE_ID, id.toInt()))
                } to LuceneUpdater.UpsertSink { line ->
                    // Replace prior doc(s) for this line_id, then add the new one.
                    writer.deleteDocuments(IntPoint.newExactQuery(FIELD_LINE_ID, line.id.toInt()))
                    val doc = Document().apply {
                        add(Field(FIELD_TYPE, TYPE_LINE, org.apache.lucene.document.StringField.TYPE_STORED))
                        add(IntPoint(FIELD_BOOK_ID, line.bookId.toInt()))
                        add(StoredField(FIELD_BOOK_ID, line.bookId))
                        add(IntPoint(FIELD_CATEGORY_ID, 0))
                        add(StoredField(FIELD_CATEGORY_ID, 0L))
                        add(IntPoint(FIELD_LINE_ID, line.id.toInt()))
                        add(StoredField(FIELD_LINE_ID, line.id))
                        add(StoredField(FIELD_LINE_INDEX, line.lineIndex.toLong()))
                        add(TextField(FIELD_TEXT, line.content, Field.Store.NO))
                        add(StoredField(FIELD_BOOK_TITLE, ""))
                        add(StoredField(FIELD_ORDER_INDEX, 999L))
                        add(StoredField(FIELD_IS_BASE_BOOK, 0L))
                    }
                    writer.addDocument(doc)
                }
            }
        }
    }
}
