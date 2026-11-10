package org.aionlink.open

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import org.aionlink.projection.TsRtpStreamer

/**
 * Captures other apps' playback with the public AudioPlaybackCapture API (Samsung's default music path also
 * captures playback at 48 kHz stereo PCM16 and hands it to the display stream) and encodes AAC-LC frames
 * with ADTS headers. Android only allows MEDIA, GAME and UNKNOWN usages for non-system apps, and apps may
 * opt out of capture; navigation prompts are therefore not captured.
 */
class AudioCapture(
    private val projection: MediaProjection,
    private val onFrame: (ByteArray, Long) -> Unit,
    private val onError: (String) -> Unit,
    /** Peak of the captured PCM, 0 to 100, every [AacPump.LEVEL_REPORT_MS]: 0 means nothing capturable is playing. */
    private val onLevel: (Int) -> Unit = {}
) : AutoCloseable {
    companion object { const val SAMPLE_RATE = AacPump.SAMPLE_RATE; const val CHANNELS = AacPump.CHANNELS }
    private var record: AudioRecord? = null
    private var pump: AacPump? = null

    /** Caller must hold RECORD_AUDIO; checked in ProjectionSession before starting. */
    @SuppressLint("MissingPermission")
    fun start() {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val capture = AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(maxOf(minimum, 8192) * 2)
            .setAudioPlaybackCaptureConfig(config).build()
        if (capture.state != AudioRecord.STATE_INITIALIZED) { capture.release(); onError("AUDIORECORD_NO_INICIALIZADO"); return }
        record = capture
        capture.startRecording()
        pump = AacPump({ chunk, size -> capture.read(chunk, 0, size).coerceAtLeast(0) }, onFrame, onError, onLevel).also { it.start() }
    }

    override fun close() {
        pump?.close(); pump = null
        record?.let { try { it.stop() } catch (_: IllegalStateException) { }; it.release() }
        record = null
    }
}

/**
 * Encodes 48 kHz stereo PCM16 from [read] into AAC-LC frames with ADTS headers. [read] fills the array from its
 * start with at most the given number of bytes and returns the count, 0 when nothing arrived, or negative on error.
 * [read] gets a heap array, never codec memory: it may block (a pipe) while [close] releases the codec, and a
 * native copy into a released codec buffer crashes the process (SIGSEGV seen on 2026-11-09 with the bridge pipe).
 */
class AacPump(
    private val read: (ByteArray, Int) -> Int,
    private val onFrame: (ByteArray, Long) -> Unit,
    private val onError: (String) -> Unit,
    private val onLevel: (Int) -> Unit = {},
) : AutoCloseable {
    companion object { const val SAMPLE_RATE = 48000; const val CHANNELS = 2; const val LEVEL_REPORT_MS = 5000L; const val CHUNK_BYTES = 4096 }
    @Volatile private var running = false
    private var codec: MediaCodec? = null
    /** Held while touching codec buffers and while releasing the codec, so neither copy runs on freed memory. */
    private val lock = Any()

    fun start() {
        val aac = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(aac, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        codec = encoder
        running = true
        Thread({ feed(encoder) }, "AionLink-audio-in").apply { isDaemon = true; start() }
        Thread({ drain(encoder) }, "AionLink-audio-out").apply { isDaemon = true; start() }
    }

    private fun feed(encoder: MediaCodec) {
        val bytesPerSecond = SAMPLE_RATE * CHANNELS * 2L
        val chunk = ByteArray(CHUNK_BYTES)
        var peak = 0
        var reportedAt = System.currentTimeMillis()
        try {
            while (running) {
                val count = read(chunk, CHUNK_BYTES)
                if (!running) return
                if (count < 0) { if (running) onError("AUDIO_IN_FIN"); return }
                if (count == 0) { Thread.sleep(5); continue }
                for (i in 0 until count / 2) {
                    val sample = (chunk[2 * i].toInt() and 0xff) or (chunk[2 * i + 1].toInt() shl 8)
                    peak = maxOf(peak, kotlin.math.abs(sample.toShort().toInt()))
                }
                val now = System.currentTimeMillis()
                if (now - reportedAt >= LEVEL_REPORT_MS) { onLevel(peak * 100 / 32768); peak = 0; reportedAt = now }
                // Timestamp of the first sample in this chunk, on the same monotonic clock as the video encoder.
                val ptsUs = System.nanoTime() / 1000 - count * 1_000_000L / bytesPerSecond
                var offset = 0
                while (offset < count && running) {
                    val index = encoder.dequeueInputBuffer(20_000)
                    if (index < 0) continue
                    synchronized(lock) {
                        if (!running) return
                        val buffer = encoder.getInputBuffer(index) ?: return@synchronized
                        buffer.clear()
                        val size = minOf(buffer.remaining(), count - offset)
                        buffer.put(chunk, offset, size)
                        encoder.queueInputBuffer(index, 0, size, ptsUs + offset * 1_000_000L / bytesPerSecond, 0)
                        offset += size
                    }
                }
            }
        } catch (error: Exception) { if (running) onError("AUDIO_IN_${error.javaClass.simpleName}") }
    }

    private fun drain(encoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        try {
            while (running) {
                val index = encoder.dequeueOutputBuffer(info, 50_000)
                if (index < 0) continue
                val frame = synchronized(lock) {
                    if (!running) return
                    val buffer = encoder.getOutputBuffer(index)
                    val copy = if (buffer != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        ByteArray(7 + info.size).also {
                            System.arraycopy(TsRtpStreamer.adtsHeader(info.size), 0, it, 0, 7)
                            buffer.position(info.offset); buffer.get(it, 7, info.size)
                        }
                    } else null
                    encoder.releaseOutputBuffer(index, false)
                    copy
                }
                frame?.let { onFrame(it, info.presentationTimeUs) }
            }
        } catch (error: Exception) { if (running) onError("AUDIO_OUT_${error.javaClass.simpleName}") }
    }

    override fun close() {
        val encoder = synchronized(lock) { running = false; codec.also { codec = null } }
        encoder?.let { try { it.stop() } catch (_: IllegalStateException) { }; it.release() }
    }
}
