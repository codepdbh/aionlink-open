package org.aionlink.open

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Starts the owner-installed Open Headunit (AGPL-3.0, third party) in self mode through its documented automation
 * broadcast (headunit.andrerinas.com/guides/automation). In mirror mode Android Auto runs on this phone's screen and
 * the existing mirror path carries it to the car. In the experimental display mode an owner-built Open Headunit that
 * declares android:allowEmbedded is opened on AionLink's own virtual display instead. AionLink bundles none of its
 * code and handles none of Android Auto's authentication; whether self mode works depends on Open Headunit and Android Auto.
 */
object OpenHeadunit {
    const val PACKAGE = "com.andrerinas.headunitrevived"
    private const val RECEIVER = "com.andrerinas.openheadunit.automation.AutomationReceiver"
    private const val PROJECTION_ACTIVITY = "com.andrerinas.openheadunit.aap.AapProjectionActivity"
    private const val ACTION_SELF_MODE = "com.andrerinas.openheadunit.ACTION_START_SELF_MODE"
    private const val ACTION_DISCONNECT = "com.andrerinas.openheadunit.ACTION_DISCONNECT"

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun selfModeBroadcast(context: Context, noUi: Boolean) =
        context.sendBroadcast(Intent(ACTION_SELF_MODE).setComponent(ComponentName(PACKAGE, RECEIVER)).putExtra("no_ui", noUi))

    fun requestSelfModeWithoutUi(context: Context) = selfModeBroadcast(context, noUi = true)

    /** Mirror mode: returns a diagnostic line without any Android Auto content. */
    fun startSelfMode(context: Context): String {
        if (!installed(context)) return "Open Headunit no está instalado"
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
            ?: return "Open Headunit instalado, sin actividad principal"
        // Bring the receiver UI to the front first so Android Auto has a visible, landscape surface to mirror.
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        selfModeBroadcast(context, noUi = false)
        return "Open Headunit abierto y self mode solicitado (broadcast documentado); Android Auto lo gestiona Open Headunit"
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var generation = 0
    private var initiatedSession = false

    /** Refuse a denied launch before sending any command to the separately installed receiver. */
    fun startOnDisplay(context: Context, displayId: Int, report: (String) -> Unit, onLaunched: () -> Unit = {}) {
        if (!installed(context)) { report("Open Headunit no está instalado"); return }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)) {
            report("Android no admite actividades en pantallas secundarias"); return
        }
        val intent = projectionIntent()
        val allowed = try {
            context.getSystemService(android.app.ActivityManager::class.java)
                .isActivityStartAllowedOnDisplay(context, displayId, intent)
        } catch (error: RuntimeException) {
            report("No se pudo comprobar el acceso a la pantalla: ${error.javaClass.simpleName}"); return
        }
        if (!allowed) {
            report("Android no autoriza esta actividad en la pantalla virtual; no se inició ni detuvo Open Headunit")
            return
        }
        val attempt = ++generation
        initiatedSession = true
        context.sendBroadcast(Intent(ACTION_STOP_SERVICE).setComponent(ComponentName(PACKAGE, RECEIVER)))
        handler.postDelayed({
            if (attempt == generation) {
                selfModeBroadcast(context, noUi = true)
                handler.postDelayed({
                    if (attempt == generation) {
                        val shown = showOnDisplay(context, displayId)
                        report("$shown; self mode solicitado sin interfaz propia")
                        if (shown.startsWith("SOLICITADO")) handler.postDelayed({
                            if (attempt == generation) onLaunched()
                        }, PLACEHOLDER_HOLD_MS)
                    }
                }, LAUNCH_AFTER_SELF_MODE_MS)
            }
        }, STOP_SETTLE_MS)
    }

    private fun projectionIntent() = Intent().setComponent(ComponentName(PACKAGE, PROJECTION_ACTIVITY))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private const val ACTION_STOP_SERVICE = "com.andrerinas.openheadunit.ACTION_STOP_SERVICE"
    /** Time for a previous Open Headunit session and its activity to finish before starting a new one. */
    private const val STOP_SETTLE_MS = 2000L
    /** Measured window: at 0 ms the activity closes (no session yet); at ~1 s Open Headunit has opened its own task. */
    private const val LAUNCH_AFTER_SELF_MODE_MS = 300L
    /** Keep AionLink's placeholder until Open Headunit's window exists, then remove it so it does not cover it. */
    private const val PLACEHOLDER_HOLD_MS = 1500L

    /** Requires a visible AionLink activity (Android limits background starts) and an embeddable activity. */
    fun showOnDisplay(context: Context, displayId: Int): String {
        if (!installed(context)) return "Open Headunit no está instalado"
        val intent = projectionIntent()
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS))
            return "Android no admite actividades en pantallas secundarias"
        return try {
            context.startActivity(intent, android.app.ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle())
            "SOLICITADO: Android recibió la petición; la imagen aún no está verificada"
        } catch (error: RuntimeException) {
            "No se pudo abrir Open Headunit en pantalla virtual: ${error.javaClass.simpleName}" +
                if (!CarDisplayHostActivity.present) " (la anfitriona de AionLink no está en la pantalla)" else ""
        }
    }

    /**
     * Ends display mode: stops Open Headunit (its activities finish) and removes AionLink's host, so nothing is moved
     * onto the phone screen when the virtual display is released.
     */
    fun stopOnDisplay(context: Context) {
        ++generation
        if (initiatedSession && installed(context)) context.sendBroadcast(Intent(ACTION_STOP_SERVICE).setComponent(ComponentName(PACKAGE, RECEIVER)))
        initiatedSession = false
        CarDisplayHostActivity.finishCurrent()
    }

    fun disconnect(context: Context) {
        if (installed(context)) context.sendBroadcast(Intent(ACTION_DISCONNECT).setComponent(ComponentName(PACKAGE, RECEIVER)))
    }
}
