package io.github.kdroidfilter.seforimapp.framework.search

import android.content.Context
import io.github.kdroidfilter.seforimlibrary.search.index.LuceneIndex
import io.github.kdroidfilter.seforimlibrary.search.index.bridge.BridgedLuceneIndex
import io.github.kdroidfilter.seforimlibrary.search.index.bridge.JavaProcessTransport
import java.io.File

/**
 * The Java runtime Lucene runs in on Android, whose ART can't keep up with the Java versions Lucene requires: a process
 * of the app's own (see SeforimLibrary's IOS_RUNTIME.md, "Android").
 *
 * Call [configure] before the first search; the app delivers the runtime (`./gradlew :searchkit:assembleAndroidJavaRuntime`):
 * the launchers in its native libraries (`lib/<abi>/libjvmlauncher.so`, arm64-v8a and x86_64, installed as files:
 * `useLegacyPackaging`), the JDK's classes and data of the device's ABI in [javaHome] and the search's jars in
 * [classPath] (both downloaded or extracted from the app's assets).
 */
object AndroidJavaRuntime {
    /** The JDK's classes and data (an exploded image: `modules/`, `conf/`, `lib/`). */
    var javaHome: File? = null

    /** The search's jars (SeforimLibrary's `mobileJvmClasspath`, the same as iOS). */
    var classPath: List<File> = emptyList()

    /** The Java process's options: its heap above all. */
    var options: List<String> = listOf("-Xmx128m", "-XX:+UseSerialGC", "-XX:UseSVE=0", "--enable-native-access=ALL-UNNAMED")

    private var launcher: File? = null

    /**
     * Uses the runtime the app delivers in [context]'s files: `jdk/<abi>/` (the ABI of the launcher Android installed)
     * and the jars of `jdk-app/`, unless set otherwise.
     */
    fun configure(
        context: Context,
        javaHome: File = File(context.filesDir, "jdk/${launcherAbi(context)}"),
        classPath: List<File> = File(context.filesDir, "jdk-app").listFiles { file -> file.extension == "jar" }.orEmpty().sorted(),
    ) {
        launcher = File(context.applicationInfo.nativeLibraryDir, "libjvmlauncher.so")
        // Android has no /tmp: the app's cache instead
        val tmp = context.cacheDir.path
        options = options.filterNot { it.startsWith("-Djava.io.tmpdir=") || it.startsWith("-XX:AltTempDir=") } +
            listOf("-Djava.io.tmpdir=$tmp", "-XX:AltTempDir=$tmp")
        this.javaHome = javaHome
        this.classPath = classPath
    }

    // The native library dir is named after the ABI Android installed the app's libraries for (lib/arm64, lib/x86_64)
    private fun launcherAbi(context: Context): String =
        when (val dir = File(context.applicationInfo.nativeLibraryDir).name) {
            "arm64" -> "arm64-v8a"
            else -> dir
        }

    val transport: JavaProcessTransport by lazy {
        JavaProcessTransport(
            launcher = checkNotNull(launcher) { "AndroidJavaRuntime.configure(context) first: Lucene runs in its Java process" },
            javaHome = checkNotNull(javaHome),
            classPath = classPath,
            options = options,
        )
    }
}

actual fun openLuceneIndex(
    indexDir: String,
    cacheQueries: Boolean,
): LuceneIndex = BridgedLuceneIndex(AndroidJavaRuntime.transport, indexDir, cacheQueries)
