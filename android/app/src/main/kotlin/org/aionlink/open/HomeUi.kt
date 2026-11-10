package org.aionlink.open

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Deep blue identity shared with OpenHeadunit Lite; follows the system light/dark setting. */
@Composable fun AionLinkTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF8AB4FF), onPrimary = Color(0xFF002E6B),
        primaryContainer = Color(0xFF14315F), onPrimaryContainer = Color(0xFFD8E2FF),
        secondaryContainer = Color(0xFF8AB4FF), onSecondaryContainer = Color(0xFF002E6B),
        background = Color(0xFF0B1220), onBackground = Color(0xFFE2E6EE),
        surface = Color(0xFF0B1220), onSurface = Color(0xFFE2E6EE),
        surfaceContainer = Color(0xFF162036), surfaceContainerHigh = Color(0xFF1C2841),
        surfaceVariant = Color(0xFF1C2841), onSurfaceVariant = Color(0xFFB7C0D2),
    ) else lightColorScheme(
        primary = Color(0xFF1F5FD1), onPrimary = Color.White,
        primaryContainer = Color(0xFFD8E2FF), onPrimaryContainer = Color(0xFF001A43),
        secondaryContainer = Color(0xFF1F5FD1), onSecondaryContainer = Color.White,
        background = Color(0xFFF6F8FC), onBackground = Color(0xFF151A23),
        surface = Color(0xFFF6F8FC), onSurface = Color(0xFF151A23),
        surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFEDF1F9),
        surfaceVariant = Color(0xFFE3E8F2), onSurfaceVariant = Color(0xFF4A5366),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

/** What the owner needs to know at a glance. */
enum class HomeTone { IDLE, BUSY, LIVE, PROBLEM }
data class HomeStatus(val title: String, val detail: String, val tone: HomeTone)

private val Live = Color(0xFF2FBF71)
private val Busy = Color(0xFFF2A93B)
private val Problem = Color(0xFFE5534B)

@Composable fun HomeHeader(appName: String, version: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Image(painterResource(R.drawable.aionlink_logo), contentDescription = null, modifier = Modifier.size(56.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(appName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("Android Auto en tu GAC AION • v$version",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun StatusCard(status: HomeStatus, busy: Boolean) {
    val accent = when (status.tone) {
        HomeTone.IDLE -> MaterialTheme.colorScheme.primary
        HomeTone.BUSY -> Busy
        HomeTone.LIVE -> Live
        HomeTone.PROBLEM -> Problem
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(accent))
                Text(status.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            if (status.detail.isNotBlank()) Text(status.detail, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)), color = accent)
        }
    }
}

/** One requirement for a new phone; [action] is null when the owner has to do it elsewhere. */
data class SetupItem(val title: String, val detail: String, val done: Boolean, val actionLabel: String?, val action: (() -> Unit)?)

@Composable fun SetupCard(items: List<SetupItem>) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Antes de conectar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (item.done) "✓" else "•", style = MaterialTheme.typography.titleMedium,
                        color = if (item.done) Live else MaterialTheme.colorScheme.onPrimaryContainer)
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(item.detail, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                    }
                    if (!item.done && item.action != null && item.actionLabel != null)
                        FilledTonalButton(onClick = item.action) { Text(item.actionLabel) }
                }
            }
        }
    }
}

/** A found receiver, shown as one tappable row. */
@Composable fun ReceiverRow(name: String, enabled: Boolean, onConnect: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text("Receptor ICCOA CarLink", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onConnect, enabled = enabled) { Text("Conectar") }
        }
    }
}
