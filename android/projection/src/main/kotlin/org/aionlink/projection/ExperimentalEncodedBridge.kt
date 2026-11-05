package org.aionlink.projection

/**
 * Offline bridge for independently produced Annex-B H.264 and AAC/ADTS frames.
 * No Android Auto receiver or network endpoint is implemented here. Each source gets
 * an opaque generation so late callbacks cannot write into a later session.
 */
class ExperimentalEncodedBridge(private val newStreamer: (Boolean) -> TsRtpStreamer) : AutoCloseable {
    enum class Result { ACCEPTED, STALE, NEED_KEYFRAME, BAD_TIMESTAMP, INVALID_SIZE, AUDIO_DISABLED }
    class Session internal constructor(internal val owner: ExperimentalEncodedBridge, internal val generation: Long)

    private var generation = 0L
    private var current: TsRtpStreamer? = null
    private var audioEnabled = false
    private var hasKeyframe = false
    private var videoPts = -1L
    private var audioPts = -1L
    private var closed = false

    /** Starts a separate muxing session; callers must negotiate the actual codecs independently. */
    @Synchronized fun start(audio: Boolean): Session {
        check(!closed) { "Bridge is closed" }
        generation++
        current = null
        hasKeyframe = false; videoPts = -1; audioPts = -1; audioEnabled = false
        val streamer = newStreamer(audio)
        current = streamer
        audioEnabled = audio
        return Session(this, generation)
    }

    @Synchronized fun video(session: Session, bytes: ByteArray, ptsUs: Long, keyframe: Boolean): Result {
        val streamer = active(session) ?: return Result.STALE
        if (bytes.isEmpty() || bytes.size > MAX_VIDEO_BYTES) return Result.INVALID_SIZE
        if (ptsUs < 0 || ptsUs <= videoPts || ptsUs > MAX_PTS_US) return Result.BAD_TIMESTAMP
        if (!hasKeyframe && !keyframe) return Result.NEED_KEYFRAME
        streamer.onAccessUnit(bytes, ptsUs, keyframe)
        videoPts = ptsUs; hasKeyframe = true
        return Result.ACCEPTED
    }

    @Synchronized fun audio(session: Session, adts: ByteArray, ptsUs: Long): Result {
        val streamer = active(session) ?: return Result.STALE
        if (!audioEnabled) return Result.AUDIO_DISABLED
        if (adts.isEmpty() || adts.size > MAX_AUDIO_BYTES) return Result.INVALID_SIZE
        if (ptsUs < 0 || ptsUs <= audioPts || ptsUs > MAX_PTS_US) return Result.BAD_TIMESTAMP
        if (!hasKeyframe) return Result.NEED_KEYFRAME
        streamer.onAudioFrame(adts, ptsUs)
        audioPts = ptsUs
        return Result.ACCEPTED
    }

    @Synchronized fun stop(session: Session) {
        if (active(session) != null) { generation++; current = null }
    }

    private fun active(session: Session): TsRtpStreamer? =
        if (!closed && session.owner === this && session.generation == generation) current else null

    @Synchronized override fun close() { closed = true; generation++; current = null }

    companion object {
        const val MAX_VIDEO_BYTES = 2 * 1024 * 1024
        const val MAX_AUDIO_BYTES = 8191 // ADTS frame length is 13 bits.
        const val MAX_PTS_US = Long.MAX_VALUE / 90
    }
}
