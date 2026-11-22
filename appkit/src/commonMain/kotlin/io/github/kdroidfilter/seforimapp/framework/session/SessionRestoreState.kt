package io.github.kdroidfilter.seforimapp.framework.session

import kotlinx.coroutines.flow.StateFlow

/** Whether the app is reopening the tabs of its last session (the desktop's `SessionManager`). */
interface SessionRestoreState {
    val isRestoringSession: StateFlow<Boolean>
}
