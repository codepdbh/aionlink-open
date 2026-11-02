package org.aionlink.projection

import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Independent synthetic sink driving the WFD sequence over loopback; not a vehicle capture. */
class WfdSourceTest {
    private fun send(socket: Socket, text: String) { socket.getOutputStream().write(text.toByteArray()); socket.getOutputStream().flush() }

    @Test fun fullCapabilityExchangeReachesPlay() {
        val loopback = InetAddress.getLoopbackAddress()
        val played = CountDownLatch(1)
        var play: WfdPlay? = null
        val stages = java.util.Collections.synchronizedList(mutableListOf<String>())
        val source = WfdSource(loopback, port = 0, onStatus = { stages += it.stage }, onPlay = { play = it; played.countDown() })
        source.start()
        Socket(loopback, source.boundPort).use { sink ->
            sink.soTimeout = 3000
            val input = BufferedInputStream(sink.getInputStream())
            val m1 = source.read(input)!!
            assertEquals("OPTIONS * RTSP/1.0", m1.startLine); assertEquals("org.wfa.wfd1.0", m1.headers["require"])
            send(sink, "RTSP/1.0 200 OK\r\nCSeq: ${m1.cseq}\r\nPublic: org.wfa.wfd1.0, GET_PARAMETER, SET_PARAMETER\r\n\r\n")
            send(sink, "OPTIONS * RTSP/1.0\r\nCSeq: 1\r\nRequire: org.wfa.wfd1.0\r\n\r\n")
            val m2 = source.read(input)!!
            assertEquals(200, m2.status); assertTrue(m2.headers["public"]!!.contains("org.wfa.wfd1.0"))
            val m3 = source.read(input)!!
            assertTrue(m3.startLine.startsWith("GET_PARAMETER"))
            assertTrue(m3.body.contains("wfd_video_formats") && m3.body.contains("wfd_car_display_mode"))
            val body = "wfd_video_formats: 00 00 01 02 00000021 00000000 00000000 00 0000 0000 00 none none\r\n" +
                "wfd_audio_codecs: LPCM 00000002 00\r\nwfd_client_rtp_ports: RTP/AVP/UDP;unicast 19000 0 mode=play\r\n" +
                "wfd_car_display_mode: synthetic\r\n" +
                "wfd_uibc_capability: input_category_list=GENERIC;generic_cap_list=SingleTouch;hidc_cap_list=none;port=none\r\n"
            send(sink, "RTSP/1.0 200 OK\r\nCSeq: ${m3.cseq}\r\nContent-Type: text/parameters\r\nContent-Length: ${body.length}\r\n\r\n$body")
            val m4 = source.read(input)!!
            val values = m4.parameters()
            assertEquals("28 00 01 02 00000020 00000000 00000000 00 0000 0000 00 none none", values["wfd_video_formats"])
            assertEquals("LPCM 00000002 00", values["wfd_audio_codecs"])
            assertEquals("RTP/AVP/UDP;unicast 19000 0 mode=play", values["wfd_client_rtp_ports"])
            assertTrue(values["wfd_presentation_URL"]!!.endsWith("/wfd1.0/streamid=0 none"))
            assertEquals(WfdFormats.UIBC_CAPABILITY, values["wfd_uibc_capability"])
            send(sink, "RTSP/1.0 200 OK\r\nCSeq: ${m4.cseq}\r\n\r\n")
            val m5 = source.read(input)!!
            assertEquals("SETUP", m5.parameters()["wfd_trigger_method"])
            send(sink, "RTSP/1.0 200 OK\r\nCSeq: ${m5.cseq}\r\n\r\n")
            send(sink, "SETUP rtsp://127.0.0.1/wfd1.0/streamid=0 RTSP/1.0\r\nCSeq: 2\r\nTransport: RTP/AVP/UDP;unicast;client_port=19000\r\n\r\n")
            val m6 = source.read(input)!!
            assertEquals(200, m6.status)
            assertEquals("RTP/AVP/UDP;unicast;client_port=19000;server_port=15550-15551", m6.headers["transport"])
            val session = m6.headers["session"]!!.substringBefore(';')
            send(sink, "PLAY rtsp://127.0.0.1/wfd1.0/streamid=0 RTSP/1.0\r\nCSeq: 3\r\nSession: $session\r\n\r\n")
            assertEquals(200, source.read(input)!!.status)
            assertTrue(played.await(3, TimeUnit.SECONDS))
            assertEquals(19000, play!!.rtpPort); assertEquals(1280, play!!.mode.width)
            assertTrue(play!!.uibc); assertEquals(WfdFormats.LPCM_48K_STEREO, play!!.audioCodec)
            send(sink, "TEARDOWN rtsp://127.0.0.1/wfd1.0/streamid=0 RTSP/1.0\r\nCSeq: 4\r\nSession: $session\r\n\r\n")
            assertEquals(200, source.read(input)!!.status)
        }
        source.close()
        assertEquals("synthetic", WfdFormats.safeExcerpt("synthetic"))
        assertTrue(stages.containsAll(listOf("LISTENING", "SINK_CONNECTED", "M2_OK", "M3_OK", "M4_OK", "M6_SETUP", "PLAYING", "TEARDOWN")))
    }

    /** Runs M1 to M5 against a synthetic sink that reports [rtpPorts]; returns the SETUP-ready input stream. */
    private fun negotiate(source: WfdSource, sink: Socket, rtpPorts: String, extraM3: String = ""): BufferedInputStream {
        sink.soTimeout = 3000
        val input = BufferedInputStream(sink.getInputStream())
        val m1 = source.read(input)!!
        send(sink, "RTSP/1.0 200 OK\r\nCSeq: ${m1.cseq}\r\n\r\n")
        send(sink, "OPTIONS * RTSP/1.0\r\nCSeq: 1\r\n\r\n")
        source.read(input)!!
        val m3 = source.read(input)!!
        val body = "wfd_video_formats: 28 00 01 01 ffffffff ffffffff ffffffff 00 0000 0000 00 none none\r\n" +
            "wfd_client_rtp_ports: $rtpPorts\r\n" + extraM3
        send(sink, "RTSP/1.0 200 OK\r\nCSeq: ${m3.cseq}\r\nContent-Length: ${body.length}\r\n\r\n$body")
        val m4 = source.read(input)!!
        assertEquals(rtpPorts, m4.parameters()["wfd_client_rtp_ports"])
        send(sink, "RTSP/1.0 200 OK\r\nCSeq: ${m4.cseq}\r\n\r\n")
        val m5 = source.read(input)!!
        send(sink, "RTSP/1.0 200 OK\r\nCSeq: ${m5.cseq}\r\n\r\n")
        return input
    }

    @Test fun reportsExtraM3ParametersAndSinkRequestNamesWithoutValues() {
        val loopback = InetAddress.getLoopbackAddress()
        var last = WfdStatus()
        val logs = java.util.concurrent.LinkedBlockingQueue<String>()
        val source = WfdSource(loopback, port = 0, onStatus = { last = it }, onPlay = {}, onLog = { logs += it })
        source.start()
        Socket(loopback, source.boundPort).use { sink ->
            val input = negotiate(source, sink, "RTP/AVP/TCP;unicast 19000 0 mode=play",
                "wfd_av_raw_packet: true\r\nwfd_presentation_URL: rtsp://192.0.2.1/x none\r\n")
            assertEquals("wfd_av_raw_packet=true", last.sinkOther)
            logs.clear()
            val body = "wfd_idr_request\r\nwfd_secret: valor\r\n"
            send(sink, "SET_PARAMETER rtsp://x RTSP/1.0\r\nCSeq: 9\r\nContent-Length: ${body.length}\r\n\r\n$body")
            assertEquals(200, source.read(input)!!.status)
            assertEquals("RTSP del receptor: SET_PARAMETER; parámetros=[wfd_idr_request, wfd_secret]", logs.poll(3, TimeUnit.SECONDS))
        }
        source.close()
    }

    @Test fun tcpUnicastConnectsToSinkListenerAndFramesPackets() {
        val loopback = InetAddress.getLoopbackAddress()
        val played = CountDownLatch(1)
        var play: WfdPlay? = null
        var last = WfdStatus()
        val source = WfdSource(loopback, port = 0, onStatus = { last = it }, onPlay = { play = it; played.countDown() })
        source.start()
        ServerSocket(0, 1, loopback).use { rtpListener ->
            rtpListener.soTimeout = 3000
            Socket(loopback, source.boundPort).use { sink ->
                val input = negotiate(source, sink, "RTP/AVP/TCP;unicast 19000 0 mode=play")
                assertEquals("RTP/AVP/TCP;unicast 19000 0 mode=play", last.sinkRtpPorts)
                send(sink, "SETUP rtsp://127.0.0.1/wfd1.0/streamid=0 RTSP/1.0\r\nCSeq: 2\r\n" +
                    "Transport: RTP/AVP/TCP;unicast;client_port=${rtpListener.localPort}\r\n\r\n")
                rtpListener.accept().use { rtp ->
                    val m6 = source.read(input)!!
                    assertEquals(200, m6.status)
                    assertEquals("RTP/AVP/TCP;unicast;client_port=${rtpListener.localPort};server_port=15550-15551", m6.headers["transport"])
                    send(sink, "PLAY rtsp://127.0.0.1/wfd1.0/streamid=0 RTSP/1.0\r\nCSeq: 3\r\nSession: 1\r\n\r\n")
                    assertEquals(200, source.read(input)!!.status)
                    assertTrue(played.await(3, TimeUnit.SECONDS))
                    assertEquals(rtpListener.localPort, play!!.rtpPort)
                    play!!.rtpTcp!!.getOutputStream().write(TsRtpStreamer.tcpFrame(byteArrayOf(1, 2, 3)))
                    rtp.soTimeout = 3000
                    assertArrayEquals(byteArrayOf(0, 3, 1, 2, 3), rtp.getInputStream().readNBytes(5))
                }
            }
        }
        source.close()
        assertTrue(play!!.rtpTcp!!.isClosed)
    }

    @Test fun tcpSetupFailsWhenSinkIsNotListening() {
        val loopback = InetAddress.getLoopbackAddress()
        val closedPort = ServerSocket(0, 1, loopback).use { it.localPort }
        val stages = java.util.Collections.synchronizedList(mutableListOf<String>())
        val source = WfdSource(loopback, port = 0, onStatus = { stages += it.stage }, onPlay = { fail("no PLAY expected") })
        source.start()
        Socket(loopback, source.boundPort).use { sink ->
            val input = negotiate(source, sink, "RTP/AVP/TCP;unicast 19000 0 mode=play")
            send(sink, "SETUP rtsp://127.0.0.1/wfd1.0/streamid=0 RTSP/1.0\r\nCSeq: 2\r\n" +
                "Transport: RTP/AVP/TCP;unicast;client_port=$closedPort\r\n\r\n")
            assertEquals(500, source.read(input)!!.status)
        }
        source.close()
        assertTrue(stages.contains("M6_TCP_FAILED"))
    }

    @Test fun interleavedIsRejectedInsteadOfBeingTreatedAsUdp() {
        val loopback = InetAddress.getLoopbackAddress()
        val stages = java.util.Collections.synchronizedList(mutableListOf<String>())
        val source = WfdSource(loopback, port = 0, onStatus = { stages += it.stage }, onPlay = { fail("no PLAY expected") })
        source.start()
        Socket(loopback, source.boundPort).use { sink ->
            val input = negotiate(source, sink, "RTP/AVP/TCP;unicast 19000 0 mode=play")
            send(sink, "SETUP rtsp://127.0.0.1/wfd1.0/streamid=0 RTSP/1.0\r\nCSeq: 2\r\nTransport: RTP/AVP/TCP;interleaved=0-1\r\n\r\n")
            assertEquals(461, source.read(input)!!.status)
        }
        source.close()
        assertTrue(stages.contains("M6_UNSUPPORTED"))
    }
}
