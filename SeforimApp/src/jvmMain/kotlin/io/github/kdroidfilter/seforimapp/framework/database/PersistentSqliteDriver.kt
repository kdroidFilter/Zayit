package io.github.kdroidfilter.seforimapp.framework.database

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.JdbcCursor
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.JdbcPreparedStatement
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.Properties

/**
 * SQLite JDBC driver backed by a single persistent connection with a per-identifier
 * [PreparedStatement] cache.
 *
 * Profiling (JFR 2026-04-23) showed the stock [app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver]
 * routing every non-transactional query through `ThreadedConnectionManager` — which
 * closes and re-opens the SQLite connection after each call, paying `sqlite3_open`
 * + `sqlite3_prepare` + `sqlite3_close` per query. On a read-only reader workload
 * that is pure waste: the reader corpus is immutable, so one connection suffices
 * and every repeatedly-issued query benefits from a cached prepared statement.
 *
 * Design:
 *  - A single [Connection] opened at construction time, kept alive until [close].
 *  - `getConnection()` returns that connection; `closeConnection()` is a no-op.
 *  - Read-tuning PRAGMAs applied once at init (WAL / NORMAL sync / page+mmap caches).
 *  - Prepared-statement cache keyed by SqlDelight's `identifier: Int` — the same
 *    integer it passes on every generated query call. Statements are synchronized
 *    on the single connection because SQLite's JDBC connection is not thread-safe.
 *
 * Transactions are handled by [JdbcDriver]'s base `autoCommit` machinery; we just
 * ensure our cached prepared statements aren't handed out while another thread holds
 * the connection by synchronizing the execute methods on `connection`.
 */
class PersistentSqliteDriver(
    url: String,
    properties: Properties = Properties(),
) : JdbcDriver() {
    private val connection: Connection = DriverManager.getConnection(url, properties)

    // Statement cache keyed by SQL text — SqlDelight sometimes reuses the same
    // `identifier` for queries whose SQL varies (e.g. `IN (?,?,?)` with variable
    // arity), so we avoid crashes by keying on the actual SQL string. Bounded in
    // practice: a few hundred distinct queries across the whole app. Never evicted.
    private val statementCache = HashMap<String, PreparedStatement>()

    init {
        connection.autoCommit = true
        // Don't apply PRAGMAs here: `SeforimRepository.init` already issues its own
        // tuned set (256 MB cache / 512 MB mmap). Running them twice while the repo
        // still holds open cursors from its schema-create pass triggers SQLITE_BUSY.
    }

    override fun getConnection(): Connection = connection

    override fun closeConnection(connection: Connection) = Unit

    override fun close() {
        synchronized(connection) {
            statementCache.values.forEach { runCatching { it.close() } }
            statementCache.clear()
            connection.close()
        }
    }

    override fun addListener(
        vararg queryKeys: String,
        listener: Query.Listener,
    ) = Unit

    override fun removeListener(
        vararg queryKeys: String,
        listener: Query.Listener,
    ) = Unit

    override fun notifyListeners(vararg queryKeys: String) = Unit

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        synchronized(connection) {
            val stmt = prepare(sql)
            stmt.clearParameters()
            if (binders != null) JdbcPreparedStatement(stmt).binders()
            val hasResultSet = stmt.execute()
            val rows =
                if (hasResultSet) {
                    // Drain any result set to release the statement's cursor so the next
                    // call (e.g. a subsequent PRAGMA) doesn't hit SQLITE_BUSY.
                    stmt.resultSet?.close()
                    0L
                } else {
                    stmt.updateCount.toLong()
                }
            return QueryResult.Value(rows)
        }
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        synchronized(connection) {
            val stmt = prepare(sql)
            stmt.clearParameters()
            if (binders != null) JdbcPreparedStatement(stmt).binders()
            // Inlined from `JdbcPreparedStatement.executeQuery`, minus the `preparedStatement.close()`
            // in its `finally` — closing would defeat the whole point of caching. We close the
            // ResultSet via `.use { }` instead so SQLite frees the cursor for the next call.
            stmt.executeQuery().use { rs ->
                return mapper(JdbcCursor(rs))
            }
        }
    }

    /**
     * Returns a cached [PreparedStatement] for [sql], preparing it lazily on first
     * use. Caller must hold the connection's monitor.
     */
    private fun prepare(sql: String): PreparedStatement {
        statementCache[sql]?.let { cached ->
            if (!cached.isClosed) return cached
            statementCache.remove(sql)
        }
        val fresh = connection.prepareStatement(sql)
        statementCache[sql] = fresh
        return fresh
    }
}
