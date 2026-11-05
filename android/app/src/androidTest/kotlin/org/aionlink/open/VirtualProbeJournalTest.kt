package org.aionlink.open

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class VirtualProbeJournalTest {
    @Test fun persistsAcrossInstancesDistinguishesInterruptionAndBoundsHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-journal-${UUID.randomUUID()}"
        val first = VirtualProbeJournal(context, name)
        try {
            assertTrue(first.snapshot().contains("Sin prueba"))
            first.start()
            repeat(160) { first.sample("sample=$it\nextra") }
            // Wait for queued apply() writes, then verify the synthetic record reached private storage.
            assertTrue(context.getSharedPreferences(name, 0).edit().commit())
            val disk = java.io.File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").readText()
            assertTrue(disk.contains("sample=159 extra"))
            val restarted = VirtualProbeJournal(context, name)
            val interrupted = restarted.snapshot()
            assertTrue(interrupted.contains("EN_CURSO_O_INTERRUMPIDA"))
            assertTrue(interrupted.contains("sample=159 extra"))
            assertFalse(interrupted.contains("sample=0 extra"))
            assertTrue(interrupted.lines().size <= 151)
            restarted.finish("Terminado")
            assertTrue(VirtualProbeJournal(context, name).snapshot().contains("FINALIZADA"))
            restarted.sample("No debe aparecer")
            assertFalse(restarted.snapshot().contains("No debe aparecer"))
        } finally {
            context.getSharedPreferences(name, 0).edit().commit()
            context.deleteSharedPreferences(name)
        }
    }
}
