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
import androidx.compose.runtime.getValue
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
