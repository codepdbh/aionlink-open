package org.aionlink.wifi

import org.aionlink.iccoa.UCarFrameWriter
import java.io.ByteArrayOutputStream

/**
 * Independently encoded app messages of fusion cast, from the UCarProto field numbers in MiAuto. AionLink exposes
 * one app, the phone's mirrored screen, with the id and category UCarAdapter uses for its normal cast stand-in.
 * Only fixed labels leave the phone: never the installed apps, their names or their icons.
 */
object CarAppMessages {
    const val APP_ID = 0
    /** ControlManager.getAppDetailInfo always requests this id and reads it as the product icon and name. */
    const val PRODUCT_ID = 1
    /** Original 48x48 solid colour PNG. */
    const val ICON_PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAIAAADYYG7QAAAAO0lEQVR42u3OQQ0AAAgEoKtiaYtawBbOBxsBSPW8EiEhISEhISEhISEhISEhISEhISEhISEhISGhu9ACF0X602a+pTEAAAAASUVORK5CYII="
    private const val LABEL = "AionLink"
    private const val PACKAGE = "org.aionlink.open"
    private const val CATEGORY_MEDIA = 1L
    private const val INVOKE_START = 0L
    private const val INVOKE_RESUME = 3L
    private const val INVOKE_UPDATE = 4L
    private const val STATE_RESUMED = 3L
    private val icon: ByteArray by lazy { java.util.Base64.getDecoder().decode(ICON_PNG_BASE64) }

    /** GetAppListResponse{total(1), appInfo(2)}. The car discards the list unless total equals the entries sent. */
    fun appList(): ByteArray = message {
        number(1, 1)
        bytes(2, message { number(1, APP_ID.toLong()); text(2, LABEL); number(3, CATEGORY_MEDIA) })
    }

    /** GetAppDetailInfoResponse{total(1), appDetailInfo(2)} with both known ids, whatever ids the request lists. */
    fun appDetail(): ByteArray = message {
        number(1, 2)
        for (id in listOf(APP_ID, PRODUCT_ID)) {
            bytes(2, message { number(1, id.toLong()); text(2, LABEL); number(3, CATEGORY_MEDIA); text(4, PACKAGE); bytes(5, icon) })
        }
    }

    /**
     * AppStateChanged{id(1), isNeedShow(2), displayMode(3), state(4), timestamp(5)} answering
     * InvokeApp{id(1), invokeState(3), displayMode(4)}; null for a malformed message or an unknown invocation.
     */
    fun appState(invokeApp: ByteArray, nowMillis: Long): ByteArray? {
        var id = 0L; var invocation = INVOKE_START; var displayMode = 0L
        try {
            ProtoScan.walk(invokeApp, onVarint = { field, value ->
                when (field) { 1 -> id = value; 3 -> invocation = value; 4 -> displayMode = value }
            })
        } catch (_: IllegalArgumentException) { return null }
        if (invocation !in INVOKE_START..INVOKE_UPDATE) return null
        // Invocation and state enums share START..RESUME; UPDATE only changes the display mode of a visible app.
        val state = if (invocation == INVOKE_UPDATE) STATE_RESUMED else invocation
        val show = invocation == INVOKE_START || invocation == INVOKE_RESUME || invocation == INVOKE_UPDATE
        return message {
            number(1, id); number(2, if (show) 1 else 0); number(3, displayMode); number(4, state); number(5, nowMillis)
        }
    }

    private class Writer(val out: ByteArrayOutputStream) {
        /** Proto3 omits default values. */
        fun number(field: Int, value: Long) { if (value != 0L) out.write(UCarFrameWriter.varintField(field, value)) }
        fun text(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))
        fun bytes(field: Int, value: ByteArray) {
            UCarFrameWriter.varint(out, ((field shl 3) or 2).toLong()); UCarFrameWriter.varint(out, value.size.toLong()); out.write(value)
        }
    }

    private fun message(build: Writer.() -> Unit): ByteArray = ByteArrayOutputStream().also { Writer(it).build() }.toByteArray()
}
