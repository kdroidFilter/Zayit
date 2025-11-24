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

    private val byToken: Map<String, List<Expansion>>

    init {
        // Group all entries by their normalized base form
        val termsByBase = mutableMapOf<String, MutableSet<String>>()

        for (e in entries) {
            val surfaceN = norm(e.surface)
            if (surfaceN.isEmpty()) continue
            val variantsN = e.variants.mapNotNull { v -> norm(v).takeIf { it.isNotEmpty() } }
            val baseN = norm(e.base).takeIf { it.isNotEmpty() } ?: surfaceN

            // Collect all terms (surface + variants + base) under this base
            val allTerms = termsByBase.getOrPut(baseN) { mutableSetOf() }
            allTerms.add(surfaceN)
            allTerms.addAll(variantsN)
            allTerms.add(baseN)
        }

        // Build map: term -> list of expansions (one per base it belongs to)
        val map = mutableMapOf<String, MutableList<Expansion>>()

        for ((baseN, allTerms) in termsByBase) {
            val termsList = allTerms.toList()

            // Create expansion for this base group
            val exp = Expansion(
                surface = termsList,
                variants = emptyList(),
                base = listOf(baseN)
            )

            // Map every term in this group to this expansion
            for (term in allTerms) {
                map.getOrPut(term) { mutableListOf() }.add(exp)
            }
        }

        byToken = map.mapValues { it.value.toList() }.toMap()

        println("[MagicDictionary] Loaded ${termsByBase.size} base groups with ${map.size} total terms")
    }

    fun expansionsFor(tokens: List<String>): List<Expansion> =
        tokens.flatMap { byToken[it] ?: emptyList() }.distinct()

    fun expansionFor(token: String): Expansion? {
        val expansions = byToken[token] ?: return null
        if (expansions.isEmpty()) return null

        // Strategy: prefer the expansion whose base matches the token
        val matchingBase = expansions.firstOrNull { exp ->
            exp.base.any { it == token }
        }
        if (matchingBase != null) return matchingBase

        // Otherwise, prefer the largest expansion (more terms = more complete paradigm)
        return expansions.maxByOrNull { it.surface.size }
    }

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
