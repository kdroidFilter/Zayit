package io.github.kdroidfilter.seforimapp.cataloggen

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Code generator that reads the SQLite DB and emits a Kotlin object with
 * precomputed titles and mappings used by the app UI.
 *
 * Usage (via Gradle task):
 *   ./gradlew :cataloggen:generatePrecomputedCatalog
 */
fun main(args: Array<String>) {
    require(args.size >= 2) { "Expected arguments: <dbPath> <outputDir>" }
    val dbPath = args[0]
    val outputDir = File(args[1])

    val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
    val repo = SeforimRepository(dbPath, driver)

    // Only include categories used in the current UI
    val categoriesOfInterest = setOf<Long>(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 60)
    val categoryTitles: MutableMap<Long, String> = mutableMapOf()
    runBlocking {
        categoriesOfInterest.forEach { cid ->
            runCatching { repo.getCategory(cid) }.getOrNull()?.let { categoryTitles[cid] = it.title }
        }
    }

    // Collect books per category and book titles (strip display titles by category label)
    val bookTitles: MutableMap<Long, String> = mutableMapOf()
    val categoryBooks: MutableMap<Long, List<Pair<Long, String>>> = mutableMapOf()
    runBlocking {
        categoryTitles.keys.forEach { cid ->
            val books = runCatching { repo.getBooksByCategory(cid) }.getOrDefault(emptyList())
            // Prefer stripping both the current category label and the root label,
            // to support cases like "שולחן ערוך, ..." and "תלמוד ירושלמי ...".
            val categoryLabel = categoryTitles[cid]
            val rootLabel = rootCategoryTitle(repo, cid)
            val labels = listOfNotNull(categoryLabel, rootLabel).distinct()
            val refs = books.map { b ->
                bookTitles[b.id] = b.title
                val display = stripAnyLabelPrefix(labels, b.title)
                b.id to display
            }
            categoryBooks[cid] = refs
        }
    }

    // Collect per-book TOC-textId → (label, tocEntryId, firstLineId) for books we use in UI
    val tocByTocTextId: MutableMap<Long, Map<Long, Triple<String, Long, Long?>>> = mutableMapOf()
    val booksOfInterest = setOf<Long>(59)
    val tocTextIdsOfInterest = setOf<Long>(3455)
    runBlocking {
        booksOfInterest.forEach { bookId ->
            val toc = runCatching { repo.getBookToc(bookId) }.getOrDefault(emptyList())
            val mappings = runCatching { repo.getLineTocMappingsForBook(bookId) }.getOrDefault(emptyList())
            val firstLineByToc: MutableMap<Long, Long> = mutableMapOf()
            mappings.forEach { m -> if (!firstLineByToc.containsKey(m.tocEntryId)) firstLineByToc[m.tocEntryId] = m.lineId }
            val map = mutableMapOf<Long, Triple<String, Long, Long?>>()
            for (e in toc) {
                val txId = e.textId ?: continue
                if (!tocTextIdsOfInterest.contains(txId)) continue
                val label = e.text
                if (label.isBlank()) continue
                map[txId] = Triple(label, e.id, firstLineByToc[e.id])
            }
            if (map.isNotEmpty()) tocByTocTextId[bookId] = map
        }
    }

    // Emit Kotlin file
    val pkg = "io.github.kdroidfilter.seforimapp.catalog"
    val fileSpecBuilder = FileSpec.builder(pkg, "PrecomputedCatalog")
    // Top-level helper types
    val bookRef = TypeSpec.classBuilder("BookRef")
        .addModifiers(KModifier.DATA)
        .primaryConstructor(FunSpec.constructorBuilder()
            .addParameter("id", LONG)
            .addParameter("title", STRING)
            .build())
        .addProperty(PropertySpec.builder("id", LONG).initializer("id").build())
        .addProperty(PropertySpec.builder("title", STRING).initializer("title").build())
        .build()
    val tocQL = TypeSpec.classBuilder("TocQuickLink")
        .addModifiers(KModifier.DATA)
        .primaryConstructor(FunSpec.constructorBuilder()
            .addParameter("label", STRING)
            .addParameter("tocEntryId", LONG)
            .addParameter("firstLineId", LONG.copy(nullable = true))
            .build())
        .addProperty(PropertySpec.builder("label", STRING).initializer("label").build())
        .addProperty(PropertySpec.builder("tocEntryId", LONG).initializer("tocEntryId").build())
        .addProperty(PropertySpec.builder("firstLineId", LONG.copy(nullable = true)).initializer("firstLineId").build())
        .build()
    fileSpecBuilder.addType(bookRef).addType(tocQL)

    val catalogObject = buildCatalogType(pkg, bookTitles, categoryTitles, categoryBooks, tocByTocTextId)
    val fileSpec = fileSpecBuilder.addType(catalogObject)
        .build()

    outputDir.mkdirs()
    fileSpec.writeTo(outputDir)
}

private fun collectCategoryTitles(repo: SeforimRepository, parentId: Long, out: MutableMap<Long, String>) {
    runBlocking {
        val children = runCatching { repo.getCategoryChildren(parentId) }.getOrDefault(emptyList())
        children.forEach { c ->
            out[c.id] = c.title
            collectCategoryTitles(repo, c.id, out)
        }
    }
}

private fun rootCategoryTitle(repo: SeforimRepository, categoryId: Long): String = runBlocking {
    var cur = runCatching { repo.getCategory(categoryId) }.getOrNull()
    var lastTitle: String? = cur?.title
    var guard = 0
    while (cur?.parentId != null && guard++ < 50) {
        cur = runCatching { repo.getCategory(cur.parentId!!) }.getOrNull()
        if (cur?.title != null) lastTitle = cur.title
    }
    lastTitle ?: ""
}

private fun buildCatalogType(
    pkg: String,
    bookTitles: Map<Long, String>,
    categoryTitles: Map<Long, String>,
    categoryBooks: Map<Long, List<Pair<Long, String>>>,
    tocByTocTextId: Map<Long, Map<Long, Triple<String, Long, Long?>>>
): TypeSpec {
    val builder = TypeSpec.objectBuilder("PrecomputedCatalog")

    // BOOK_TITLES
    val btCode = CodeBlock.builder().add("mapOf(\n")
    bookTitles.entries.sortedBy { it.key }.forEach { (id, title) ->
        btCode.add("  %LL to %S,\n", id, title)
    }
    btCode.add(")")
    builder.addProperty(PropertySpec.builder("BOOK_TITLES", MAP.parameterizedBy(LONG, STRING))
        .initializer(btCode.build()).build())

    // CATEGORY_TITLES
    val ctCode = CodeBlock.builder().add("mapOf(\n")
    categoryTitles.entries.sortedBy { it.key }.forEach { (id, title) ->
        ctCode.add("  %LL to %S,\n", id, title)
    }
    ctCode.add(")")
    builder.addProperty(PropertySpec.builder("CATEGORY_TITLES", MAP.parameterizedBy(LONG, STRING))
        .initializer(ctCode.build()).build())

    // CATEGORY_BOOKS
    val bookRefType = ClassName(pkg, "BookRef")
    val listBookRef = LIST.parameterizedBy(bookRefType)
    val mapCatBooks = MAP.parameterizedBy(LONG, listBookRef)
    val cbCode = CodeBlock.builder().add("mapOf(\n")
    categoryBooks.entries.sortedBy { it.key }.forEach { (cid, refs) ->
        cbCode.add("  %LL to listOf(", cid)
        refs.forEachIndexed { idx, (bid, btitle) ->
            if (idx > 0) cbCode.add(", ")
            cbCode.add("BookRef(%LL, %S)", bid, btitle)
        }
        cbCode.add(") ,\n")
    }
    cbCode.add(")")
    builder.addProperty(PropertySpec.builder("CATEGORY_BOOKS", mapCatBooks)
        .initializer(cbCode.build()).build())

    // TOC_BY_TOC_TEXT_ID
    val tocQLType = ClassName(pkg, "TocQuickLink")
    val innerMap = MAP.parameterizedBy(LONG, tocQLType)
    val tocMapType = MAP.parameterizedBy(LONG, innerMap)
    val tocCode = CodeBlock.builder().add("mapOf(\n")
    tocByTocTextId.entries.sortedBy { it.key }.forEach { (bookId, inner) ->
        tocCode.add("  %LL to mapOf(", bookId)
        inner.entries.forEachIndexed { idx, (tx, triple) ->
            if (idx > 0) tocCode.add(", ")
            val (label, tocEntryId, firstLineId) = triple
            tocCode.add("%LL to TocQuickLink(%S, %LL, %L)", tx, label, tocEntryId, firstLineId)
        }
        tocCode.add(") ,\n")
    }
    tocCode.add(")")
    builder.addProperty(PropertySpec.builder("TOC_BY_TOC_TEXT_ID", tocMapType)
        .initializer(tocCode.build()).build())

    return builder.build()
}

private val STRING = String::class.asClassName()
private val LONG = Long::class.asClassName()
private val LIST = ClassName("kotlin.collections", "List")
private val MAP = ClassName("kotlin.collections", "Map")

private fun stripLabelPrefix(label: String, title: String): String {
    if (label.isBlank()) return title
    val prefix = Regex.escape(label)
    val patterns = listOf(
        Regex("^$prefix\\s*,\\s*"),  // label + comma
        Regex("^$prefix,\\s*"),       // label,comma
        Regex("^$prefix\\s*[:\u2013\u2014-]\\s*"), // label + colon/en/em dash/hyphen
        Regex("^$prefix\\s*\\+\\s*"), // label + plus
        Regex("^$prefix\\s+")         // label + space
    )
    for (p in patterns) {
        val replaced = title.replaceFirst(p, "")
        if (replaced !== title) return replaced.trimStart()
    }
    return title
}

private fun stripAnyLabelPrefix(labels: List<String>, title: String): String {
    var result = title
    for (lbl in labels) {
        result = stripLabelPrefix(lbl, result)
    }
    return result
}

private inline fun <K, V, R> Iterable<Map.Entry<K, V>>.associateNotNull(transform: (Map.Entry<K, V>) -> R?): Map<K, R> {
    val dest = LinkedHashMap<K, R>()
    for (e in this) {
        val v = transform(e) ?: continue
        dest[e.key] = v
    }
    return dest
}
