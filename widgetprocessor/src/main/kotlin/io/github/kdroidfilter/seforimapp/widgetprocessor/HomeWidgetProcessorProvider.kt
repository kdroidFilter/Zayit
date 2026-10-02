package io.github.kdroidfilter.seforimapp.widgetprocessor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration

private const val PACKAGE = "io.github.kdroidfilter.seforimapp.features.home.widgets"
private const val HOME_WIDGET = "$PACKAGE.HomeWidget"

/**
 * Writes `availableHomeWidgets`: every `object` of the module implementing `HomeWidget`, so a new widget shows in the
 * gallery without being listed anywhere. Ordered by package, then by name, for a gallery grouped by theme.
 */
class HomeWidgetProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = HomeWidgetProcessor(environment.codeGenerator)
}

private class HomeWidgetProcessor(
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {
    private var done = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (done) return emptyList()
        done = true
        val widgets =
            resolver
                .getAllFiles()
                .flatMap { file -> file.declarations.filterIsInstance<KSClassDeclaration>() }
                .filter { it.classKind == ClassKind.OBJECT }
                .filter { widget -> widget.getAllSuperTypes().any { it.declaration.qualifiedName?.asString() == HOME_WIDGET } }
                .sortedWith(compareBy({ it.packageName.asString() }, { it.simpleName.asString() }))
                .toList()
        val sources = widgets.mapNotNull { it.containingFile }.toTypedArray()
        codeGenerator.createNewFile(Dependencies(aggregating = true, *sources), PACKAGE, "AvailableHomeWidgets").writer().use { out ->
            out.write("package $PACKAGE\n\n")
            out.write("/** Every widget the user can place, shown by default or not: generated from each `HomeWidget` object. */\n")
            out.write("internal val availableHomeWidgets: List<HomeWidget> =\n    listOf(\n")
            widgets.forEach { out.write("        ${it.qualifiedName!!.asString()},\n") }
            out.write("    )\n")
        }
        return emptyList()
    }
}
