package org.aionlink.open

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.aionlink.projection.CarAppSelection

private class CarApp(val key: String, val label: String, val icon: Bitmap)

/**
 * The owner's own app grid. It runs on the phone like any other activity and reaches the car through screen sharing, so
 * the phone must stay on and unlocked. The chosen apps stay on the phone; nothing about them goes to the car or the export.
 */
class CarLauncherActivity : ComponentActivity() {
    companion object {
        private const val PREFERENCES = "aionlink_car_launcher"
        private const val KEY_APPS = "apps"
        private const val ICON_PIXELS = 144

        fun chosenCount(context: Context) = CarAppSelection.decode(selection(context)).size
        private fun selection(context: Context) =
            context.getSharedPreferences(PREFERENCES, MODE_PRIVATE).getString(KEY_APPS, "").orEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { LauncherScreen() } }
    }

    private fun installedApps(): List<CarApp> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(query, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { info ->
                val drawable = info.loadIcon(packageManager)
                val icon = Bitmap.createBitmap(ICON_PIXELS, ICON_PIXELS, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, ICON_PIXELS, ICON_PIXELS)
                drawable.draw(Canvas(icon))
                CarApp(ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToString(),
                    info.loadLabel(packageManager).toString(), icon)
            }.sortedBy { it.label.lowercase() }
    }

    private fun open(app: CarApp) {
        val component = ComponentName.unflattenFromString(app.key) ?: return
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try { startActivity(launch) } catch (_: android.content.ActivityNotFoundException) { }
          catch (_: SecurityException) { }
    }

    @Composable private fun LauncherScreen() {
        var apps by remember { mutableStateOf<List<CarApp>?>(null) }
        var chosen by remember { mutableStateOf(CarAppSelection.decode(selection(this))) }
        var editing by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { installedApps() } }
        fun choose(key: String) {
            chosen = CarAppSelection.toggle(chosen, key)
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putString(KEY_APPS, CarAppSelection.encode(chosen)).apply()
        }
        val available = apps
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 32.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing) "Elige tus apps" else "AionLink", style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                Button(onClick = { editing = !editing }) { Text(if (editing) "Listo" else "Editar") }
            }
            Spacer(Modifier.height(12.dp))
            val shown = when {
                available == null -> emptyList()
                editing -> available
                else -> chosen.mapNotNull { key -> available.firstOrNull { it.key == key } }
            }
            when {
                available == null -> Message("Cargando apps…")
                shown.isEmpty() -> Message("Pulsa Editar y elige tus apps")
                else -> LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize()) {
                    items(shown, key = { it.key }) { app ->
                        val selected = app.key in chosen
                        Column(
                            Modifier.padding(6.dp)
                                .background(if (editing && selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
                                .clickable { if (editing) choose(app.key) else open(app) }
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(app.icon.asImageBitmap(), null, Modifier.size(96.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(app.label, color = MaterialTheme.colorScheme.onBackground, maxLines = 2,
                                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    @Composable private fun Message(text: String) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
