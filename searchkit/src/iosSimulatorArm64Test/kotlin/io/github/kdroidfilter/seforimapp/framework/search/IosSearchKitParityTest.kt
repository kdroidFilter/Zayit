package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimapp.features.search.semantic.SemanticModels
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import platform.Foundation.NSProcessInfo
import kotlin.test.Test
import kotlin.time.measureTimedValue

/**
 * The app's search on iOS, end to end in the simulator: the lookup and the hybrid search, Lucene in the embedded Java
 * runtime, the semantic model on ONNX Runtime's C API, the database on SQLDelight's native driver. Writes SearchKitDump
 * (variables passed by the iosSimulatorArm64Test task); skipped without data.
 */
class IosSearchKitParityTest {
    @Test
    fun dump() {
        val env = NSProcessInfo.processInfo.environment
        val db = env["SEFORIM_TEST_DB"] as String? ?: return
        IosJavaRuntime.classPath = (env["SEFORIM_JVM_CLASSPATH"] as String).split(':')
        val models =
            (env["SEFORIM_MODEL_DIR"] as String?)?.let {
                SemanticModels("$it/seforim-embed-v5-int8.onnx", "$it/tokenizer.json", "$it/seforim-embed-v5-static.i8")
            }
        SearchServices(db, openSeforimRepository(db), models).use { services ->
            val (dump, took) = measureTimedValue { runBlocking { SearchKitDump.dump(services) } }
            println("SearchKit dump in $took")
            SystemFileSystem.sink(Path(env["SEFORIM_PARITY_OUT"] as String)).buffered().use { it.writeString(dump) }
        }
    }
}
