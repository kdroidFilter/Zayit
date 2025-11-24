package io.github.kdroidfilter.seforimapp.framework.search

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * Lightweight index for the three-level dictionary stored in a SQLite DB
 * (tables: surface, variant, base). Priority order: surface > variant > base.
 * All strings are normalized with the same Hebrew normalizer as queries.
 */
class MagicDictionaryIndex(
    private val norm: (String) -> String,
    entries: List<Entry>
) {
    data class Entry(
        val surface: String,
        val base: String,
        val variants: List<String>
    )

    data class Expansion(
        val surface: List<String>,
        val variants: List<String>,
        val base: List<String>
    )

    private val byToken: Map<String, Expansion>

    init {
        val map = mutableMapOf<String, Expansion>()
        for (e in entries) {
            val surfaceN = norm(e.surface)
            if (surfaceN.isEmpty()) continue
            val variantsN = e.variants.mapNotNull { v -> norm(v).takeIf { it.isNotEmpty() } }
            val baseN = norm(e.base)
            val exp = Expansion(
                surface = listOf(surfaceN),
                variants = variantsN.distinct(),
                base = if (baseN.isNotEmpty()) listOf(baseN) else emptyList()
            )
            for (token in exp.surface + exp.variants + exp.base) {
                map[token] = exp
            }
        }
        byToken = map.toMap()
    }

    fun expansionsFor(tokens: List<String>): List<Expansion> =
        tokens.mapNotNull { byToken[it] }

    companion object {
        /**
         * Load from SQLite DB (expected tables: surface(value, base_id), variant(value, surface_id), base(value)).
         */
        fun load(norm: (String) -> String, candidate: Path?): MagicDictionaryIndex? {
            val file = candidate?.takeIf { Files.isRegularFile(it) } ?: return null
            val url = "jdbc:sqlite:${file.toAbsolutePath()}"
            return runCatching {
                DriverManager.getConnection(url).use { conn ->
                    val entries = fetchEntries(conn)
                    MagicDictionaryIndex(norm, entries)
                }
            }.onFailure {
                println("[MagicDictionary] Failed to load from $file : ${it.message}")
            }.getOrNull()
        }

        private fun fetchEntries(conn: Connection): List<Entry> {
            val entries = mutableListOf<Entry>()
            val sql = """
                SELECT s.value as surface, b.value as base, group_concat(v.value, X'1F') as variants
                FROM surface s
                JOIN base b ON b.id = s.base_id
                LEFT JOIN variant v ON v.surface_id = s.id
                GROUP BY s.id
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val surface = rs.getString("surface") ?: ""
                    val base = rs.getString("base") ?: ""
                    val variantsRaw = rs.getString("variants") ?: ""
                    val variants = if (variantsRaw.isBlank()) emptyList() else variantsRaw.split("\u001F")
                    entries += Entry(surface = surface, base = base, variants = variants)
                }
            }
            return entries
        }
    }
}
