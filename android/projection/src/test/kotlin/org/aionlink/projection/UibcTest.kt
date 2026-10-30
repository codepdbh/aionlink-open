package org.aionlink.projection

import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Synthetic UIBC messages following the field names Samsung reads; not vehicle captures. */
class UibcTest {
    @Test fun parsesSplitTouchAndKeyMessages() {
        val parser = UibcParser()
        assertTrue(parser.feed("{\"type\":1,\"action\":0,\"width\":1280,\"height\":720,\"count\":1,\"trackID0\":3,").isEmpty())
        val events = parser.feed("\"x0\":640,\"y0\":360}{\"type\":2,\"action\":1,\"keycode\":4,\"metaState\":0}")
        assertEquals(2, events.size)
        assertEquals(UibcEvent.Touch(0, 1280, 720, listOf(UibcEvent.Pointer(3, 640, 360))), events[0])
        assertEquals(UibcEvent.Key(1, 4, 0), events[1])
        assertTrue(parser.feed("{\"type\":1,\"action\":0,\"count\":1}").isEmpty()) // missing coordinates
    }

    @Test fun mapsLetterboxedPortraitScreen() {
        // 1080x2400 portrait phone mirrored into 1280x720: scale 0.3, 478 px bars on each side.
        val center = UibcMapper.map(640, 360, 1280, 720, 1080, 2400)!!
        assertEquals(540f, center.first, 0.5f); assertEquals(1200f, center.second, 0.5f)
        assertNull(UibcMapper.map(10, 360, 1280, 720, 1080, 2400)) // inside the black bar
        val corner = UibcMapper.map(1279, 719, 1280, 1280 * 720 / 1280, 1280, 720)!!
        assertEquals(1279f, corner.first, 0.01f)
    }

    @Test fun serverDeliversEventsFromReceiver() {
        val received = CountDownLatch(2)
        val events = java.util.Collections.synchronizedList(mutableListOf<UibcEvent>())
        UibcServer(InetAddress.getLoopbackAddress(), port = 0, onEvent = { events += it; received.countDown() }, onStatus = { }).use { server ->
            server.start()
            Socket(InetAddress.getLoopbackAddress(), server.boundPort).use { car ->
                car.getOutputStream().write(("{\"type\":1,\"action\":0,\"width\":1280,\"height\":720,\"count\":1,\"trackID0\":0,\"x0\":1,\"y0\":2}" +
                    "{\"type\":1,\"action\":1,\"width\":1280,\"height\":720,\"count\":1,\"trackID0\":0,\"x0\":1,\"y0\":2}").toByteArray())
                car.getOutputStream().flush()
                assertTrue(received.await(3, TimeUnit.SECONDS))
            }
        }
        assertEquals(listOf(0, 1), events.map { (it as UibcEvent.Touch).action })
    }
}
