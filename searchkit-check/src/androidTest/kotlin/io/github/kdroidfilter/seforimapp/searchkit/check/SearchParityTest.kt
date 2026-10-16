package io.github.kdroidfilter.seforimapp.searchkit.check

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kdroidfilter.seforimapp.features.search.semantic.SemanticModels
import io.github.kdroidfilter.seforimapp.framework.search.AndroidJavaRuntime
import io.github.kdroidfilter.seforimapp.framework.search.SearchKitDump
import io.github.kdroidfilter.seforimapp.framework.search.SearchServices
import io.github.kdroidfilter.seforimapp.framework.search.openSeforimRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The app's search on this device, as an app runs it: Lucene in the app's Java process. The data goes in the app's
 * files dir (adb root, push, chown to the app): `seforim/` (seforim.db, its indexes, lexical.db, `model/`), `jdk/` and
 * `jdk-app/` (searchkit's assembleAndroidJavaRuntime). Writes SearchKitDump as `searchkit.txt` there; skipped without
 * data.
 */
@RunWith(AndroidJUnit4::class)
class SearchParityTest {
    @Test
    fun dump() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.filesDir, "seforim")
        val db = File(dir, "seforim.db").takeIf { it.isFile } ?: return
        AndroidJavaRuntime.configure(context)
        val model = File(dir, "model")
        val models =
            SemanticModels(
                File(model, "seforim-embed-v5-int8.onnx").path,
                File(model, "tokenizer.json").path,
                File(model, "seforim-embed-v5-static.i8").path,
            ).takeIf { model.isDirectory }
        SearchServices(db.path, openSeforimRepository(context, db.path), models).use { services ->
            val start = SystemClock.elapsedRealtime()
            val dump = runBlocking { SearchKitDump.dump(services) }
            println("SearchKit dump in ${SystemClock.elapsedRealtime() - start} ms")
            File(context.filesDir, "searchkit.txt").writeText(dump)
        }
    }
}
