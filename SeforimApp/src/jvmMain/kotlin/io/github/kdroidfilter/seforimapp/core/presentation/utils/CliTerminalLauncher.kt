package io.github.kdroidfilter.seforimapp.core.presentation.utils

import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.logger.errorln
import io.github.kdroidfilter.seforimapp.logger.infoln
import java.io.File
import java.nio.file.Paths

/**
 * Opens the SeforimLibrary search CLI in an external terminal window by re-launching the very same
 * application binary with a leading `cli` argument (see the `args.firstOrNull() == "cli"` branch in
 * `main()`). No separate `seforim-cli` executable is bundled — the normal `zayit` binary doubles as
 * the CLI when invoked as `zayit cli <command> ...`.
 *
 * The launched process is fully isolated: it runs in its own JVM/terminal, so a CLI error or
 * `exitProcess` cannot disturb the running desktop app. The database/index/dictionary paths the app
 * currently uses are resolved here and passed explicitly, so the CLI targets exactly the same data
 * the GUI does (its own defaults resolve the dictionary differently).
 */
object CliTerminalLauncher {
    /**
     * Launches `<thisBinary> cli <commandArgs...> [--db ... --index ... --dict ...]` inside a native
     * terminal window. Runs off the UI thread; failures are logged, never thrown.
     *
     * @param commandArgs the CLI command and its options, e.g. `listOf("search", "בראשית", "--json")`.
     *   Defaults to `help` so a bare call opens the usage screen.
     */
    fun launch(commandArgs: List<String> = listOf("help")) {
        Thread {
            runCatching { launchBlocking(commandArgs) }
                .onFailure { error -> errorln { "[cli] failed to launch CLI terminal: ${error.message}" } }
        }.apply {
            isDaemon = true
            name = "cli-terminal-launcher"
        }.start()
    }

    private fun launchBlocking(commandArgs: List<String>) {
        val exe = resolveExecutablePath() ?: error("could not resolve the running executable path")
        val tokens =
            buildList {
                add(exe)
                add("cli")
                addAll(commandArgs)
                addAll(resolveDataPathArgs())
            }
        infoln { "[cli] opening terminal: ${tokens.joinToString(" ")}" }

        val os = System.getProperty("os.name").orEmpty().lowercase()
        when {
            os.contains("mac") -> openMacTerminal(tokens)
            os.contains("win") -> openWindowsTerminal(tokens)
            else -> openLinuxTerminal(tokens)
        }
    }

    /**
     * Resolves the path of the currently running application binary.
     *
     * - `jpackage.app-path` is set by jpackage-style launchers and points at the user-facing
     *   executable.
     * - Otherwise [ProcessHandle.current] returns the launching command, which for the GraalVM
     *   native image is the `zayit` binary itself.
     *
     * Returns `null` (e.g. a plain `java -jar` dev run, where the command is the JDK launcher and
     * re-launching it would not enter our `main`).
     */
    private fun resolveExecutablePath(): String? {
        System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() && File(it).exists() }?.let { return it }

        val command =
            ProcessHandle
                .current()
                .info()
                .command()
                .orElse(null)
                ?.takeIf { it.isNotBlank() }
        if (command != null) {
            val name = File(command).name.lowercase()
            // A JVM launcher means we are running from a dev/classpath setup, not the packaged
            // native binary — re-launching `java` would not re-enter our main() with `cli`.
            val isJvmLauncher = name == "java" || name == "javaw" || name == "java.exe" || name == "javaw.exe"
            if (!isJvmLauncher && File(command).exists()) return command
        }
        return null
    }

    /**
     * Resolves `--db/--index/--dict` matching the app's `provideSearchEngine` wiring so the CLI
     * opens the exact same data set. Returns an empty list if the path can't be resolved (the CLI
     * then falls back to its own defaults).
     */
    private fun resolveDataPathArgs(): List<String> =
        runCatching {
            val dbPath = getDatabasePath()
            val indexPath = if (dbPath.endsWith(".db")) "$dbPath.lucene" else "$dbPath.luceneindex"
            val dictPath = Paths.get(indexPath).resolveSibling("lexical.db").toString()
            listOf("--db", dbPath, "--index", indexPath, "--dict", dictPath)
        }.getOrElse {
            errorln { "[cli] could not resolve database paths, CLI will use its defaults: ${it.message}" }
            emptyList()
        }

    // --- Platform terminal openers -------------------------------------------------------------

    private fun openMacTerminal(tokens: List<String>) {
        // Build a POSIX shell command line, then embed it in an AppleScript string for Terminal.app.
        // `do script` runs the command in a new tab/window and leaves it open afterwards.
        val shellCommand = tokens.joinToString(" ") { posixQuote(it) }
        val appleScript = "tell application \"Terminal\"\nactivate\ndo script \"${escapeForAppleScript(shellCommand)}\"\nend tell"
        ProcessBuilder("osascript", "-e", appleScript).start()
    }

    private fun openWindowsTerminal(tokens: List<String>) {
        // `start` opens a new console window; `cmd /k` keeps it open after the CLI exits so the
        // user can read the output. The empty "" is start's window-title placeholder.
        val inner = tokens.joinToString(" ") { winQuote(it) }
        ProcessBuilder("cmd", "/c", "start", "", "cmd", "/k", inner)
            .apply { redirectErrorStream(true) }
            .start()
    }

    private fun openLinuxTerminal(tokens: List<String>) {
        val shellCommand = tokens.joinToString(" ") { posixQuote(it) }
        // Keep the window open after the command finishes by dropping into an interactive shell.
        val keepOpen = "$shellCommand; exec \${SHELL:-bash}"

        val candidates =
            listOf(
                listOf("x-terminal-emulator", "-e", "bash", "-c", keepOpen),
                listOf("gnome-terminal", "--", "bash", "-c", keepOpen),
                listOf("konsole", "-e", "bash", "-c", keepOpen),
                listOf("xfce4-terminal", "-e", "bash -c ${posixQuote(keepOpen)}"),
                listOf("xterm", "-e", "bash", "-c", keepOpen),
            )
        for (command in candidates) {
            val launched = runCatching { ProcessBuilder(command).start() }.isSuccess
            if (launched) return
        }
        error("no supported terminal emulator found (tried x-terminal-emulator, gnome-terminal, konsole, xfce4-terminal, xterm)")
    }

    // --- Quoting helpers -----------------------------------------------------------------------

    /** POSIX single-quote: safe for spaces, Hebrew, and metacharacters. */
    private fun posixQuote(token: String): String = "'" + token.replace("'", "'\\''") + "'"

    /** Escape a backslash/double-quote string for embedding inside an AppleScript "..." literal. */
    private fun escapeForAppleScript(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"")

    /** Wrap a token in double quotes for cmd.exe, doubling any inner double quotes. */
    private fun winQuote(token: String): String = "\"" + token.replace("\"", "\"\"") + "\""
}
