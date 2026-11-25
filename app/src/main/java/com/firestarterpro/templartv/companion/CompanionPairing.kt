package com.firestarterpro.templartv.companion

import java.security.MessageDigest
import java.security.SecureRandom

/** A paired client. Only the SHA-256 hash of its token is ever stored. */
data class PairedDevice(val name: String, val tokenHash: String, val pairedAt: Long)

/** Where token hashes live. Android uses SharedPreferences; tests use memory. */
interface PairedDeviceStore {
    fun load(): List<PairedDevice>
    fun save(devices: List<PairedDevice>)
}

class InMemoryPairedDeviceStore : PairedDeviceStore {
    private var devices = emptyList<PairedDevice>()
    override fun load() = devices
    override fun save(devices: List<PairedDevice>) { this.devices = devices }
}

sealed interface PairResult {
    data class Paired(val token: String, val deviceName: String) : PairResult
    data object NoCode : PairResult
    data object WrongCode : PairResult
    data object Locked : PairResult
}

/**
 * Six-digit pairing codes shown on the TV, exchanged once for a bearer token.
 * Wrong codes are rate limited: [MAX_FAILURES] misses lock pairing for [LOCK_MS] and cancel the code.
 */
class CompanionPairing(
    private val store: PairedDeviceStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    private var code: String? = null
    private var codeExpiresAt = 0L
    private var failures = 0
    private var lockedUntil = 0L

    /** Creates a fresh code, valid for [CODE_TTL_MS]. Shown on the TV only. */
    @Synchronized
    fun startPairing(): String {
        val c = (random.nextInt(1_000_000)).toString().padStart(6, '0')
        code = c
        codeExpiresAt = clock() + CODE_TTL_MS
        failures = 0
        return c
    }

    @Synchronized
    fun currentCode(): String? = code?.takeIf { clock() < codeExpiresAt }

    @Synchronized
    fun cancelPairing() { code = null }

    @Synchronized
    fun pair(candidate: String, deviceName: String): PairResult {
        val now = clock()
        if (now < lockedUntil) return PairResult.Locked
        val active = code?.takeIf { now < codeExpiresAt } ?: return PairResult.NoCode
        if (!constantTimeEquals(active, candidate.trim())) {
            failures++
            if (failures >= MAX_FAILURES) {
                lockedUntil = now + LOCK_MS
                failures = 0
                code = null
            }
            return PairResult.WrongCode
        }
        code = null
        failures = 0
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        val name = deviceName.trim().take(60).ifBlank { "Companion" }
        store.save(store.load() + PairedDevice(name, sha256(token), now))
        return PairResult.Paired(token, name)
    }

    /** True when [authorizationHeader] is `Bearer <token>` for a paired device. */
    fun isAuthorized(authorizationHeader: String?): Boolean {
        val token = authorizationHeader?.trim()?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring(7)?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val hash = sha256(token)
        return store.load().any { constantTimeEquals(it.tokenHash, hash) }
    }

    fun pairedDevices(): List<PairedDevice> = store.load()

    @Synchronized
    fun unpairAll() = store.save(emptyList())

    companion object {
        const val CODE_TTL_MS = 5 * 60_000L
        const val MAX_FAILURES = 5
        const val LOCK_MS = 60_000L

        fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

        private fun constantTimeEquals(a: String, b: String): Boolean =
            MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
    }
}
