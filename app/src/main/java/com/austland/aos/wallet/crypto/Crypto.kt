package com.austland.aos.wallet.crypto

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Everything here is written to match, byte for byte, the AOS v2 wire protocol implemented in
 * the server's `aos/protocol.py` (see `digest()`, `public_address()`, `validate_transfer()` and
 * `validate_earn_claim()`). Nothing in this file talks to the network — see [data.AosApi] /
 * [data.TransactionBuilder] for that.
 *
 * Wallet keys are the *raw* 32-byte Ed25519 seed, hex-encoded (64 lowercase hex chars) — the
 * exact same format the reference `test_tx.py` client writes to its wallet JSON files. A key
 * exported from this app can be imported there, and vice versa.
 */
object HexUtils {
    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    fun toHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX_CHARS[v ushr 4]
            out[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(out)
    }

    fun fromHex(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Hex string must have an even length." }
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "Invalid hex string." }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    fun isLowercaseHex(value: String, length: Int): Boolean =
        value.length == length && value.all { it in '0'..'9' || it in 'a'..'f' }
}

/** A loaded (or freshly generated) AOS Ed25519 keypair plus its derived Au5 address. */
data class AosKeyPair(
    val privateKeyHex: String, // 64 lowercase hex chars = raw 32-byte seed
    val publicKeyHex: String,  // 64 lowercase hex chars = raw 32-byte public key
    val address: String        // "Au5" + 40 lowercase hex chars
) {
    val signer: Ed25519Signer
        get() = Ed25519Signer().apply {
            init(true, Ed25519PrivateKeyParameters(HexUtils.fromHex(privateKeyHex), 0))
        }

    /** Signs the ASCII bytes of a tx_hash hex string, exactly as the server/reference client do. */
    fun sign(txHashHex: String): String {
        val s = signer
        val msg = txHashHex.toByteArray(Charsets.US_ASCII)
        s.update(msg, 0, msg.size)
        return HexUtils.toHex(s.generateSignature())
    }
}

object AosKeys {
    private const val ADDRESS_PREFIX = "Au5"

    /** Generates a brand-new wallet keypair using a cryptographically secure random seed. */
    fun generate(): AosKeyPair {
        val seed = ByteArray(32)
        SecureRandom().nextBytes(seed)
        return fromPrivateKeySeed(HexUtils.toHex(seed))
    }

    /** Rebuilds a keypair (and re-derives the public key + address) from a 64-hex-char private seed. */
    fun fromPrivateKeySeed(privateKeyHex: String): AosKeyPair {
        val clean = privateKeyHex.trim().lowercase()
        require(HexUtils.isLowercaseHex(clean, 64)) {
            "A private key must be exactly 64 lowercase hex characters (32 bytes)."
        }
        val priv = Ed25519PrivateKeyParameters(HexUtils.fromHex(clean), 0)
        val pub: Ed25519PublicKeyParameters = priv.generatePublicKey()
        val pubHex = HexUtils.toHex(pub.encoded)
        return AosKeyPair(privateKeyHex = clean, publicKeyHex = pubHex, address = addressFromPublicKey(pubHex))
    }

    /** address = "Au5" + first 40 hex chars of SHA-256(raw 32-byte public key) — matches public_address(). */
    fun addressFromPublicKey(publicKeyHex: String): String {
        require(HexUtils.isLowercaseHex(publicKeyHex, 64)) { "public_key must be 64 lowercase hex characters." }
        val digest = MessageDigest.getInstance("SHA-256").digest(HexUtils.fromHex(publicKeyHex))
        return ADDRESS_PREFIX + HexUtils.toHex(digest).substring(0, 40)
    }

    private val ADDRESS_REGEX = Regex("^Au5[0-9a-f]{40}$")
    fun isValidAddress(value: String): Boolean = ADDRESS_REGEX.matches(value)
}

/** The treasury marker address the server mints MINT-type transactions from. */
val MINT_ADDRESS = "Au5" + "0".repeat(40)

/**
 * Formats a decimal amount with exactly 8 fractional digits, HALF_UP, using '.' and no grouping —
 * matching Python's `f'{value:.8f}'` used throughout the server and reference client.
 */
fun formatAmount8dp(value: BigDecimal): String =
    value.setScale(8, RoundingMode.HALF_UP).toPlainString()

/**
 * Computes the canonical AOS2 transaction digest:
 *   AOS2:{chain_id}:{kind}:{sender}:{recipient}:{amount_8dp}:{request_id}:{nonce}
 * sha256'd and hex-encoded — identical to `aos.protocol.digest()` on the server.
 */
fun aosDigest(
    chainId: String,
    kind: String,
    sender: String,
    recipient: String,
    amount8dp: String,
    requestId: String,
    nonce: String
): String {
    val raw = "AOS2:$chainId:$kind:$sender:$recipient:$amount8dp:$requestId:$nonce"
    val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.US_ASCII))
    return HexUtils.toHex(bytes)
}

/**
 * Client-side proof-of-work: find the smallest non-negative integer nonce (as a decimal string,
 * 1-20 digits, matching the server's `pow_nonce` validation) such that the resulting tx_hash
 * starts with [prefix] (server default: "0000", ~65,536 average SHA-256 attempts). This is the
 * same cost function Bitcoin mining uses, just sized for a phone instead of an ASIC farm.
 *
 * [shouldCancel] is polled between attempts so a running mine/claim can be stopped (e.g. the user
 * turned mining off, or a WorkManager job is being torn down).
 */
object ProofOfWork {
    data class Result(val nonce: String, val txHash: String, val attempts: Long)

    fun solve(
        chainId: String,
        kind: String,
        sender: String,
        recipient: String,
        amount8dp: String,
        requestId: String,
        prefix: String,
        shouldCancel: () -> Boolean = { false }
    ): Result? {
        var nonce = 0L
        var attempts = 0L
        while (!shouldCancel()) {
            val hash = aosDigest(chainId, kind, sender, recipient, amount8dp, requestId, nonce.toString())
            attempts++
            if (hash.startsWith(prefix)) {
                return Result(nonce.toString(), hash, attempts)
            }
            nonce++
            // 20-decimal-digit ceiling enforced by the server; this is far beyond reachable in practice.
            if (nonce.toString().length > 20) return null
        }
        return null
    }
}
