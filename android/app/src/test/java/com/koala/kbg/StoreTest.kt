package com.koala.kbg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StoreTest {
    private fun tmp(): File = Files.createTempDirectory("kbg").toFile()

    @Test fun saveAndLoad() {
        val s = Store(tmp())
        assertEquals("", s.load())
        s.save("{\"a\":1}")
        assertEquals("{\"a\":1}", s.load())
        assertEquals("{\"a\":1}", s.read("kbg-${Store.day()}.json"))
    }

    @Test fun keeps30Daily() {
        val root = tmp(); val s = Store(root)
        for (i in 1..40) File(s.backups, "kbg-2026-01-%02d.json".format(i % 28 + 1).replace("2026-01", if (i > 28) "2026-02" else "2026-01")).writeText("x")
        s.save("{}")
        val daily = s.backups.listFiles()!!.filter { Regex("^kbg-\\d{4}-\\d{2}-\\d{2}\\.json$").matches(it.name) }
        assertEquals(30, daily.size)
        assertTrue(daily.any { it.name == "kbg-${Store.day()}.json" })
    }

    @Test fun safetyCopiesAndNames() {
        val s = Store(tmp())
        val n = s.safetyCopy("{\"b\":2}", "Avant import")
        assertTrue(n.endsWith("-avant-import.json"))
        assertEquals("{\"b\":2}", s.read(n))
        assertEquals("", s.read("../state.json"))
        assertEquals("", s.read("state.json"))
    }

    @Test fun versionCompare() {
        assertTrue(Updater.newer("1.84", "1.83"))
        assertTrue(Updater.newer("2.0", "1.99"))
        assertTrue(!Updater.newer("1.83", "1.83"))
    }
}
