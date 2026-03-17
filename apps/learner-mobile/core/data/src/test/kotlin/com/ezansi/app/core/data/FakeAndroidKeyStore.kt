@file:Suppress("TooManyFunctions")

package com.ezansi.app.core.data

import android.security.keystore.KeyGenParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Fake "AndroidKeyStore" security provider for unit tests.
 *
 * Android's real KeyStore provider is hardware-backed and unavailable in
 * JVM unit tests (even under Robolectric). This utility registers an
 * in-memory provider that satisfies [ProfileEncryption]'s requirements:
 *
 * - `KeyStore.getInstance("AndroidKeyStore")` → [FakeAndroidKeyStoreSpi]
 * - `KeyGenerator.getInstance("AES", "AndroidKeyStore")` → [FakeAESKeyGeneratorSpi]
 * - Generated keys are real AES-256 keys using standard JVM crypto
 * - Keys are auto-stored in the fake KeyStore (mimics Android behaviour)
 *
 * ## Usage
 * ```kotlin
 * @BeforeEach
 * fun setUp() {
 *     installFakeAndroidKeyStore()
 *     FakeAndroidKeyStoreSpi.reset()
 * }
 * ```
 *
 * ## Requirements
 * - Must run under **Robolectric** so [KeyGenParameterSpec] is available
 *   (it's an Android framework class shadowed by Robolectric)
 *
 * @see com.ezansi.app.core.data.encryption.ProfileEncryption
 */

// ── Public API ──────────────────────────────────────────────────────

/**
 * Registers the fake AndroidKeyStore provider if not already present.
 * Safe to call multiple times.
 */
fun installFakeAndroidKeyStore() {
    if (Security.getProvider("AndroidKeyStore") == null) {
        Security.addProvider(FakeAndroidKeyStoreProvider())
    }
}

// ── Provider ────────────────────────────────────────────────────────

/**
 * Fake security provider that registers itself as "AndroidKeyStore"
 * and supplies both a KeyStore and a KeyGenerator service.
 */
class FakeAndroidKeyStoreProvider : Provider(
    "AndroidKeyStore",
    1.0,
    "Fake AndroidKeyStore for eZansi unit tests",
) {
    init {
        put("KeyStore.AndroidKeyStore", FakeAndroidKeyStoreSpi::class.java.name)
        put("KeyGenerator.AES", FakeAESKeyGeneratorSpi::class.java.name)
    }
}

// ── KeyStore SPI ────────────────────────────────────────────────────

/**
 * In-memory KeyStore implementation backed by a [HashMap].
 *
 * Entries persist across calls within a test but are isolated between
 * tests when [reset] is called in `@BeforeEach`.
 */
class FakeAndroidKeyStoreSpi : KeyStoreSpi() {

    companion object {
        /** Shared in-memory key storage. */
        internal val entries = mutableMapOf<String, KeyStore.Entry>()

        /** Clears all stored keys — call between tests. */
        fun reset() = entries.clear()
    }

    // ── Read operations ─────────────────────────────────────────

    override fun engineGetEntry(
        alias: String?,
        protParam: KeyStore.ProtectionParameter?,
    ): KeyStore.Entry? = entries[alias]

    override fun engineGetKey(alias: String?, password: CharArray?): Key? =
        (entries[alias] as? KeyStore.SecretKeyEntry)?.secretKey

    override fun engineContainsAlias(alias: String?): Boolean =
        entries.containsKey(alias)

    override fun engineSize(): Int = entries.size

    override fun engineAliases(): Enumeration<String> =
        Collections.enumeration(entries.keys)

    override fun engineIsKeyEntry(alias: String?): Boolean =
        entries[alias] is KeyStore.SecretKeyEntry

    override fun engineGetCreationDate(alias: String?): Date = Date()

    // ── Write operations ────────────────────────────────────────

    override fun engineSetEntry(
        alias: String?,
        entry: KeyStore.Entry?,
        protParam: KeyStore.ProtectionParameter?,
    ) {
        if (alias != null && entry != null) entries[alias] = entry
    }

    override fun engineSetKeyEntry(
        alias: String?,
        key: Key?,
        password: CharArray?,
        chain: Array<out Certificate>?,
    ) {
        if (key is SecretKey && alias != null) {
            entries[alias] = KeyStore.SecretKeyEntry(key)
        }
    }

    override fun engineSetKeyEntry(
        alias: String?,
        key: ByteArray?,
        chain: Array<out Certificate>?,
    ) {
        // Not used by ProfileEncryption — no-op
    }

    override fun engineDeleteEntry(alias: String?) {
        entries.remove(alias)
    }

    // ── Unused certificate operations ───────────────────────────

    override fun engineIsCertificateEntry(alias: String?): Boolean = false
    override fun engineGetCertificate(alias: String?): Certificate? = null
    override fun engineGetCertificateChain(alias: String?): Array<Certificate>? = null
    override fun engineGetCertificateAlias(cert: Certificate?): String? = null
    override fun engineSetCertificateEntry(alias: String?, cert: Certificate?) {}

    // ── Lifecycle ───────────────────────────────────────────────

    override fun engineStore(stream: OutputStream?, password: CharArray?) {
        // In-memory — nothing to persist
    }

    override fun engineLoad(stream: InputStream?, password: CharArray?) {
        // No-op: KeyStore.load(null) is called by ProfileEncryption init
    }
}

// ── KeyGenerator SPI ────────────────────────────────────────────────

/**
 * Fake AES KeyGenerator that creates real AES-256 keys and auto-stores
 * them in [FakeAndroidKeyStoreSpi] — mimicking Android's behaviour where
 * `KeyGenerator.generateKey()` for the AndroidKeyStore provider
 * automatically persists the key under the alias from [KeyGenParameterSpec].
 */
class FakeAESKeyGeneratorSpi : KeyGeneratorSpi() {

    private var alias: String? = null
    private var keySize: Int = 256

    override fun engineInit(random: SecureRandom?) {
        // Default init — use 256-bit AES
    }

    override fun engineInit(keysize: Int, random: SecureRandom?) {
        this.keySize = keysize
    }

    override fun engineInit(params: AlgorithmParameterSpec?, random: SecureRandom?) {
        if (params is KeyGenParameterSpec) {
            alias = params.keystoreAlias
            // KeyGenParameterSpec.getKeySize() may return 0 if not set;
            // default to 256 for AES-256-GCM
            val specSize = params.keySize
            keySize = if (specSize > 0) specSize else 256
        }
    }

    override fun engineGenerateKey(): SecretKey {
        val keyBytes = ByteArray(keySize / 8)
        SecureRandom().nextBytes(keyBytes)
        val key = SecretKeySpec(keyBytes, "AES")

        // Auto-store in the fake keystore under the alias from KeyGenParameterSpec
        alias?.let { a ->
            FakeAndroidKeyStoreSpi.entries[a] = KeyStore.SecretKeyEntry(key)
        }

        return key
    }
}
