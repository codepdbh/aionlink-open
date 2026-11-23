package org.aionlink.projection

import org.junit.Assert.*
import org.junit.Test

/** Synthetic CustomKeyEvent bodies built from the UCarProto field numbers; not vehicle captures. */
class CarKeysTest {
    private fun varint(value: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream(); var v = value
        while (true) { if (v and 0x7fL.inv() == 0L) { out.write(v.toInt()); return out.toByteArray() }; out.write(((v and 0x7f) or 0x80).toInt()); v = v ushr 7 }
    }
    private fun field(number: Int, value: Long) = varint((number shl 3).toLong()) + varint(value)

    @Test fun `decodes a key press and release, proto3 omitting action 0`() {
        assertEquals(0 to 87, CarKeys.parseCustomKeyEvent(field(2, 87)))
        assertEquals(1 to 8107, CarKeys.parseCustomKeyEvent(field(1, 1) + field(2, 8107) + field(3, 0) + field(4, 123456789)))
    }

    @Test fun `skips unknown length-delimited fields and rejects malformed bodies`() {
        val extra = varint((9 shl 3 or 2).toLong()) + varint(3) + byteArrayOf(1, 2, 3)
        assertEquals(0 to 88, CarKeys.parseCustomKeyEvent(extra + field(2, 88)))
        assertNull(CarKeys.parseCustomKeyEvent(byteArrayOf(0x10)))           // truncated varint
        assertNull(CarKeys.parseCustomKeyEvent(field(1, 1)))                // no keycode
        assertNull(CarKeys.parseCustomKeyEvent(field(1, 7) + field(2, 87))) // invalid action
    }

    @Test fun `protocol keys map to the keys Android Auto understands`() {
        assertEquals(CarKeys.KEYCODE_SEARCH, CarKeys.toAndroid(CarKeys.VR_START))
        assertEquals(CarKeys.KEYCODE_HOME, CarKeys.toAndroid(CarKeys.MAIN))
        assertEquals(CarKeys.KEYCODE_CALL, CarKeys.toAndroid(CarKeys.TEL))
        assertEquals(CarKeys.KEYCODE_MUSIC, CarKeys.toAndroid(CarKeys.MEDIA))
        assertEquals(CarKeys.KEYCODE_SOFT_RIGHT, CarKeys.toAndroid(CarKeys.NEXT_FOCUS))
        assertEquals(CarKeys.KEYCODE_BACK, CarKeys.toAndroid(CarKeys.OUT_FOCUS))
        for (media in listOf(85, 87, 88, 126, 127)) {
            assertEquals(media, CarKeys.toAndroid(media)); assertTrue(CarKeys.isMedia(media))
        }
    }

    @Test fun `keys without a safe equivalent are not forwarded`() {
        for (code in listOf(CarKeys.NAVI, CarKeys.NAVI_QUIT, CarKeys.VR_STOP, CarKeys.MEDIA_FAVOR,
            CarKeys.KEYCODE_VOLUME_UP, CarKeys.KEYCODE_VOLUME_DOWN, 26, 9999)) assertNull(CarKeys.toAndroid(code))
        assertFalse(CarKeys.isMedia(CarKeys.KEYCODE_HOME))
    }
}
