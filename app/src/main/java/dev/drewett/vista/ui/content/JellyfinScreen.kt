package com.firestarterpro.templartv.ui.content

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.firestarterpro.templartv.companion.CompanionController
import com.firestarterpro.templartv.jellyfin.JellyfinClient
import com.firestarterpro.templartv.jellyfin.JellyfinSetupServer
import com.firestarterpro.templartv.jellyfin.JellyfinConfig
import com.firestarterpro.templartv.jellyfin.JellyfinItem
import com.firestarterpro.templartv.jellyfin.JellyfinKind

private val JELLYFIN_PACKAGES = listOf("org.jellyfin.androidtv", "org.jellyfin.mobile")

private sealed interface JfState {
    object Loading : JfState
    object NeedsSetup : JfState
    data class Failed(val message: String) : JfState
    data class Ready(val items: List<JellyfinItem>) : JfState
}

/**
 * My Movies and Music tabs: posters or album covers from the personal Jellyfin server.
 * Without a server key it shows a setup form instead (key stays on this device, never in git).
 */
@Composable
fun JellyfinScreen(
    title: String,
    kind: JellyfinKind,
    contentFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val config = remember { JellyfinConfig(context) }
    var state by remember { mutableStateOf<JfState>(if (config.isConfigured) JfState.Loading else JfState.NeedsSetup) }
    var editing by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        if (!config.isConfigured) { state = JfState.NeedsSetup; return@LaunchedEffect }
        state = JfState.Loading
        state = JellyfinClient.list(config, kind).fold(
            onSuccess = { JfState.Ready(it) },
            onFailure = { JfState.Failed(it.message ?: "Couldn't reach Jellyfin") },
        )
    }

    Column(modifier.fillMaxSize().padding(top = 96.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
        )
        val s = state
        when {
            editing || s is JfState.NeedsSetup -> SetupForm(config, contentFocus, onDone = { editing = false; reload++ }, onCancel = if (s is JfState.NeedsSetup) null else ({ editing = false }))
            s is JfState.Loading -> Centered("Loading from Jellyfin…")
            s is JfState.Failed -> Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(s.message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Button("Try again", Modifier.focusRequester(contentFocus)) { reload++ }
                Button("Server settings") { editing = true }
            }
            s is JfState.Ready -> {
                if (s.items.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Nothing found in this Jellyfin library yet.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                        Button("Server settings", Modifier.focusRequester(contentFocus)) { editing = true }
                    }
                } else {
                    val ratio = if (kind == JellyfinKind.MOVIE) 2f / 3f else 1f
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 168.dp),
                        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        itemsIndexed(s.items, key = { _, it -> it.id }) { index, item ->
                            PosterTile(
                                item, ratio,
                                Modifier.then(if (index == 0) Modifier.focusRequester(contentFocus) else Modifier),
                            ) { playInJellyfin(context, item) }
                        }
                        item(key = "settings") {
                            Button("Server settings") { editing = true }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterTile(item: JellyfinItem, ratio: Float, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val ctx = LocalContext.current
            val key = remember { JellyfinConfig(ctx).apiKey }
            AsyncImage(
                model = remember(item.imageUrl) {
                    coil.request.ImageRequest.Builder(ctx).data(item.imageUrl)
                        .addHeader("Authorization", JellyfinClient.authHeader(key)).build()
                },
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        if (item.subtitle.isNotEmpty()) {
            Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun SetupForm(config: JellyfinConfig, contentFocus: FocusRequester, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    var url by remember { mutableStateOf(config.serverUrl.ifEmpty { JellyfinConfig.DEFAULT_URL }) }
    var key by remember { mutableStateOf(config.apiKey) }
    // Easier than the remote: a small web page on the LAN, open only while this form shows.
    val server = remember {
        JellyfinSetupServer(JellyfinConfig.DEFAULT_URL, onSave = { u, k ->
            config.serverUrl = u
            config.apiKey = k
            android.os.Handler(android.os.Looper.getMainLooper()).post { onDone() }
        })
    }
    DisposableEffect(server) {
        server.start()
        onDispose { server.stop() }
    }
    val ip = remember { CompanionController.lanAddresses().firstOrNull() }
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 16.dp).width(960.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Connect to your Jellyfin server. The key is saved on this TV only.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (ip != null) {
            Text(
                "Easiest: on your phone or PC, open  http://$ip:${JellyfinSetupServer.PORT}  and enter code  ${server.code}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Or type them here with the remote:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        }
        Field("Server address", url, { url = it }, Modifier.focusRequester(contentFocus))
        Field("API key (Jellyfin dashboard > API Keys)", key, { key = it })
        Button("Save and load") {
            config.serverUrl = url
            config.apiKey = key
            onDone()
        }
        if (onCancel != null) Button("Cancel") { onCancel() }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Box(
            Modifier
                .width(640.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Button(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(320.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

/** Opens the Jellyfin app on the item. If the app can't take the link it just opens, and a toast names the title. */
private fun playInJellyfin(context: Context, item: JellyfinItem) {
    val pm = context.packageManager
    val pkg = JELLYFIN_PACKAGES.firstOrNull { pm.getLaunchIntentForPackage(it) != null }
    if (pkg == null) {
        Toast.makeText(context, "Jellyfin isn't installed on this TV.", Toast.LENGTH_LONG).show()
        return
    }
    val link = Intent(Intent.ACTION_VIEW, Uri.parse("jellyfin://items/${item.id}")).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = runCatching { context.startActivity(link) }.isSuccess
    if (!opened) {
        context.startActivity(pm.getLaunchIntentForPackage(pkg)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Toast.makeText(context, "Opened Jellyfin. Find: ${item.title}", Toast.LENGTH_LONG).show()
    }
}
