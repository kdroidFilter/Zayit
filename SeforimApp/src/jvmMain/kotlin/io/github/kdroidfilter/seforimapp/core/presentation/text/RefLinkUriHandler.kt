package io.github.kdroidfilter.seforimapp.core.presentation.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.launch
import java.util.UUID

private const val REF_LINK_PREFIX = "seforim://line/"

/** The line a citation link of the texts points to (`seforim://line/<id>`), or null for any other URI. */
internal fun refLinkLineId(uri: String): Long? =
    uri.takeIf { it.startsWith(REF_LINK_PREFIX) }?.removePrefix(REF_LINK_PREFIX)?.toLongOrNull()

/** Opens the citation links of the texts in a new tab of [tabsViewModel]; any other URI goes to the platform handler. */
@Composable
fun rememberRefLinkUriHandler(tabsViewModel: TabsViewModel): UriHandler {
    val platform = LocalUriHandler.current
    val repository = LocalAppGraph.current.repository
    val scope = rememberCoroutineScope()
    return remember(platform, repository, tabsViewModel, scope) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val lineId = refLinkLineId(uri) ?: return platform.openUri(uri)
                scope.launch {
                    val line = repository.getLine(lineId) ?: return@launch
                    tabsViewModel.openTab(
                        TabsDestination.BookContent(bookId = line.bookId, tabId = UUID.randomUUID().toString(), lineId = line.id),
                    )
                }
            }
        }
    }
}
