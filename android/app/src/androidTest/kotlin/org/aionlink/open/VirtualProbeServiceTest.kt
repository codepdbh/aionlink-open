package org.aionlink.open

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Screen-on check of the foreground-service probe; the screen-off run is a manual test by the owner. */
class VirtualProbeServiceTest {
    @Test fun foregroundServiceRunsCarSizedProbeAndJournalsIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("1920x960p30 AVC encoder", ScreenEncoder.supports(1920, 960, 30))
        try {
            VirtualProbeService.start(context)
            val deadline = System.currentTimeMillis() + 5000
            while (!VirtualProbeService.running.value && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertTrue("service did not start", VirtualProbeService.running.value)
            Thread.sleep(11_000)
            val journal = VirtualProbeJournal(context).snapshot()
            assertTrue(journal, journal.contains("EN_CURSO_O_INTERRUMPIDA"))
            assertTrue(journal, journal.contains("Servicio en primer plano activo"))
            assertTrue(journal, journal.contains("Pantalla virtual propia 1920x960"))
            assertTrue(journal, journal.contains("servicio en primer plano=true"))
            assertTrue(journal, Regex("H264=(\\d+)").findAll(journal).any { it.groupValues[1].toInt() > 100 })
            assertTrue(journal, journal.contains("paquetes inválidos=0"))
        } finally {
            VirtualProbeService.stop(context)
            val deadline = System.currentTimeMillis() + 5000
            while (VirtualProbeService.running.value && System.currentTimeMillis() < deadline) Thread.sleep(100)
        }
        assertFalse("service did not stop", VirtualProbeService.running.value)
        assertTrue(VirtualProbeJournal(context).snapshot().contains("FINALIZADA"))
    }
}
