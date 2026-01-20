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
 *   With env var (recommended):
 *     SEFORIM_DB=/path/to/seforim.db ./gradlew :cataloggen:generatePrecomputedCatalog --args="<outputDir>"
 *   Legacy (without env var):
 *     ./gradlew :cataloggen:generatePrecomputedCatalog --args="<dbPath> <outputDir>"
 */
fun main(args: Array<String>) {
    val envDb = System.getenv("SEFORIM_DB")?.takeIf { it.isNotBlank() }

    // Supported invocation modes:
    //  - 2 args: <dbPath> <outputDir> (legacy)
    //  - 1 arg and SEFORIM_DB set: <outputDir>
    val (dbPath, outputDirPath) =
        when {
            args.size >= 2 -> args[0] to args[1]
            args.size == 1 && envDb != null -> envDb to args[0]
            else ->
                error(
                    """
                    Invalid arguments. Supported usages:\n
                    1) With env var (recommended):
                       SEFORIM_DB=/path/to/seforim.db <command> --args=\"<outputDir>\"

                    2) Legacy (no env var):
                       <command> --args=\"<dbPath> <outputDir>\"\n
                    Received ${args.size} arg(s), SEFORIM_DB set=${envDb != null}.
                    """.trimIndent(),
                )
        }
    println(dbPath)
    val outputDir = File(outputDirPath)

    val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
    val repo = SeforimRepository(dbPath, driver)

    val resolvedIds = runBlocking { resolveCatalogIds(repo) }

    // Only include categories used in the current UI (resolved dynamically from the DB)
    val categoriesOfInterest: Set<Long> =
        resolvedIds.categoryIds.values
            .plus(resolvedIds.mishnehTorahChildren)
            .toSet()
    val categoryTitles: MutableMap<Long, String> = mutableMapOf()
    runBlocking {
        categoriesOfInterest.forEach { cid ->
            runCatching { repo.getCategory(cid) }.getOrNull()?.let { categoryTitles[cid] = it.title }
        }
        // Preserve legacy display labels for Talmud children (תלמוד בבלי / תלמוד ירושלמי)
        val bavliId = resolvedIds.categoryIds["BAVLI"]
        val yerushalmiId = resolvedIds.categoryIds["YERUSHALMI"]
        if (bavliId != null || yerushalmiId != null) {
            val bavliParentTitle =
                bavliId?.let { id ->
                    runCatching { repo.getCategory(id)?.parentId?.let { pid -> repo.getCategory(pid)?.title } }.getOrNull()
                }
            val prefix = bavliParentTitle?.takeIf { it.isNotBlank() } ?: "תלמוד"
            bavliId?.let { id ->
                val current = categoryTitles[id] ?: "בבלי"
                categoryTitles[id] = "$prefix $current"
            }
            yerushalmiId?.let { id ->
                val current = categoryTitles[id] ?: "ירושלמי"
                categoryTitles[id] = "$prefix $current"
            }
        }
    }

    // Collect books per category and book titles (strip display titles by category label)
    val bookTitles: MutableMap<Long, String> = mutableMapOf()
    val categoryBooks: MutableMap<Long, List<Pair<Long, String>>> = mutableMapOf()
    val mishnehTorahId = resolvedIds.categoryIds.getValue("MISHNE_TORAH")
    runBlocking {
        categoryTitles.keys.forEach { cid ->
            var books = runCatching { repo.getBooksByCategory(cid) }.getOrDefault(emptyList())
            // For Mishneh Torah (root or its immediate children), exclude books starting with "מפרשים"
            val parentId = runCatching { repo.getCategory(cid) }.getOrNull()?.parentId
            val isMishnehTorahContext = (cid == mishnehTorahId) || (parentId == mishnehTorahId)
            if (isMishnehTorahContext) {
                books = books.filter { b -> !b.title.trimStart().startsWith("מפרשים") }
            }
            // Strip any ancestor labels (category, parent, root, etc.) to avoid repetition like "משנה תורה, ..."
            val labels = ancestorTitles(repo, cid)
            val refs =
                books.map { b ->
                    bookTitles[b.id] = b.title
                    val display = stripAnyLabelPrefix(labels, b.title)
                    b.id to display
                }
            categoryBooks[cid] = refs
        }
    }

    // Collect per-book TOC-textId → (label, tocEntryId, firstLineId) for books we use in UI
    val tocByTocTextId: MutableMap<Long, Map<Long, Triple<String, Long, Long?>>> = mutableMapOf()
    val booksOfInterest = resolvedIds.bookIds.values.toSet()
    val tocTextIdsOfInterest = resolvedIds.tocTextIds.values.toSet()
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
    val fileSpecBuilder =
        FileSpec
            .builder(pkg, "PrecomputedCatalog")
            .addFileComment(
                """
                DO NOT EDIT.
                This file is auto-generated by the catalog generator.
                To regenerate: ./gradlew :cataloggen:generatePrecomputedCatalog
                Manual changes will be lost.
                """.trimIndent(),
            )
    // Top-level helper types
    val bookRef =
        TypeSpec
            .classBuilder("BookRef")
            .addModifiers(KModifier.DATA)
            .primaryConstructor(
                FunSpec
                    .constructorBuilder()
                    .addParameter("id", LONG)
                    .addParameter("title", STRING)
                    .build(),
            ).addProperty(PropertySpec.builder("id", LONG).initializer("id").build())
            .addProperty(PropertySpec.builder("title", STRING).initializer("title").build())
            .build()
    val tocQL =
        TypeSpec
            .classBuilder("TocQuickLink")
            .addModifiers(KModifier.DATA)
            .primaryConstructor(
                FunSpec
                    .constructorBuilder()
                    .addParameter("label", STRING)
                    .addParameter("tocEntryId", LONG)
                    .addParameter("firstLineId", LONG.copy(nullable = true))
                    .build(),
            ).addProperty(PropertySpec.builder("label", STRING).initializer("label").build())
            .addProperty(PropertySpec.builder("tocEntryId", LONG).initializer("tocEntryId").build())
            .addProperty(PropertySpec.builder("firstLineId", LONG.copy(nullable = true)).initializer("firstLineId").build())
            .build()
    // Polymorphic dropdown descriptors
    val dropdownSpec =
        TypeSpec
            .interfaceBuilder("DropdownSpec")
            .addModifiers(KModifier.SEALED)
            .build()
    val categorySpec =
        TypeSpec
            .classBuilder("CategoryDropdownSpec")
            .addModifiers(KModifier.DATA)
            .addSuperinterface(ClassName(pkg, "DropdownSpec"))
            .primaryConstructor(
                FunSpec
                    .constructorBuilder()
                    .addParameter("categoryId", LONG)
                    .build(),
            ).addProperty(PropertySpec.builder("categoryId", LONG).initializer("categoryId").build())
            .build()
    val multiCategorySpec =
        TypeSpec
            .classBuilder("MultiCategoryDropdownSpec")
            .addModifiers(KModifier.DATA)
            .addSuperinterface(ClassName(pkg, "DropdownSpec"))
            .primaryConstructor(
                FunSpec
                    .constructorBuilder()
                    .addParameter("labelCategoryId", LONG)
                    .addParameter("bookCategoryIds", LIST.parameterizedBy(LONG))
                    .build(),
            ).addProperty(PropertySpec.builder("labelCategoryId", LONG).initializer("labelCategoryId").build())
            .addProperty(PropertySpec.builder("bookCategoryIds", LIST.parameterizedBy(LONG)).initializer("bookCategoryIds").build())
            .build()
    val tocQuickLinksSpec =
        TypeSpec
            .classBuilder("TocQuickLinksSpec")
            .addModifiers(KModifier.DATA)
            .addSuperinterface(ClassName(pkg, "DropdownSpec"))
            .primaryConstructor(
                FunSpec
                    .constructorBuilder()
                    .addParameter("bookId", LONG)
                    .addParameter("tocTextIds", LIST.parameterizedBy(LONG))
                    .build(),
            ).addProperty(PropertySpec.builder("bookId", LONG).initializer("bookId").build())
            .addProperty(PropertySpec.builder("tocTextIds", LIST.parameterizedBy(LONG)).initializer("tocTextIds").build())
            .build()
    fileSpecBuilder
        .addType(bookRef)
        .addType(tocQL)
        .addType(dropdownSpec)
        .addType(categorySpec)
        .addType(multiCategorySpec)
        .addType(tocQuickLinksSpec)

    val mishnehTorahChildrenIds: List<Long> = resolvedIds.mishnehTorahChildren

    val catalogObject =
        buildCatalogType(
            pkg,
            bookTitles,
            categoryTitles,
            categoryBooks,
            tocByTocTextId,
            mishnehTorahChildrenIds,
            resolvedIds,
        )
    val fileSpec =
        fileSpecBuilder
            .addType(catalogObject)
            .build()

    outputDir.mkdirs()
    fileSpec.writeTo(outputDir)
}

private fun collectCategoryTitles(
    repo: SeforimRepository,
    parentId: Long,
    out: MutableMap<Long, String>,
) {
    runBlocking {
        val children = runCatching { repo.getCategoryChildren(parentId) }.getOrDefault(emptyList())
        children.forEach { c ->
            out[c.id] = c.title
            collectCategoryTitles(repo, c.id, out)
        }
    }
}

private fun rootCategoryTitle(
    repo: SeforimRepository,
    categoryId: Long,
): String =
    runBlocking {
        var cur = runCatching { repo.getCategory(categoryId) }.getOrNull()
        var lastTitle: String? = cur?.title
        var guard = 0
        while (cur?.parentId != null && guard++ < 50) {
            cur = runCatching { repo.getCategory(cur.parentId!!) }.getOrNull()
            if (cur?.title != null) lastTitle = cur.title
        }
        lastTitle ?: ""
    }

private fun ancestorTitles(
    repo: SeforimRepository,
    categoryId: Long,
): List<String> =
    runBlocking {
        val labels = mutableListOf<String>()
        var cur = runCatching { repo.getCategory(categoryId) }.getOrNull()
        if (cur?.title != null) labels += cur.title
        var guard = 0
        while (cur?.parentId != null && guard++ < 50) {
            cur = runCatching { repo.getCategory(cur.parentId!!) }.getOrNull()
            val t = cur?.title
            if (!t.isNullOrBlank()) labels += t
        }
        labels.distinct()
    }

private fun buildCatalogType(
    pkg: String,
    bookTitles: Map<Long, String>,
    categoryTitles: Map<Long, String>,
    categoryBooks: Map<Long, List<Pair<Long, String>>>,
    tocByTocTextId: Map<Long, Map<Long, Triple<String, Long, Long?>>>,
    mishnehTorahChildrenIds: List<Long>,
    resolvedIds: ResolvedCatalogIds,
): TypeSpec {
    val builder = TypeSpec.objectBuilder("PrecomputedCatalog")
    val categoryIds = resolvedIds.categoryIds
    val bookIds = resolvedIds.bookIds
    val tocTextIds = resolvedIds.tocTextIds

    // BOOK_TITLES
    val btCode = CodeBlock.builder().add("mapOf(\n")
    bookTitles.entries.sortedBy { it.key }.forEach { (id, title) ->
        btCode.add("  %LL to %S,\n", id, title)
    }
    btCode.add(")")
    builder.addProperty(
        PropertySpec
            .builder("BOOK_TITLES", MAP.parameterizedBy(LONG, STRING))
            .initializer(btCode.build())
            .build(),
    )

    // CATEGORY_TITLES
    val ctCode = CodeBlock.builder().add("mapOf(\n")
    categoryTitles.entries.sortedBy { it.key }.forEach { (id, title) ->
        ctCode.add("  %LL to %S,\n", id, title)
    }
    ctCode.add(")")
    builder.addProperty(
        PropertySpec
            .builder("CATEGORY_TITLES", MAP.parameterizedBy(LONG, STRING))
            .initializer(ctCode.build())
            .build(),
    )

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
    builder.addProperty(
        PropertySpec
            .builder("CATEGORY_BOOKS", mapCatBooks)
            .initializer(cbCode.build())
            .build(),
    )

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
    builder.addProperty(
        PropertySpec
            .builder("TOC_BY_TOC_TEXT_ID", tocMapType)
            .initializer(tocCode.build())
            .build(),
    )

    // Ids: pretty-named constants for UI code (avoid magic numbers)
    val idsObj = TypeSpec.objectBuilder("Ids")

    // Categories
    val categoriesPretty = categoryIds
    val catObj = TypeSpec.objectBuilder("Categories")
    categoriesPretty.forEach { (name, id) ->
        val he = categoryTitles[id] ?: ""
        catObj.addProperty(
            PropertySpec
                .builder(name, LONG)
                .addModifiers(KModifier.CONST)
                .initializer("%LL", id)
                .addKdoc("%L", he)
                .build(),
        )
    }
    idsObj.addType(catObj.build())

    // Books (featured ones referenced in UI)
    val booksPretty = bookIds
    val bookObj = TypeSpec.objectBuilder("Books")
    booksPretty.forEach { (name, id) ->
        val he = bookTitles[id] ?: ""
        bookObj.addProperty(
            PropertySpec
                .builder(name, LONG)
                .addModifiers(KModifier.CONST)
                .initializer("%LL", id)
                .addKdoc("%L", he)
                .build(),
        )
    }
    idsObj.addType(bookObj.build())

    // TocTexts (featured quick-jump entries)
    val tocTextPretty = tocTextIds
    val heByTocId: Map<Long, String> =
        tocByTocTextId.values
            .flatMap { it.entries }
            .associate { (tx, triple) -> tx to triple.first }
    val tocObj = TypeSpec.objectBuilder("TocTexts")
    tocTextPretty.forEach { (name, id) ->
        val he = heByTocId[id] ?: ""
        tocObj.addProperty(
            PropertySpec
                .builder(name, LONG)
                .addModifiers(KModifier.CONST)
                .initializer("%LL", id)
                .addKdoc("%L", he)
                .build(),
        )
    }
    idsObj.addType(tocObj.build())

    builder.addType(idsObj.build())

    // Dropdown presets for HomeView (polymorphic descriptors)
    val dropdownsObj = TypeSpec.objectBuilder("Dropdowns")
    val dropdownSpecClass = ClassName(pkg, "DropdownSpec")
    val listOfDropdownSpec = LIST.parameterizedBy(dropdownSpecClass)
    val tanakhId = categoryIds.getValue("TANAKH")
    val torahId = categoryIds.getValue("TORAH")
    val neviimId = categoryIds.getValue("NEVIIM")
    val ketuvimId = categoryIds.getValue("KETUVIM")
    val mishnaId = categoryIds.getValue("MISHNA")
    val mishnaOrders =
        listOf(
            categoryIds.getValue("MISHNA_ZERAIM"),
            categoryIds.getValue("MISHNA_MOED"),
            categoryIds.getValue("MISHNA_NASHIM"),
            categoryIds.getValue("MISHNA_NEZIKIN"),
            categoryIds.getValue("MISHNA_KODASHIM"),
            categoryIds.getValue("MISHNA_TAHAROT"),
        )
    val bavliId = categoryIds.getValue("BAVLI")
    val bavliOrders =
        listOf(
            categoryIds.getValue("BAVLI_ZERAIM"),
            categoryIds.getValue("BAVLI_MOED"),
            categoryIds.getValue("BAVLI_NASHIM"),
            categoryIds.getValue("BAVLI_NEZIKIN"),
            categoryIds.getValue("BAVLI_KODASHIM"),
            categoryIds.getValue("BAVLI_TAHAROT"),
        )
    val yerushalmiId = categoryIds.getValue("YERUSHALMI")
    val yerushalmiOrders =
        listOf(
            categoryIds.getValue("YERUSHALMI_ZERAIM"),
            categoryIds.getValue("YERUSHALMI_MOED"),
            categoryIds.getValue("YERUSHALMI_NASHIM"),
            categoryIds.getValue("YERUSHALMI_NEZIKIN"),
            categoryIds.getValue("YERUSHALMI_TAHAROT"),
        )
    val shulchanAruchId = categoryIds.getValue("SHULCHAN_ARUCH")
    val mishneTorahId = categoryIds.getValue("MISHNE_TORAH")
    val turCategoryId = categoryIds.getValue("TUR")
    val turBookId = bookIds.getValue("TUR")
    val tocOc = tocTextIds.getValue("ORACH_CHAIM")
    val tocYd = tocTextIds.getValue("YOREH_DEAH")
    val tocEh = tocTextIds.getValue("EVEN_HAEZER")
    val tocCm = tocTextIds.getValue("CHOSHEN_MISHPAT")
    val homeDropdowns =
        CodeBlock
            .builder()
            .add("listOf(\n")
            // Tanakh combined like Yerushalmi: root label with child categories' books
            .add("  MultiCategoryDropdownSpec(%LL, listOf(%LL, %LL, %LL)),\n", tanakhId, torahId, neviimId, ketuvimId)
            // Mishna
            .add(
                "  MultiCategoryDropdownSpec(%LL, listOf(%LL, %LL, %LL, %LL, %LL, %LL)),\n",
                mishnaId,
                mishnaOrders[0],
                mishnaOrders[1],
                mishnaOrders[2],
                mishnaOrders[3],
                mishnaOrders[4],
                mishnaOrders[5],
            )
            // Bavli
            .add(
                "  MultiCategoryDropdownSpec(%LL, listOf(%LL, %LL, %LL, %LL, %LL, %LL)),\n",
                bavliId,
                bavliOrders[0],
                bavliOrders[1],
                bavliOrders[2],
                bavliOrders[3],
                bavliOrders[4],
                bavliOrders[5],
            )
            // Yerushalmi
            .add(
                "  MultiCategoryDropdownSpec(%LL, listOf(%LL, %LL, %LL, %LL, %LL)),\n",
                yerushalmiId,
                yerushalmiOrders[0],
                yerushalmiOrders[1],
                yerushalmiOrders[2],
                yerushalmiOrders[3],
                yerushalmiOrders[4],
            )
            // Shulchan Aruch
            .add("  CategoryDropdownSpec(%LL),\n", shulchanAruchId)
            // Tur quick links
            .add(
                "  TocQuickLinksSpec(%LL, listOf(%LL, %LL, %LL, %LL)),\n",
                turBookId,
                tocOc,
                tocYd,
                tocEh,
                tocCm,
            ).add(")")
            .build()
    dropdownsObj.addProperty(
        PropertySpec
            .builder("HOME", listOfDropdownSpec)
            .initializer(homeDropdowns)
            .build(),
    )
    // Named specs for easy use in UI
    dropdownsObj.addProperty(
        PropertySpec
            .builder("TANAKH", dropdownSpecClass)
            .initializer(
                "MultiCategoryDropdownSpec(%LL, listOf(%LL, %LL, %LL))",
                tanakhId,
                torahId,
                neviimId,
                ketuvimId,
            ).build(),
    )
    // Conserver les presets historiques pour compatibilité éventuelle
    dropdownsObj.addProperty(
        PropertySpec
            .builder("TORAH", dropdownSpecClass)
            .initializer("CategoryDropdownSpec(%LL)", torahId)
            .build(),
    )
    dropdownsObj.addProperty(
        PropertySpec
            .builder("NEVIIM", dropdownSpecClass)
            .initializer("CategoryDropdownSpec(%LL)", neviimId)
            .build(),
    )
    dropdownsObj.addProperty(
        PropertySpec
            .builder("KETUVIM", dropdownSpecClass)
            .initializer("CategoryDropdownSpec(%LL)", ketuvimId)
            .build(),
    )
    dropdownsObj.addProperty(
        PropertySpec
            .builder("MISHNA", dropdownSpecClass)
            .initializer(
                "MultiCategoryDropdownSpec(%LL, listOf(%LL, %LL, %LL, %LL, %LL, %LL))",
                mishnaId,
                mishnaOrders[0],
                mishnaOrders[1],
                mishnaOrders[2],
                mishnaOrders[3],
                mishnaOrders[4],
                mishnaOrders[5],
            ).build(),
    )
    dropdownsObj.addProperty(
        PropertySpec
            .builder("BAVLI", dropdownSpecClass)
            .initializer(
                "MultiCategoryDropdownSpec(%LL, listOf(%LL, %LL, %LL, %LL, %LL, %LL))",
                bavliId,
                bavliOrders[0],
                bavliOrders[1],
                bavliOrders[2],
                bavliOrders[3],
                bavliOrders[4],
                bavliOrders[5],
            ).build(),
    )
    dropdownsObj.addProperty(
        PropertySpec
            .builder("YERUSHALMI", dropdownSpecClass)
            .initializer(
                "MultiCategoryDropdownSpec(%LL, listOf(%LL, %LL, %LL, %LL, %LL))",
                yerushalmiId,
                yerushalmiOrders[0],
                yerushalmiOrders[1],
                yerushalmiOrders[2],
                yerushalmiOrders[3],
                yerushalmiOrders[4],
            ).build(),
    )
    dropdownsObj.addProperty(
        PropertySpec
            .builder("SHULCHAN_ARUCH", dropdownSpecClass)
            .initializer("CategoryDropdownSpec(%LL)", shulchanAruchId)
            .build(),
    )
    // Mishneh Torah: display all books under its child categories
    run {
        val listLiteral =
            CodeBlock
                .builder()
                .apply {
                    add("listOf(")
                    mishnehTorahChildrenIds.forEachIndexed { idx, id ->
                        if (idx > 0) add(", ")
                        add("%LL", id)
                    }
                    add(")")
                }.build()
        dropdownsObj.addProperty(
            PropertySpec
                .builder("MISHNE_TORAH", dropdownSpecClass)
                .initializer(CodeBlock.of("MultiCategoryDropdownSpec(%LL, %L)", mishneTorahId, listLiteral))
                .build(),
        )
    }
    dropdownsObj.addProperty(
        PropertySpec
            .builder("TUR_QUICK_LINKS", dropdownSpecClass)
            .initializer(
                "TocQuickLinksSpec(%LL, listOf(%LL, %LL, %LL, %LL))",
                turBookId,
                tocOc,
                tocYd,
                tocEh,
                tocCm,
            ).build(),
    )
    builder.addType(dropdownsObj.build())

    return builder.build()
}

private data class ResolvedCatalogIds(
    val categoryIds: LinkedHashMap<String, Long>,
    val bookIds: LinkedHashMap<String, Long>,
    val tocTextIds: LinkedHashMap<String, Long>,
    val mishnehTorahChildren: List<Long>,
)

private suspend fun resolveCatalogIds(repo: SeforimRepository): ResolvedCatalogIds {
    val tanakhId = requireCategoryId(repo, "Tanakh root", listOf("תנ\"ך", "תנך", "תנ״ך"))
    val torahId = requireCategoryId(repo, "Torah", listOf("תורה"), tanakhId)
    val neviimId = requireCategoryId(repo, "Neviim", listOf("נביאים"), tanakhId)
    val ketuvimId = requireCategoryId(repo, "Ketuvim", listOf("כתובים"), tanakhId)

    val mishnaId = requireCategoryId(repo, "Mishna", listOf("משנה"))
    val mishnaOrders =
        listOf(
            "MISHNA_ZERAIM" to listOf("סדר זרעים"),
            "MISHNA_MOED" to listOf("סדר מועד"),
            "MISHNA_NASHIM" to listOf("סדר נשים"),
            "MISHNA_NEZIKIN" to listOf("סדר נזיקין"),
            "MISHNA_KODASHIM" to listOf("סדר קדשים"),
            "MISHNA_TAHAROT" to listOf("סדר טהרות"),
        ).associate { (key, titles) -> key to requireCategoryId(repo, key, titles, mishnaId) }

    val talmudRootId = findCategoryId(repo, listOf("תלמוד"))
    val bavliId =
        findCategoryId(repo, listOf("בבלי", "תלמוד בבלי"), talmudRootId)
            ?: requireCategoryId(repo, "Talmud Bavli", listOf("בבלי", "תלמוד בבלי"))
    val bavliOrders =
        listOf(
            "BAVLI_ZERAIM" to listOf("סדר זרעים"),
            "BAVLI_MOED" to listOf("סדר מועד"),
            "BAVLI_NASHIM" to listOf("סדר נשים"),
            "BAVLI_NEZIKIN" to listOf("סדר נזיקין"),
            "BAVLI_KODASHIM" to listOf("סדר קדשים"),
            "BAVLI_TAHAROT" to listOf("סדר טהרות"),
        ).associate { (key, titles) -> key to requireCategoryId(repo, key, titles, bavliId) }

    val yerushalmiId =
        findCategoryId(repo, listOf("ירושלמי", "תלמוד ירושלמי"), talmudRootId)
            ?: requireCategoryId(repo, "Talmud Yerushalmi", listOf("ירושלמי", "תלמוד ירושלמי"))
    val yerushalmiOrders =
        listOf(
            "YERUSHALMI_ZERAIM" to listOf("סדר זרעים"),
            "YERUSHALMI_MOED" to listOf("סדר מועד"),
            "YERUSHALMI_NASHIM" to listOf("סדר נשים"),
            "YERUSHALMI_NEZIKIN" to listOf("סדר נזיקין"),
            "YERUSHALMI_TAHAROT" to listOf("סדר טהרות"),
        ).associate { (key, titles) -> key to requireCategoryId(repo, key, titles, yerushalmiId) }

    val halachaRootId = findCategoryId(repo, listOf("הלכה"))
    val mishnehTorahId =
        findCategoryId(repo, listOf("משנה תורה"), halachaRootId)
            ?: requireCategoryId(repo, "Mishneh Torah", listOf("משנה תורה"))
    val turCategoryId =
        findCategoryId(repo, listOf("טור", "ארבעה טורים"), halachaRootId)
            ?: requireCategoryId(repo, "Tur category", listOf("טור", "ארבעה טורים"))
    val shulchanAruchId =
        findCategoryId(repo, listOf("שולחן ערוך"), halachaRootId)
            ?: requireCategoryId(repo, "Shulchan Aruch", listOf("שולחן ערוך"))

    val mishnehTorahChildren =
        runCatching { repo.getCategoryChildren(mishnehTorahId) }
            .getOrDefault(emptyList())
            .filter { !it.title.trimStart().startsWith("מפרשים") }
            .map { it.id }

    val turBookId = requireBookId(repo, "Tur", listOf("טור"))
    val tocQuickLinks = resolveTurQuickLinks(repo, turBookId)

    val categoryIds = linkedMapOf<String, Long>()
    categoryIds["TANAKH"] = tanakhId
    categoryIds["TORAH"] = torahId
    categoryIds["NEVIIM"] = neviimId
    categoryIds["KETUVIM"] = ketuvimId
    categoryIds["MISHNA"] = mishnaId
    categoryIds["MISHNA_ZERAIM"] = mishnaOrders.getValue("MISHNA_ZERAIM")
    categoryIds["MISHNA_MOED"] = mishnaOrders.getValue("MISHNA_MOED")
    categoryIds["MISHNA_NASHIM"] = mishnaOrders.getValue("MISHNA_NASHIM")
    categoryIds["MISHNA_NEZIKIN"] = mishnaOrders.getValue("MISHNA_NEZIKIN")
    categoryIds["MISHNA_KODASHIM"] = mishnaOrders.getValue("MISHNA_KODASHIM")
    categoryIds["MISHNA_TAHAROT"] = mishnaOrders.getValue("MISHNA_TAHAROT")
    categoryIds["BAVLI"] = bavliId
    categoryIds["BAVLI_ZERAIM"] = bavliOrders.getValue("BAVLI_ZERAIM")
    categoryIds["BAVLI_MOED"] = bavliOrders.getValue("BAVLI_MOED")
    categoryIds["BAVLI_NASHIM"] = bavliOrders.getValue("BAVLI_NASHIM")
    categoryIds["BAVLI_NEZIKIN"] = bavliOrders.getValue("BAVLI_NEZIKIN")
    categoryIds["BAVLI_KODASHIM"] = bavliOrders.getValue("BAVLI_KODASHIM")
    categoryIds["BAVLI_TAHAROT"] = bavliOrders.getValue("BAVLI_TAHAROT")
    categoryIds["YERUSHALMI"] = yerushalmiId
    categoryIds["YERUSHALMI_ZERAIM"] = yerushalmiOrders.getValue("YERUSHALMI_ZERAIM")
    categoryIds["YERUSHALMI_MOED"] = yerushalmiOrders.getValue("YERUSHALMI_MOED")
    categoryIds["YERUSHALMI_NASHIM"] = yerushalmiOrders.getValue("YERUSHALMI_NASHIM")
    categoryIds["YERUSHALMI_NEZIKIN"] = yerushalmiOrders.getValue("YERUSHALMI_NEZIKIN")
    categoryIds["YERUSHALMI_TAHAROT"] = yerushalmiOrders.getValue("YERUSHALMI_TAHAROT")
    categoryIds["MISHNE_TORAH"] = mishnehTorahId
    categoryIds["TUR"] = turCategoryId
    categoryIds["SHULCHAN_ARUCH"] = shulchanAruchId

    val bookIds = linkedMapOf("TUR" to turBookId)

    return ResolvedCatalogIds(
        categoryIds = categoryIds,
        bookIds = bookIds,
        tocTextIds = tocQuickLinks,
        mishnehTorahChildren = mishnehTorahChildren,
    )
}

private suspend fun resolveTurQuickLinks(
    repo: SeforimRepository,
    turBookId: Long,
): LinkedHashMap<String, Long> {
    val toc = runCatching { repo.getBookToc(turBookId) }.getOrDefault(emptyList())
    if (toc.isEmpty()) error("Could not load TOC for Tur (bookId=$turBookId)")
    val rootIds = toc.filter { it.parentId == null }.map { it.id }.toSet()
    val scopedEntries = if (rootIds.isEmpty()) toc else toc.filter { it.parentId in rootIds }
    val targets =
        linkedMapOf(
            "ORACH_CHAIM" to listOf("אורח חיים"),
            "YOREH_DEAH" to listOf("יורה דעה"),
            "EVEN_HAEZER" to listOf("אבן העזר"),
            "CHOSHEN_MISHPAT" to listOf("חושן משפט"),
        )
    val result = linkedMapOf<String, Long>()
    for ((key, names) in targets) {
        val match =
            scopedEntries.firstOrNull { entry ->
                val normalized = normalizeTitle(entry.text)
                names.any { normalizeTitle(it) == normalized }
            }
        if (match?.textId != null) {
            result[key] = match.textId!!
        }
    }
    val missing = targets.keys - result.keys
    if (missing.isNotEmpty()) {
        error("Missing TOC quick links for Tur (bookId=$turBookId): ${missing.joinToString()}")
    }
    return result
}

private suspend fun requireCategoryId(
    repo: SeforimRepository,
    label: String,
    candidates: List<String>,
    parentId: Long? = null,
): Long =
    findCategoryId(repo, candidates, parentId)
        ?: error("Could not locate category '$label' (candidates=${candidates.joinToString()})")

private suspend fun findCategoryId(
    repo: SeforimRepository,
    candidates: List<String>,
    parentId: Long? = null,
): Long? {
    if (candidates.isEmpty()) return null
    val normalizedTargets = candidates.map(::normalizeTitle).toSet()
    val scoped =
        runCatching {
            if (parentId != null) repo.getCategoryChildren(parentId) else repo.getRootCategories()
        }.getOrDefault(emptyList())
    scoped.firstOrNull { normalizedTargets.contains(normalizeTitle(it.title)) }?.let { return it.id }

    for (candidate in candidates) {
        val match = runCatching { repo.findCategoryByTitlePreferExact(candidate) }.getOrNull()
        if (match != null && (parentId == null || match.parentId == parentId)) {
            return match.id
        }
    }

    for (candidate in candidates) {
        val matches =
            runCatching { repo.findCategoriesByTitleLike("%$candidate%", 10) }
                .getOrDefault(emptyList())
        val match = matches.firstOrNull { parentId == null || it.parentId == parentId }
        if (match != null) return match.id
    }
    return null
}

private suspend fun requireBookId(
    repo: SeforimRepository,
    label: String,
    candidates: List<String>,
): Long =
    findBookId(repo, candidates)
        ?: error("Could not locate book '$label' (candidates=${candidates.joinToString()})")

private suspend fun findBookId(
    repo: SeforimRepository,
    candidates: List<String>,
): Long? {
    if (candidates.isEmpty()) return null
    val normalizedTargets = candidates.map(::normalizeTitle).toSet()
    for (candidate in candidates) {
        val book = runCatching { repo.findBookByTitlePreferExact(candidate) }.getOrNull()
        if (book != null && normalizedTargets.contains(normalizeTitle(book.title))) {
            return book.id
        }
    }
    for (candidate in candidates) {
        val books =
            runCatching { repo.findBooksByTitleLike("%$candidate%", 10) }
                .getOrDefault(emptyList())
        val match = books.firstOrNull { normalizedTargets.contains(normalizeTitle(it.title)) }
        if (match != null) return match.id
    }
    return null
}

private fun normalizeTitle(value: String): String = value.filter { it.isLetterOrDigit() }.lowercase()

private val STRING = String::class.asClassName()
private val LONG = Long::class.asClassName()
private val LIST = ClassName("kotlin.collections", "List")
private val MAP = ClassName("kotlin.collections", "Map")

private fun stripLabelPrefix(
    label: String,
    title: String,
): String {
    if (label.isBlank()) return title
    val prefix = Regex.escape(label)
    val patterns =
        listOf(
            Regex("^$prefix\\s*,\\s*"), // label + comma
            Regex("^$prefix,\\s*"), // label,comma
            Regex("^$prefix\\s*[:–—-]\\s*"), // label + colon/en/em dash/hyphen
            Regex("^$prefix\\s*\\+\\s*"), // label + plus
            Regex("^$prefix\\s+"), // label + space
        )
    for (p in patterns) {
        val replaced = title.replaceFirst(p, "")
        if (replaced !== title) return replaced.trimStart()
    }
    return title
}

private fun stripAnyLabelPrefix(
    labels: List<String>,
    title: String,
): String {
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
