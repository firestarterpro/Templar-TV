package com.firestarterpro.templartv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.firestarterpro.templartv.companion.CompanionController
import com.firestarterpro.templartv.companion.CompanionServer
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.firestarterpro.templartv.ui.theme.VistaTheme

/** Pairing code, address and unpair for the companion API. The code is shown here only. */
@Composable
private fun CompanionPairingPanel() {
    val context = LocalContext.current
    val pairing = remember { CompanionController.pairing(context) }
    var code by remember { mutableStateOf(pairing.currentCode()) }
    var paired by remember { mutableIntStateOf(pairing.pairedDevices().size) }
    val addresses = remember { CompanionController.lanAddresses() }
    // Refresh while the panel is visible: the code expires and devices pair from the tablet.
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            code = pairing.currentCode()
            paired = pairing.pairedDevices().size
        }
    }
    val colors = ClickableSurfaceDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onSurface,
        focusedContentColor = MaterialTheme.colorScheme.onPrimary,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Companion: " + (if (CompanionController.isRunning) "listening" else "not running") +
                " on " + (addresses.joinToString(", ").ifBlank { "no LAN address" }) + ", port ${CompanionServer.DEFAULT_PORT}" +
                " · $paired paired",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = { code = pairing.startPairing() }, colors = colors,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp))) {
                Text("Show pairing code", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
            }
            Surface(onClick = { pairing.unpairAll(); paired = 0 }, colors = colors,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp))) {
                Text("Unpair all devices", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
            }
            code?.let {
                Text(it.chunked(3).joinToString(" "), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

/** Settings → Features: every feature flag as a switch. Changes apply immediately, no rebuild. */
class FeaturesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VistaTheme(dark = isSystemInDarkTheme()) {
                val flags by FeatureFlags.observe(this).collectAsStateWithLifecycle(initialValue = emptyMap())
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 48.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Features", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "Switch features on or off. Experimental ones start off. Press Back to return.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    if (flags[Flag.COMPANION_API] == true) CompanionPairingPanel()
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(Flag.entries.toList()) { flag ->
                            val on = flags[flag] ?: flag.defaultEnabled
                            Surface(
                                onClick = { FeatureFlags.set(this@FeaturesActivity, flag, !on) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            flag.label + if (flag.experimental) "  (experimental)" else "",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text(flag.description, style = MaterialTheme.typography.bodyLarge)
                                    }
                                    Text(if (on) "On" else "Off", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
