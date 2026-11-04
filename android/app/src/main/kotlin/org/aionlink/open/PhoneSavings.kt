package org.aionlink.open

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional minimum brightness and forced landscape while the phone streams, plus battery and screen state measurements
 * of each stream. Previous values are stored before they change, so they can be restored if the process dies mid-stream.
 */
class PhoneSavings(private val context: Context, private val event: (String) -> Unit) {
    private companion object {
        const val KEY_ENABLED = "min_brightness"
        const val KEY_SAVED_LEVEL = "saved_brightness"
        const val KEY_SAVED_MODE = "saved_brightness_mode"
        const val MIN_BRIGHTNESS = 1
        const val KEY_LANDSCAPE = "force_landscape"
        const val KEY_LAUNCHER = "car_launcher"
        const val KEY_SAVED_AUTO_ROTATE = "saved_auto_rotate"
        const val KEY_SAVED_ROTATION = "saved_rotation"
    }
    private val preferences = context.getSharedPreferences("aionlink_savings", Context.MODE_PRIVATE)
    private val resolver get() = context.contentResolver
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))
    val enabled = mutableEnabled.asStateFlow()
    private val mutableLandscape = MutableStateFlow(preferences.getBoolean(KEY_LANDSCAPE, true))
    /** Lets apps that follow auto-rotate fill the car's wide screen instead of a portrait strip. */
    val landscape = mutableLandscape.asStateFlow()
    private val mutableLauncher = MutableStateFlow(preferences.getBoolean(KEY_LAUNCHER, true))
    /** Off gives plain screen sharing of whatever the phone is showing. */
    val launcher = mutableLauncher.asStateFlow()
    private var streaming = false
    private var startedAt = 0L
    private var startCharge = 0L
    private var startCharging = false
    private var receiver: BroadcastReceiver? = null

    val canWrite get() = Settings.System.canWrite(context)

    /** Undoes a stream that ended with the process, for instance after a crash. */
    @Synchronized fun restorePending() {
        if (streaming) return
        if (preferences.contains(KEY_SAVED_LEVEL)) restoreBrightness("Brillo restaurado tras una sesión interrumpida")
        restoreRotation()
    }

    @Synchronized fun setLauncher(value: Boolean) {
        preferences.edit().putBoolean(KEY_LAUNCHER, value).apply()
        mutableLauncher.value = value
    }

    @Synchronized fun setLandscape(value: Boolean) {
        preferences.edit().putBoolean(KEY_LANDSCAPE, value).apply()
        mutableLandscape.value = value
        if (streaming) { if (value) applyLandscape() else restoreRotation() }
    }

    @Synchronized fun setEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        mutableEnabled.value = value
        if (streaming) { if (value) applyMinimum() else restoreBrightness("Brillo restaurado") }
    }

    /** [units] reads the stream's access units, to show whether video kept flowing when the screen state changed. */
    @Synchronized fun onStreamStarted(units: () -> Long) {
        if (streaming) return
        streaming = true
        startedAt = System.currentTimeMillis()
        startCharge = charge()
        startCharging = charging()
        if (mutableEnabled.value) applyMinimum()
        if (mutableLandscape.value) applyLandscape()
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ignored: Context?, intent: Intent?) {
                val state = if (intent?.action == Intent.ACTION_SCREEN_OFF) "apagada" else "encendida"
                event("Pantalla del teléfono $state; unidades de vídeo=${units()}")
            }
        }.also { context.registerReceiver(it, IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }) }
    }

    @Synchronized fun onStreamStopped() {
        if (!streaming) return
        event("Batería de la sesión: ${summary()}")
        streaming = false
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
        if (preferences.contains(KEY_SAVED_LEVEL)) restoreBrightness("Brillo restaurado")
        restoreRotation()
    }

    /** Android's charge counter moves in coarse steps, and charging from the car hides the drain. */
    @Synchronized fun summary(): String {
        if (!streaming) return "sin sesión de vídeo activa"
        val seconds = (System.currentTimeMillis() - startedAt) / 1000
        val usedMah = (startCharge - charge()) / 1000
        val average = if (seconds > 0) usedMah * 3600 / seconds else 0
        return "${seconds}s; consumo=$usedMah mAh; media=$average mA; cargando=${startCharging || charging()}; " +
            "brillo mínimo=${preferences.contains(KEY_SAVED_LEVEL)}"
    }

    private fun charge() = context.getSystemService(BatteryManager::class.java).getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    private fun charging() = context.getSystemService(BatteryManager::class.java).isCharging

    private fun applyMinimum() {
        if (!canWrite) { event("Brillo mínimo no aplicado: falta el permiso de ajustes del sistema"); return }
        if (preferences.contains(KEY_SAVED_LEVEL)) return
        try {
            val level = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
            val mode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            // Stored synchronously first: a death right after the change must still find the original values.
            preferences.edit().putInt(KEY_SAVED_LEVEL, level).putInt(KEY_SAVED_MODE, mode).commit()
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, MIN_BRIGHTNESS)
            event("Brillo mínimo aplicado durante la proyección")
        } catch (_: Settings.SettingNotFoundException) { event("Brillo mínimo no aplicado: ajuste no disponible") }
        catch (_: SecurityException) { event("Brillo mínimo no aplicado: permiso rechazado") }
    }

    private fun applyLandscape() {
        if (!canWrite) { event("Horizontal no aplicado: falta el permiso de ajustes del sistema"); return }
        if (preferences.contains(KEY_SAVED_AUTO_ROTATE)) return
        try {
            val auto = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION)
            val rotation = Settings.System.getInt(resolver, Settings.System.USER_ROTATION, Surface.ROTATION_0)
            preferences.edit().putInt(KEY_SAVED_AUTO_ROTATE, auto).putInt(KEY_SAVED_ROTATION, rotation).commit()
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
            Settings.System.putInt(resolver, Settings.System.USER_ROTATION, Surface.ROTATION_90)
            event("Pantalla horizontal aplicada durante la proyección")
        } catch (_: Settings.SettingNotFoundException) { event("Horizontal no aplicado: ajuste no disponible") }
        catch (_: SecurityException) { event("Horizontal no aplicado: permiso rechazado") }
        catch (_: IllegalArgumentException) { event("Horizontal no aplicado: ajuste rechazado") }
    }

    private fun restoreRotation() {
        if (!preferences.contains(KEY_SAVED_AUTO_ROTATE)) return
        if (!canWrite) { event("Rotación sin restaurar: falta el permiso de ajustes del sistema"); return }
        try {
            Settings.System.putInt(resolver, Settings.System.USER_ROTATION, preferences.getInt(KEY_SAVED_ROTATION, Surface.ROTATION_0))
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, preferences.getInt(KEY_SAVED_AUTO_ROTATE, 1))
            preferences.edit().remove(KEY_SAVED_AUTO_ROTATE).remove(KEY_SAVED_ROTATION).apply()
            event("Rotación restaurada")
        } catch (_: SecurityException) { event("Rotación sin restaurar: permiso rechazado") }
        catch (_: IllegalArgumentException) { event("Rotación sin restaurar: ajuste rechazado") }
    }

    private fun restoreBrightness(message: String) {
        if (!preferences.contains(KEY_SAVED_LEVEL)) return
        if (!canWrite) { event("Brillo sin restaurar: falta el permiso de ajustes del sistema"); return }
        try {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, preferences.getInt(KEY_SAVED_LEVEL, MIN_BRIGHTNESS))
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, preferences.getInt(KEY_SAVED_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL))
            preferences.edit().remove(KEY_SAVED_LEVEL).remove(KEY_SAVED_MODE).apply()
            event(message)
        } catch (_: SecurityException) { event("Brillo sin restaurar: permiso rechazado") }
    }
}
