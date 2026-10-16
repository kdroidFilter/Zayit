package io.github.kdroidfilter.seforimapp.framework.search

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kdroidfilter.seforimapp.features.search.semantic.SemanticModels
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The app's search on Android 15+, end to end: the lookup and the hybrid search, Lucene on ART, the semantic model on
 * ONNX Runtime for Android, the database on Android's SQLite. The data (seforim.db and its indexes, lexical.db, the
 * model in model/) goes in the test app's files dir under seforim/ (adb root, push, chown to the app); SearchKitDump
 * comes back as searchkit-android.txt there. Skipped without data.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSearchKitParityTest {
    @Test
    fun dump() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.filesDir, "seforim")
        val db = File(dir, "seforim.db").takeIf { it.isFile } ?: return
        InstrumentationRegistry.getArguments().getString("luceneQueryCache")?.let { System.setProperty("seforim.lucene.queryCache", it) }
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
            File(context.filesDir, "searchkit-android.txt").writeText(dump)
        }
    }
}
