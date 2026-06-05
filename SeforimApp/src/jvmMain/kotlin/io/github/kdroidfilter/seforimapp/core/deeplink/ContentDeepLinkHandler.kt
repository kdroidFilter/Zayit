package io.github.kdroidfilter.seforimapp.core.deeplink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Resolves incoming zayit:// content deep links and opens them in a new tab.
 *
 * Cross-platform (unlike [AppJumpList], which is Windows-only): the transport — cold-start CLI
 * arg, single-instance relay, macOS Apple Events — is provided by Nucleus and surfaced through
 * [pendingDeepLink]. Internal seforim:// jump-list URIs are ignored here and left to [AppJumpList].
 */
@Composable
fun ContentDeepLinkHandler(
    tabsViewModel: TabsViewModel,
    repository: SeforimRepository,
    pendingDeepLink: StateFlow<String?>,
    onClearDeepLink: () -> Unit,
) {
    val currentClear by rememberUpdatedState(onClearDeepLink)
    LaunchedEffect(Unit) {
        pendingDeepLink.filterNotNull().collect { raw ->
            val destination = parseZayitDeepLink(raw) ?: return@collect
            // Guard against dangling references (e.g. a link from a future database version).
            val resolvable =
                when (destination) {
                    is TabsDestination.BookContent -> repository.getBookCore(destination.bookId) != null
                    else -> true
                }
            if (resolvable) {
                tabsViewModel.openTab(destination)
            } else {
                warnln { "Ignoring deep link to unknown reference: $raw" }
            }
            currentClear()
        }
    }
}
