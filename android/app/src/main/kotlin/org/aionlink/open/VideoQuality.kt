package org.aionlink.open

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owner's image-quality choice. HIGH encodes at the car's own screen size (sharper, more bitrate); COMPATIBLE keeps
 * the 1280x720 mode that 0.25–0.28 used in the vehicle, as a fallback if a car rejects the larger stream.
 */
class VideoQuality(context: Context) {
    private val preferences = context.getSharedPreferences("aionlink_video", Context.MODE_PRIVATE)
    private val mutableHigh = MutableStateFlow(preferences.getBoolean("high", true))
    val high = mutableHigh.asStateFlow()

    fun setHigh(value: Boolean) {
        preferences.edit().putBoolean("high", value).apply()
        mutableHigh.value = value
    }
}
