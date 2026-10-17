package io.github.kdroidfilter.seforimapp.framework.desktop

import io.github.kdroidfilter.seforim.tabs.TabItem
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsHost

/** A window's tabs: the group [groupIdOf] of the desktop [sessionOf] (both follow the window across desktops). */
internal class DesktopTabsHost(
    private val sessionOf: () -> DesktopSession,
    private val groupIdOf: () -> String,
) : TabsHost {
    private val session: DesktopSession get() = sessionOf()
    private val groupId: String get() = groupIdOf()

    override fun ids(): List<String> = session.group(groupId)?.ids.orEmpty()

    override fun selectedId(): String? = session.group(groupId)?.selectedId

    override fun item(tabId: String): TabItem? = session.item(tabId)

    override fun isDeclared(tabId: String): Boolean = session.workspace.tab(tabId) != null

    override fun isPinned(tabId: String): Boolean = session.isPinned(tabId)

    override fun setPinned(
        tabId: String,
        pinned: Boolean,
    ) = session.setPinned(tabId, pinned)

    override fun select(tabId: String) = session.workspace.select(tabId)

    override fun close(tabId: String) = session.workspace.close(tabId)

    override fun reorder(
        tabId: String,
        toIndex: Int,
    ) = session.workspace.reorder(tabId, toIndex)

    override fun add(
        destination: TabsDestination,
        index: Int,
        replacing: String?,
        pinned: Boolean,
    ) {
        session.addTab(destination, groupId, index = index, replacing = replacing, pinned = pinned)
    }

    override fun updateDestination(
        tabId: String,
        destination: TabsDestination,
    ) = session.updateDestination(tabId, destination)
}
