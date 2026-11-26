package com.firestarterpro.templartv.ui.content

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private val JELLYFIN_PACKAGES = listOf("org.jellyfin.androidtv", "org.jellyfin.mobile")

/** Music tab: opens the Jellyfin app, where the personal music library lives. */
@Composable
fun MusicScreen(contentFocus: FocusRequester, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center).width(560.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Music", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Your own music library is served by Jellyfin.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Surface(
                onClick = { openJellyfin(context) },
                modifier = Modifier.focusRequester(contentFocus),
                shape = ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    "Open Jellyfin music",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
    }
}

private fun openJellyfin(context: Context) {
    val intent = JELLYFIN_PACKAGES.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
    if (intent == null) {
        Toast.makeText(context, "Jellyfin isn't installed on this TV yet.", Toast.LENGTH_LONG).show()
    } else {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
