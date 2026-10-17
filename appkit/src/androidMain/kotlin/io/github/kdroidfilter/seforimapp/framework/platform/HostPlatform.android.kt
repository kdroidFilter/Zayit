package io.github.kdroidfilter.seforimapp.framework.platform

actual val isLinuxDesktop: Boolean = false

actual fun environmentVariable(name: String): String? = System.getenv(name)
