package com.austland.aos.wallet.work

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.austland.aos.wallet.AosApplication
import com.austland.aos.wallet.R
import com.austland.aos.wallet.data.AosApi
import com.austland.aos.wallet.data.AosApiException
import com.austland.aos.wallet.data.TransactionBuilder
import com.austland.aos.wallet.data.WalletStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MiningWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = WalletStore(applicationContext)
        val keyPair = store.currentKeyPair() ?: run {
            store.miningSessionActive = false
            return@withContext Result.failure()
        }
        if (!store.miningSessionActive) {
            return@withContext Result.success()
        }

        val api = AosApi { store.gatewayUrl }
        try {
            val config = api.fetchConfig()
            val miner = api.fetchMiner(keyPair.address)

            if (!miner.canClaimNow) {
                WorkScheduler.scheduleMiningClaim(applicationContext, miner.cooldownRemainingSeconds.coerceAtLeast(1))
                return@withContext Result.success()
            }

            val claim = TransactionBuilder.buildEarnClaim(
                keyPair = keyPair,
                chainId = config.chainId,
                rewardAmount8dp = config.earnReward,
                powPrefix = config.powPrefix,
                shouldCancel = { isStopped }
            )
            val response = api.submitEarnClaim(
                chainId = claim.chainId, recipient = claim.recipient, amount = claim.amount,
                requestId = claim.requestId, powNonce = claim.powNonce,
                publicKey = claim.publicKey, signature = claim.signature
            )

            store.miningSessionActive = false
            store.lastMiningError = null
            notifyClaimed(response.transaction.amount)
            Result.success()
        } catch (e: AosApiException) {
            if (e.status == 429) {
                val wait = Regex("Wait (\\d+)s").find(e.message ?: "")?.groupValues?.get(1)?.toLongOrNull() ?: 60L
                WorkScheduler.scheduleMiningClaim(applicationContext, wait)
                return@withContext Result.success()
            }
            Log.w("MiningWorker", "Claim failed: ${e.message}")
            store.lastMiningError = e.message
            if (e.status in 400..499) {
                store.miningSessionActive = false
                Result.failure()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.w("MiningWorker", "Claim attempt errored, will retry", e)
            store.lastMiningError = e.message
            Result.retry()
        }
    }

    private fun notifyClaimed(amount: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(applicationContext, AosApplication.CHANNEL_MINING)
            .setSmallIcon(R.drawable.ic_stat_aos)
            .setContentTitle("Estbrugh Treasury Reward Mined")
            .setContentText("+$amount AOS added to your Au5 wallet.")
            .setAutoCancel(true)
            .build()
        manager.notify(1001, notification)
    }
}
