package io.github.kdroidfilter.seforimapp.features.settings

import io.github.kdroidfilter.seforimapp.framework.update.InstallInfo

/** The conditions shown in the onboarding and the settings, with the blocks that do not apply to this install left out. */
object ConditionsText {
    const val PATH = "files/CONDITIONS.md"

    private val conditionalBlock = Regex("""<!-- if:([a-z-]+) -->\n(.*?)<!-- endif -->\n""", RegexOption.DOT_MATCHES_ALL)

    fun forThisInstall(markdown: String): String = filter(markdown, ::appliesHere)

    /** Keeps the body of each `<!-- if:NAME -->…<!-- endif -->` block whose NAME [applies], drops the others. */
    internal fun filter(
        markdown: String,
        applies: (String) -> Boolean,
    ): String = markdown.replace(conditionalBlock) { if (applies(it.groupValues[1])) it.groupValues[2] else "" }

    private fun appliesHere(name: String): Boolean =
        when (name) {
            "self-update" -> InstallInfo.selfUpdates
            "update-helper" -> InstallInfo.hasPasswordlessUpdateHelper
            else -> false
        }
}
