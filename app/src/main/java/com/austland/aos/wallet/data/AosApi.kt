package com.austland.aos.wallet.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Extra fields the server adds on top of a plain receipt for a successful mining claim. */
data class EarnClaimResult(
    val transaction: AosTransaction,
    val claims: Long,
    val nextClaimAt: Long
)

/**
 * Minimal, dependency-free REST client for the AOS gateway (`api/index.py`). Deliberately built
 * on [HttpURLConnection] + the platform's built-in `org.json` instead of Retrofit/OkHttp/Gson —
 * this whole client is a few hundred lines and one extra dependency tree isn't worth it for an
 * app meant to stay lightweight (same philosophy as the "temporary node" background sync).
 *
 * Every call here is synchronous and must be invoked off the main thread (the ViewModels and
 * WorkManager workers that use this class already do that).
 */
class AosApi(private val baseUrlProvider: () -> String) {

    private fun connect(path: String, method: String): HttpURLConnection {
        val url = URL(baseUrlProvider().trimEnd('/') + path)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Accept", "application/json")
        return conn
    }

    private fun readStream(conn: HttpURLConnection, ok: Boolean): String {
        val stream = if (ok) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
    }

    private fun get(path: String): JSONObject {
        val conn = connect(path, "GET")
        try {
            val code = conn.responseCode
            val body = readStream(conn, code in 200..299)
            if (code !in 200..299) throw AosApiException(code, extractError(body, code))
            return JSONObject(body)
        } catch (e: IOException) {
            throw AosApiException(-1, "Couldn't reach the AOS gateway: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    private fun post(path: String, body: JSONObject, adminSecret: String? = null): JSONObject {
        val conn = connect(path, "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        if (adminSecret != null) conn.setRequestProperty("X-Admin-Secret", adminSecret)
        try {
            val payload = body.toString().toByteArray(StandardCharsets.UTF_8)
            conn.setRequestProperty("Content-Length", payload.size.toString())
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(String(payload, StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val respBody = readStream(conn, code in 200..299)
            if (code !in 200..299) throw AosApiException(code, extractError(respBody, code))
            return JSONObject(respBody)
        } catch (e: IOException) {
            throw AosApiException(-1, "Couldn't reach the AOS gateway: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    private fun extractError(body: String, code: Int): String = try {
        JSONObject(body).optString("error", "Gateway returned HTTP $code")
    } catch (e: Exception) {
        "Gateway returned HTTP $code"
    }

    fun fetchConfig(): GatewayConfig {
        val j = get("/")
        return GatewayConfig(
            currencyName = j.optString("currency_name"),
            ticker = j.optString("ticker", "AOS"),
            mode = j.optString("mode"),
            chainId = j.getString("chain_id"),
            maxSupply = j.optString("max_supply"),
            fee = j.optString("fee"),
            powPrefix = j.optString("pow_prefix", "0000"),
            earnReward = j.optString("earn_reward", "1.00000000"),
            earnCooldownSeconds = j.optLong("earn_cooldown_seconds", 60L)
        )
    }

    fun fetchWallet(address: String): WalletInfo {
        val j = get("/wallet/" + URLEncoder.encode(address, "UTF-8"))
        return WalletInfo(
            address = j.optString("address", address),
            balance = j.optString("balance", "0.00000000"),
            version = j.optLong("version", 0L),
            publicKey = j.optString("public_key", null).takeUnless { it.isNullOrEmpty() || it == "null" }
        )
    }

    fun fetchMiner(address: String): MinerInfo {
        val j = get("/miner/" + URLEncoder.encode(address, "UTF-8"))
        return MinerInfo(
            address = j.optString("address", address),
            claims = j.optLong("claims", 0L),
            lastClaimAt = j.optLong("last_claim_at", 0L),
            nextClaimAt = j.optLong("next_claim_at", 0L),
            cooldownRemainingSeconds = j.optLong("cooldown_remaining_seconds", 0L),
            canClaimNow = j.optBoolean("can_claim_now", true),
            rewardAmount = j.optString("reward_amount", "0.00000000"),
            cooldownSeconds = j.optLong("cooldown_seconds", 0L)
        )
    }

    fun fetchSupply(): SupplyInfo {
        val j = get("/supply")
        return SupplyInfo(
            chainId = j.optString("chain_id"),
            totalMinted = j.optString("total_minted"),
            maxSupply = j.optString("max_supply"),
            remaining = j.optString("remaining")
        )
    }

    fun fetchTransactions(limit: Int = 25, addressFilter: String? = null): TransactionsPage {
        val path = buildString {
            append("/transactions?limit=").append(limit)
            if (!addressFilter.isNullOrEmpty()) append("&address=").append(URLEncoder.encode(addressFilter, "UTF-8"))
        }
        val j = get(path)
        val arr: JSONArray = j.optJSONArray("transactions") ?: JSONArray()
        val list = (0 until arr.length()).map { parseTransaction(arr.getJSONObject(it)) }
        return TransactionsPage(transactions = list, count = j.optInt("count", list.size))
    }

    fun fetchTransaction(txHash: String): AosTransaction = parseTransaction(get("/transactions/$txHash"))

    /** POST / — submit a signed, PoW-stamped player transfer. */
    fun submitTransfer(
        chainId: String, sender: String, recipient: String, amount: String,
        requestId: String, powNonce: String, publicKey: String, signature: String
    ): AosTransaction {
        val body = JSONObject()
            .put("chain_id", chainId).put("sender", sender).put("recipient", recipient)
            .put("amount", amount).put("request_id", requestId).put("pow_nonce", powNonce)
            .put("public_key", publicKey).put("signature", signature)
        return parseTransaction(post("/", body))
    }

    /** POST /earn/claim — submit a self-custodial PoW mining reward claim. */
    fun submitEarnClaim(
        chainId: String, recipient: String, amount: String,
        requestId: String, powNonce: String, publicKey: String, signature: String
    ): EarnClaimResult {
        val body = JSONObject()
            .put("chain_id", chainId).put("recipient", recipient).put("amount", amount)
            .put("request_id", requestId).put("pow_nonce", powNonce)
            .put("public_key", publicKey).put("signature", signature)
        val j = post("/earn/claim", body)
        return EarnClaimResult(
            transaction = parseTransaction(j),
            claims = j.optLong("claims", 0L),
            nextClaimAt = j.optLong("next_claim_at", 0L)
        )
    }

    private fun parseTransaction(j: JSONObject): AosTransaction = AosTransaction(
        status = j.optString("status", "confirmed"),
        duplicate = j.optBoolean("duplicate", false),
        txHash = j.optString("tx_hash"),
        txType = j.optString("tx_type"),
        sender = j.optString("sender"),
        recipient = j.optString("recipient"),
        amount = j.optString("amount"),
        fee = j.optString("fee", "0.00000000"),
        requestId = j.optString("request_id"),
        powNonce = j.optString("pow_nonce"),
        publicKey = j.optString("public_key"),
        signature = j.optString("signature"),
        createdAt = j.optLong("created_at", 0L)
    )
}
