package io.github.kdroidfilter.seforimapp.framework.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

actual val isLinuxDesktop: Boolean = false

@OptIn(ExperimentalForeignApi::class)
actual fun environmentVariable(name: String): String? = getenv(name)?.toKString()
