package org.aionlink.projection

/**
 * Car keys (steering wheel or panel) as the receiver reports them, either in UIBC key JSON or in the CONTROL channel's
 * CustomKeyEvent {1 action, 2 keycode, 3 metaState, 4 timestamp} (UCarProto field numbers; method 12). Codes below 1000
 * are Android KeyEvent codes; 81xx are the protocol's own (UCarProto CustomKeyEvent.KeyCode). The handling list follows
 * the static reading of Samsung's ICCOAKeyEventHandler; which physical button sends which code in the AION is not
 * verified, so every key is logged with its code.
 */
object CarKeys {
    const val MAIN = 8103
    const val TEL = 8104
    const val NAVI = 8105
    const val MEDIA = 8106
    const val VR_START = 8107
    const val VR_STOP = 8108
    const val NAVI_QUIT = 8113
    const val NEXT_FOCUS = 8114
    const val PRE_FOCUS = 8115
    const val IN_FOCUS = 8116
    const val OUT_FOCUS = 8117
    const val MEDIA_FAVOR = 8118

    // Android KeyEvent codes (kept here so this module stays free of android.*).
    const val KEYCODE_HOME = 3
    const val KEYCODE_BACK = 4
    const val KEYCODE_CALL = 5
    const val KEYCODE_ENDCALL = 6
    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_DPAD_CENTER = 23
    const val KEYCODE_VOLUME_UP = 24
    const val KEYCODE_VOLUME_DOWN = 25
    const val KEYCODE_HEADSETHOOK = 79
    const val KEYCODE_SEARCH = 84
    const val KEYCODE_MEDIA_PLAY_PAUSE = 85
    const val KEYCODE_MEDIA_STOP = 86
    const val KEYCODE_MEDIA_NEXT = 87
    const val KEYCODE_MEDIA_PREVIOUS = 88
    const val KEYCODE_MEDIA_PLAY = 126
    const val KEYCODE_MEDIA_PAUSE = 127
    const val KEYCODE_SOFT_LEFT = 1
    const val KEYCODE_SOFT_RIGHT = 2
    const val KEYCODE_MUSIC = 209
    const val KEYCODE_VOICE_ASSIST = 231

    private val passThrough = setOf(KEYCODE_HOME, KEYCODE_BACK, KEYCODE_CALL, KEYCODE_ENDCALL,
        KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_CENTER,
        KEYCODE_HEADSETHOOK, KEYCODE_SEARCH, KEYCODE_MEDIA_PLAY_PAUSE, KEYCODE_MEDIA_STOP, KEYCODE_MEDIA_NEXT,
        KEYCODE_MEDIA_PREVIOUS, KEYCODE_MEDIA_PLAY, KEYCODE_MEDIA_PAUSE, KEYCODE_MUSIC)

    private val mediaKeys = setOf(KEYCODE_HEADSETHOOK, KEYCODE_MEDIA_PLAY_PAUSE, KEYCODE_MEDIA_STOP,
        KEYCODE_MEDIA_NEXT, KEYCODE_MEDIA_PREVIOUS, KEYCODE_MEDIA_PLAY, KEYCODE_MEDIA_PAUSE)

    /**
     * Android key code to hand to Android Auto, or null when the key has no safe equivalent (navigation page/quit,
     * favourites, voice stop). Volume keys are not forwarded: the car keeps its own volume.
     */
    fun toAndroid(code: Int): Int? = when (code) {
        in passThrough -> code
        KEYCODE_VOICE_ASSIST, VR_START -> KEYCODE_SEARCH // Android Auto's voice key
        MAIN -> KEYCODE_HOME
        TEL -> KEYCODE_CALL
        MEDIA -> KEYCODE_MUSIC
        NEXT_FOCUS -> KEYCODE_SOFT_RIGHT // rotary step, as Open Headunit turns it into a scroll-wheel step
        PRE_FOCUS -> KEYCODE_SOFT_LEFT
        IN_FOCUS -> KEYCODE_DPAD_CENTER
        OUT_FOCUS -> KEYCODE_BACK
        else -> null
    }

    fun isMedia(androidCode: Int): Boolean = androidCode in mediaKeys

    fun name(code: Int): String = when (code) {
        KEYCODE_HOME -> "inicio"; KEYCODE_BACK -> "atrás"; KEYCODE_CALL -> "llamar"; KEYCODE_ENDCALL -> "colgar"
        KEYCODE_DPAD_UP -> "arriba"; KEYCODE_DPAD_DOWN -> "abajo"; KEYCODE_DPAD_LEFT -> "izquierda"
        KEYCODE_DPAD_RIGHT -> "derecha"; KEYCODE_DPAD_CENTER -> "aceptar"; KEYCODE_VOLUME_UP -> "volumen+"
        KEYCODE_VOLUME_DOWN -> "volumen-"; KEYCODE_HEADSETHOOK -> "auricular"; KEYCODE_SEARCH -> "voz"
        KEYCODE_MEDIA_PLAY_PAUSE -> "play/pausa"; KEYCODE_MEDIA_STOP -> "stop"; KEYCODE_MEDIA_NEXT -> "siguiente"
        KEYCODE_MEDIA_PREVIOUS -> "anterior"; KEYCODE_MEDIA_PLAY -> "play"; KEYCODE_MEDIA_PAUSE -> "pausa"
        KEYCODE_MUSIC -> "música"; KEYCODE_VOICE_ASSIST -> "asistente"; MAIN -> "principal"; TEL -> "teléfono"
        NAVI -> "navegación"; MEDIA -> "multimedia"; VR_START -> "voz inicio"; VR_STOP -> "voz fin"
        NAVI_QUIT -> "salir navegación"; NEXT_FOCUS -> "foco siguiente"; PRE_FOCUS -> "foco anterior"
        IN_FOCUS -> "entrar foco"; OUT_FOCUS -> "salir foco"; MEDIA_FAVOR -> "favorito"
        else -> "desconocida"
    }

    /** CustomKeyEvent body (already decrypted): action and keycode, or null if either is missing or out of range. */
    fun parseCustomKeyEvent(body: ByteArray): Pair<Int, Int>? {
        var action = 0L; var keycode = -1L // proto3 omits action=0 (down)
        try {
            ProtoVarints.walk(body) { field, value -> when (field) { 1 -> action = value; 2 -> keycode = value } }
        } catch (_: IllegalArgumentException) { return null }
        if (action !in 0..1 || keycode !in 0..65535) return null
        return action.toInt() to keycode.toInt()
    }
}

/** Minimal bounded protobuf reader that reports varint fields and skips the rest. */
object ProtoVarints {
    fun walk(data: ByteArray, onVarint: (Int, Long) -> Unit) {
        var i = 0
        fun varint(): Long {
            var result = 0L; var shift = 0
            while (true) {
                require(i < data.size && shift < 64) { "truncated varint" }
                val b = data[i++].toInt() and 0xff
                result = result or ((b and 0x7f).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
        while (i < data.size) {
            val tag = varint()
            val field = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> onVarint(field, varint())
                1 -> { require(i + 8 <= data.size); i += 8 }
                2 -> { val n = varint(); require(n >= 0 && i + n <= data.size); i += n.toInt() }
                5 -> { require(i + 4 <= data.size); i += 4 }
                else -> throw IllegalArgumentException("wire type")
            }
        }
    }
}
