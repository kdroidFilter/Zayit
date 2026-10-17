package io.github.kdroidfilter.seforimapp.framework.search

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test

/** Writes SearchKitDump on the JVM for the parity with iOS: -PseforimTestDb=<seforim.db clone>, -PparityOut. */
class SearchKitParityTest {
    @Test
    fun dump() {
        val db = System.getProperty("seforimTestDb") ?: return
        val out = File(System.getProperty("parityOut") ?: return)
        SearchServices(db, openSeforimRepository(db)).use { services ->
            val start = System.nanoTime()
            out.writeText(runBlocking { SearchKitDump.dump(services) })
            println("SearchKit dump in ${(System.nanoTime() - start) / 1_000_000} ms")
        }
    }
}
