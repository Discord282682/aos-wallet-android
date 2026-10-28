package com.austland.aos.wallet.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.austland.aos.wallet.BuildConfig
import com.austland.aos.wallet.crypto.AosKeyPair
import com.austland.aos.wallet.crypto.AosKeys

/**
 * Holds the one thing in this app that actually matters to protect: the wallet's private key.
 *
 * It's stored in [EncryptedSharedPreferences], which AES-256-GCM-encrypts both keys and values
 * on disk using a key held in the Android Keystore (hardware-backed where the device supports
 * it) — the private key material itself is never written in plaintext. The private key never
 * leaves the device except inside a signature the user explicitly asks to broadcast.
 *
 * This is good-practice local storage, not an audited hardware wallet: anyone with root / a
 * forensic image of an unlocked, compromised device could still extract it. Treat AOS, by the
 * server's own README, as a non-monetary prototype currency, not a place to hold real value.
 */
class WalletStore(context: Context) {

    private val appContext = context.applicationContext

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            "wallet_secure_prefs_v2",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Non-secret app settings (gateway URL, node toggle) live in a plain prefs file — nothing
    // sensitive, no reason to pay the encryption overhead on every read.
    private val settings: SharedPreferences by lazy {
        appContext.getSharedPreferences("wallet_settings", Context.MODE_PRIVATE)
    }

    fun hasWallet(): Boolean = prefs.contains(KEY_PRIVATE_KEY)

    fun currentKeyPair(): AosKeyPair? {
        val priv = prefs.getString(KEY_PRIVATE_KEY, null) ?: return null
        return AosKeys.fromPrivateKeySeed(priv)
    }

    /** Generates a brand-new wallet, persists it, and returns the keypair. Overwrites any existing wallet. */
    fun createNewWallet(): AosKeyPair {
        val kp = AosKeys.generate()
        saveKeyPair(kp)
        return kp
    }

    /** Imports a wallet from a raw 64-hex-char private key. Overwrites any existing wallet. */
    fun importWallet(privateKeyHex: String): AosKeyPair {
        val kp = AosKeys.fromPrivateKeySeed(privateKeyHex)
        saveKeyPair(kp)
        return kp
    }

    private fun saveKeyPair(kp: AosKeyPair) {
        prefs.edit()
            .putString(KEY_PRIVATE_KEY, kp.privateKeyHex)
            .putString(KEY_PUBLIC_KEY, kp.publicKeyHex)
            .putString(KEY_ADDRESS, kp.address)
            .apply()
    }

    /** Irreversibly removes the wallet from this device. There is no recovery without a backed-up private key. */
    fun deleteWallet() {
        prefs.edit().clear().apply()
    }

    // --- Settings -----------------------------------------------------------------------------

    var gatewayUrl: String
        get() = settings.getString(KEY_GATEWAY_URL, BuildConfig.DEFAULT_GATEWAY_URL) ?: BuildConfig.DEFAULT_GATEWAY_URL
        set(value) {
            settings.edit().putString(KEY_GATEWAY_URL, value.trimEnd('/')).apply()
        }

    var nodeModeEnabled: Boolean
        get() = settings.getBoolean(KEY_NODE_MODE, false)
        set(value) {
            settings.edit().putBoolean(KEY_NODE_MODE, value).apply()
        }

    /** Set while a mining session (start-tap -> eventual claim) is outstanding; cleared on claim/cancel. */
    var miningSessionActive: Boolean
        get() = settings.getBoolean(KEY_MINING_ACTIVE, false)
        set(value) {
            settings.edit().putBoolean(KEY_MINING_ACTIVE, value).apply()
        }

    var lastMiningError: String?
        get() = settings.getString(KEY_MINING_ERROR, null)
        set(value) {
            settings.edit().putString(KEY_MINING_ERROR, value).apply()
        }

    var nodeSyncCount: Long
        get() = settings.getLong(KEY_NODE_SYNC_COUNT, 0L)
        set(value) {
            settings.edit().putLong(KEY_NODE_SYNC_COUNT, value).apply()
        }

    var nodeLastSyncAt: Long
        get() = settings.getLong(KEY_NODE_LAST_SYNC, 0L)
        set(value) {
            settings.edit().putLong(KEY_NODE_LAST_SYNC, value).apply()
        }

    companion object {
        private const val KEY_PRIVATE_KEY = "private_key_hex"
        private const val KEY_PUBLIC_KEY = "public_key_hex"
        private const val KEY_ADDRESS = "address"

        private const val KEY_GATEWAY_URL = "gateway_url"
        private const val KEY_NODE_MODE = "node_mode_enabled"
        private const val KEY_MINING_ACTIVE = "mining_session_active"
        private const val KEY_MINING_ERROR = "last_mining_error"
        private const val KEY_NODE_SYNC_COUNT = "node_sync_count"
        private const val KEY_NODE_LAST_SYNC = "node_last_sync_at"
    }
}
