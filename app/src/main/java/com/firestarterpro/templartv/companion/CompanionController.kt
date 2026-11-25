package com.firestarterpro.templartv.companion

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.firestarterpro.templartv.FeatureFlags
import com.firestarterpro.templartv.Flag
import com.firestarterpro.templartv.quickmenu.QuickMenuController
import com.firestarterpro.templartv.data.TemplarData
import com.firestarterpro.templartv.data.favourites.FavouritesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface

/** Token hashes in app-private SharedPreferences. Never the tokens themselves. */
private class PrefsPairedDeviceStore(context: Context) : PairedDeviceStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("templar_companion", Context.MODE_PRIVATE)

    override fun load(): List<PairedDevice> = runCatching {
        val a = JSONArray(prefs.getString(KEY, "[]"))
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let { PairedDevice(it.getString("name"), it.getString("hash"), it.getLong("at")) }
        }
    }.getOrDefault(emptyList())

    override fun save(devices: List<PairedDevice>) {
        val a = JSONArray().apply {
            devices.forEach { put(JSONObject().put("name", it.name).put("hash", it.tokenHash).put("at", it.pairedAt)) }
        }
        prefs.edit().putString(KEY, a.toString()).apply()
    }

    private companion object { const val KEY = "paired_devices" }
}

private class AndroidCompanionHost(private val context: Context) : CompanionHost {
    private val favourites = FavouritesRepository(context)

    override val appVersion: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""

    override fun historyEnabled() = FeatureFlags.isEnabled(context, Flag.COMPANION_HISTORY)

    private val pm = context.packageManager

    private data class Launchable(val packageName: String, val component: ComponentName, val label: String, val leanback: Boolean)

    /**
     * Launchable apps without icons or banners. AppRepository.loadApps() decodes every app's art, which
     * takes seconds on the Streamer and made companion calls time out on the tablet.
     */
    private fun launchables(): List<Launchable> {
        val out = LinkedHashMap<String, Launchable>()
        for ((category, leanback) in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER to true, Intent.CATEGORY_LAUNCHER to false)) {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0).forEach { ri ->
                val a = ri.activityInfo ?: return@forEach
                if (a.packageName == context.packageName || out.containsKey(a.packageName)) return@forEach
                out[a.packageName] = Launchable(a.packageName, ComponentName(a.packageName, a.name), ri.loadLabel(pm).toString(), leanback)
            }
        }
        return out.values.sortedBy { it.label.lowercase() }
    }

    private fun isLaunchable(packageName: String) =
        pm.getLeanbackLaunchIntentForPackage(packageName) != null || pm.getLaunchIntentForPackage(packageName) != null

    override suspend fun apps(): List<CompanionApp> {
        val favs = favourites.favourites.first()
        return launchables().map { CompanionApp(it.packageName, it.label, it.packageName in favs, it.leanback) }
    }

    override suspend fun setFavouriteApp(packageName: String, favourite: Boolean): Boolean {
        if (packageName == context.packageName || !isLaunchable(packageName)) return false
        val isFav = packageName in favourites.favourites.first()
        if (isFav != favourite) favourites.toggle(packageName)
        return true
    }

    override fun launch(packageName: String): Boolean? {
        if (packageName == context.packageName) return null
        val intent = (pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName))
            ?: return null
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }
        return mayBeBlocked()
    }

    /** The quick menu when its flag is on, otherwise the old quick-settings panel. */
    override fun openQuickMenu(): Boolean {
        QuickMenuController.openOrLegacy(context)
        return mayBeBlocked()
    }

    /** Background activity starts are allowed for apps holding the overlay permission. */
    private fun mayBeBlocked() = !Settings.canDrawOverlays(context)

    override suspend fun history(profileId: String, limit: Int): List<CompanionHistoryItem> =
        TemplarData.get(context).history.observeRecent(profileId, limit).first().map {
            CompanionHistoryItem(
                id = it.id.toLong(), profileId = it.profileId, provider = it.provider,
                title = it.canonicalTitle.ifBlank { it.originalTitle }, seriesTitle = it.seriesTitle,
                seasonNumber = it.seasonNumber, episodeNumber = it.episodeNumber,
                positionMs = it.playbackPosition, runtimeMs = it.mediaRuntime, completion = it.completionPercentage,
                watched = it.watchedStatus, confidence = it.matchConfidence, evidence = it.evidenceSource,
                startedAt = it.startTimestamp, stoppedAt = it.stopTimestamp,
            )
        }
}

/**
 * Owns the companion server's lifetime: running exactly while the `companion_api` flag is on.
 * Safe to call [init] repeatedly (every MainActivity create).
 */
object CompanionController {
    private const val TAG = "TemplarCompanion"

    @Volatile private var pairing: CompanionPairing? = null
    private var server: CompanionServer? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    @Synchronized
    fun init(context: Context) {
        val app = context.applicationContext
        if (listener == null) listener = FeatureFlags.register(app) { apply(app) }
        apply(app)
    }

    fun pairing(context: Context): CompanionPairing =
        pairing ?: synchronized(this) {
            pairing ?: CompanionPairing(PrefsPairedDeviceStore(context.applicationContext)).also { pairing = it }
        }

    val isRunning: Boolean get() = server?.isRunning == true

    @Synchronized
    private fun apply(context: Context) {
        val on = FeatureFlags.isEnabled(context, Flag.COMPANION_API)
        if (on && server == null) {
            val router = CompanionRouter(pairing(context), TemplarData.get(context).sync, AndroidCompanionHost(context))
            val s = CompanionServer(router)
            // The home rows only exist once the default profile is seeded; nothing else does that yet,
            // so the companion would otherwise list no rows. Idempotent, never touches edited rows.
            Thread({ runCatching { runBlocking { TemplarData.get(context).profiles.ensureDefault() } } }, "companion-seed").start()
            runCatching { s.start() }
                .onSuccess { server = s }
                .onFailure { Log.w(TAG, "Companion server did not start: ${it.javaClass.simpleName}") }
        } else if (!on && server != null) {
            server?.stop()
            server = null
            pairing?.cancelPairing()
        }
    }

    /** IPv4 addresses on private networks, for showing next to the pairing code. */
    fun lanAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filter { it is Inet4Address && it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())
}
