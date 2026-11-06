package org.aionlink.open

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Starts the owner-installed Open Headunit (AGPL-3.0, third party) in self mode through its documented automation
 * broadcast (headunit.andrerinas.com/guides/automation): Android Auto then runs on this phone's screen and the existing
 * mirror path carries it, with touch and audio, to the car. AionLink bundles none of its code and handles none of
 * Android Auto's authentication; whether self mode works depends on Open Headunit and the Android Auto version.
 */
object OpenHeadunit {
    const val PACKAGE = "com.andrerinas.headunitrevived"
    private const val RECEIVER = "com.andrerinas.openheadunit.automation.AutomationReceiver"
    private const val ACTION_SELF_MODE = "com.andrerinas.openheadunit.ACTION_START_SELF_MODE"
    private const val ACTION_DISCONNECT = "com.andrerinas.openheadunit.ACTION_DISCONNECT"

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    /** Returns a diagnostic line without any Android Auto content. */
    fun startSelfMode(context: Context): String {
        if (!installed(context)) return "Open Headunit no está instalado"
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
            ?: return "Open Headunit instalado, sin actividad principal"
        // Bring the receiver UI to the front first so Android Auto has a visible, landscape surface to mirror.
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        context.sendBroadcast(Intent(ACTION_SELF_MODE).setComponent(ComponentName(PACKAGE, RECEIVER)).putExtra("no_ui", false))
        return "Open Headunit abierto y self mode solicitado (broadcast documentado); Android Auto lo gestiona Open Headunit"
    }

    /** Called only from the visible AionLink activity after the owner selects the experimental source. */
    fun showOnDisplay(context: Context, displayId: Int): String {
        if (!installed(context)) return "Open Headunit no est? instalado"
        val intent = Intent().setComponent(ComponentName(PACKAGE, "com.andrerinas.openheadunit.aap.AapProjectionActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val manager = context.getSystemService(android.app.ActivityManager::class.java)
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS))
            return "Android no admite actividades en pantallas secundarias"
        if (!manager.isActivityStartAllowedOnDisplay(context, displayId, intent))
            return "Android rechaz? la actividad en esta pantalla virtual; comprueba la versi?n modificada de Open Headunit"
        return try {
            context.startActivity(intent, android.app.ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle())
            "Lanzamiento solicitado en pantalla virtual; imagen, pantalla apagada y t?ctil a?n sin verificar"
        } catch (error: RuntimeException) {
            "No se pudo abrir Open Headunit en pantalla virtual: ${error.javaClass.simpleName}"
        }
    }

    fun disconnect(context: Context) {
        if (installed(context)) context.sendBroadcast(Intent(ACTION_DISCONNECT).setComponent(ComponentName(PACKAGE, RECEIVER)))
    }
}
