package org.aionlink.open

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.aionlink.iccoa.AddressDisclosure
import org.aionlink.wifi.P2pConnector
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Queries only this phone's public local metadata. No peer scan, group or network is requested. */
class PlatformMetadataProbeTest {
    @Test fun reportWhetherThisPhoneProvidesRealOrAnonymizedMetadata() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Wi-Fi must already be on; the probe never enables it", context.getSystemService(WifiManager::class.java)?.isWifiEnabled == true)
        val result = AtomicReference<AddressDisclosure>()
        val connector = AtomicReference<P2pConnector>()
        val ready = CountDownLatch(1)
        instrumentation.runOnMainSync {
            connector.set(P2pConnector(context))
            if (connector.get().hasPermissions()) connector.get().prepare {
                result.set(it.platformAddress.disclosure); ready.countDown()
            } else ready.countDown()
        }
        try {
            assertTrue("Public metadata query did not complete", ready.await(12, TimeUnit.SECONDS))
            assumeTrue("Nearby Wi-Fi permission and public local data required", result.get() != null)
            instrumentation.sendStatus(0, Bundle().apply { putString("LOCAL_P2P_METADATA_KIND", result.get().name) })
            assertNotEquals(AddressDisclosure.UNAVAILABLE, result.get())
        } finally { instrumentation.runOnMainSync { connector.get().stop("Consulta local de pruebas finalizada") } }
    }
}
