package io.github.kdroidfilter.seforimapp

import dev.zacsweers.metro.createGraph
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.vinceglb.filekit.FileKit

/** The real [AppGraph] for UI tests, FileKit initialized first since the graph's databases live under its dirs. */
fun testAppGraph(): AppGraph {
    FileKit.init("seforimapp-tests")
    return createGraph<AppGraph>()
}
