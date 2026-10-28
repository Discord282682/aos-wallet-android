package com.austland.aos.wallet.data

import com.austland.aos.wallet.crypto.AosKeyPair
import com.austland.aos.wallet.crypto.MINT_ADDRESS
import com.austland.aos.wallet.crypto.ProofOfWork
import com.austland.aos.wallet.crypto.formatAmount8dp
import java.math.BigDecimal
import java.util.UUID

/** Builds and signs the two kinds of outbound payload this wallet can broadcast. */
object TransactionBuilder {

    class PowCancelled : Exception("Proof-of-work cancelled.")

    /**
     * Builds a signed `POST /` transfer payload. Runs client-side PoW (the "amount_8dp" formatting,
     * digest and signature all mirror `aos.protocol.validate_transfer` exactly).
     */
    fun buildTransfer(
        keyPair: AosKeyPair,
        chainId: String,
        recipient: String,
        amount: BigDecimal,
        powPrefix: String,
        shouldCancel: () -> Boolean = { false }
    ): SignedTransfer {
        val amount8dp = formatAmount8dp(amount)
        val requestId = UUID.randomUUID().toString()
        val result = ProofOfWork.solve(
            chainId = chainId, kind = "TRANSFER", sender = keyPair.address, recipient = recipient,
            amount8dp = amount8dp, requestId = requestId, prefix = powPrefix, shouldCancel = shouldCancel
        ) ?: throw PowCancelled()
        val signature = keyPair.sign(result.txHash)
        return SignedTransfer(
            chainId = chainId, sender = keyPair.address, recipient = recipient, amount = amount8dp,
            requestId = requestId, powNonce = result.nonce, publicKey = keyPair.publicKeyHex,
            signature = signature, attempts = result.attempts
        )
    }

    /**
     * Builds a signed `POST /earn/claim` payload. The miner's own keypair signs it — the reward
     * amount must equal the server's configured `earn_reward` exactly, or the gateway rejects it.
     */
    fun buildEarnClaim(
        keyPair: AosKeyPair,
        chainId: String,
        rewardAmount8dp: String,
        powPrefix: String,
        shouldCancel: () -> Boolean = { false }
    ): SignedEarnClaim {
        val requestId = UUID.randomUUID().toString()
        val result = ProofOfWork.solve(
            chainId = chainId, kind = "MINT", sender = MINT_ADDRESS, recipient = keyPair.address,
            amount8dp = rewardAmount8dp, requestId = requestId, prefix = powPrefix, shouldCancel = shouldCancel
        ) ?: throw PowCancelled()
        val signature = keyPair.sign(result.txHash)
        return SignedEarnClaim(
            chainId = chainId, recipient = keyPair.address, amount = rewardAmount8dp,
            requestId = requestId, powNonce = result.nonce, publicKey = keyPair.publicKeyHex,
            signature = signature, attempts = result.attempts
        )
    }
}

data class SignedTransfer(
    val chainId: String, val sender: String, val recipient: String, val amount: String,
    val requestId: String, val powNonce: String, val publicKey: String, val signature: String,
    val attempts: Long
)

data class SignedEarnClaim(
    val chainId: String, val recipient: String, val amount: String,
    val requestId: String, val powNonce: String, val publicKey: String, val signature: String,
    val attempts: Long
)
