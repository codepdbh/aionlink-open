package org.aionlink.open

import org.junit.Assert.*
import org.junit.Test

class PendingAuthCodeTest {
    @Test fun replacementAndCancellationWipeCodesAndTakeIsOneShot() {
        val pending = PendingAuthCode()
        val first = byteArrayOf(49, 50)
        val second = byteArrayOf(51, 52)
        pending.set(first); pending.set(second)
        assertArrayEquals(byteArrayOf(0, 0), first)
        assertTrue(pending.pending)
        assertSame(second, pending.take())
        assertFalse(pending.pending)
        assertNull(pending.take())
        pending.set(second)
        assertTrue(pending.clear())
        assertArrayEquals(byteArrayOf(0, 0), second)
        assertFalse(pending.clear())
    }
}
