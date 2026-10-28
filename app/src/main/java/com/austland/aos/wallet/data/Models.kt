package com.austland.aos.wallet.data

/** GET / — public protocol/monetary configuration. Fetched at startup and cached. */
data class GatewayConfig(
    val currencyName: String,
    val ticker: String,
    val mode: String,
    val chainId: String,
    val maxSupply: String,
    val fee: String,
    val powPrefix: String,
    val earnReward: String,
    val earnCooldownSeconds: Long
)

/** GET /wallet/{address} */
data class WalletInfo(
    val address: String,
    val balance: String,
    val version: Long,
    val publicKey: String?
)

/** GET /miner/{address} */
data class MinerInfo(
    val address: String,
    val claims: Long,
    val lastClaimAt: Long,
    val nextClaimAt: Long,
    val cooldownRemainingSeconds: Long,
    val canClaimNow: Boolean,
    val rewardAmount: String,
    val cooldownSeconds: Long
)

/** GET /supply */
data class SupplyInfo(
    val chainId: String,
    val totalMinted: String,
    val maxSupply: String,
    val remaining: String
)

/** A confirmed ledger entry, returned by POST / , POST /earn/claim, GET /transactions[...] */
data class AosTransaction(
    val status: String,
    val duplicate: Boolean,
    val txHash: String,
    val txType: String, // "TRANSFER" or "MINT"
    val sender: String,
    val recipient: String,
    val amount: String,
    val fee: String,
    val requestId: String,
    val powNonce: String,
    val publicKey: String,
    val signature: String,
    val createdAt: Long
)

data class TransactionsPage(val transactions: List<AosTransaction>, val count: Int)

/** Thrown for any non-2xx gateway response or network failure. [status] is -1 for pure I/O errors. */
class AosApiException(val status: Int, message: String) : Exception(message)
