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
    private val onError: (String) -> Unit
) : AutoCloseable {
    companion object { const val SAMPLE_RATE = 48000; const val CHANNELS = 2 }
    @Volatile private var running = false
    private var record: AudioRecord? = null
    private var codec: MediaCodec? = null

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
        val aac = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(aac, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        record = capture; codec = encoder
        capture.startRecording()
        running = true
        Thread({ feed(capture, encoder) }, "AionLink-audio-in").apply { isDaemon = true; start() }
        Thread({ drain(encoder) }, "AionLink-audio-out").apply { isDaemon = true; start() }
    }

    private fun feed(capture: AudioRecord, encoder: MediaCodec) {
        val bytesPerSecond = SAMPLE_RATE * CHANNELS * 2L
        try {
            while (running) {
                val index = encoder.dequeueInputBuffer(20_000)
                if (index < 0) continue
                val buffer = encoder.getInputBuffer(index) ?: continue
                buffer.clear()
                val count = capture.read(buffer, minOf(buffer.capacity(), 4096))
                if (count <= 0) { encoder.queueInputBuffer(index, 0, 0, System.nanoTime() / 1000, 0); continue }
                // Timestamp of the first sample in this chunk, on the same monotonic clock as the video encoder.
                val ptsUs = System.nanoTime() / 1000 - count * 1_000_000L / bytesPerSecond
                encoder.queueInputBuffer(index, 0, count, ptsUs, 0)
            }
        } catch (error: Exception) { if (running) onError("AUDIO_IN_${error.javaClass.simpleName}") }
    }

    private fun drain(encoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        try {
            while (running) {
                val index = encoder.dequeueOutputBuffer(info, 50_000)
                if (index < 0) continue
                val buffer = encoder.getOutputBuffer(index)
                if (buffer != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                    val frame = ByteArray(7 + info.size)
                    System.arraycopy(TsRtpStreamer.adtsHeader(info.size), 0, frame, 0, 7)
                    buffer.position(info.offset); buffer.get(frame, 7, info.size)
                    onFrame(frame, info.presentationTimeUs)
                }
                encoder.releaseOutputBuffer(index, false)
            }
        } catch (error: Exception) { if (running) onError("AUDIO_OUT_${error.javaClass.simpleName}") }
    }

    override fun close() {
        running = false
        record?.let { try { it.stop() } catch (_: IllegalStateException) { }; it.release() }
        record = null
        codec?.let { try { it.stop() } catch (_: IllegalStateException) { }; it.release() }
        codec = null
    }
}
