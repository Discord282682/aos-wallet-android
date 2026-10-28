package com.austland.aos.wallet.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Central place that (re)schedules the two background jobs this app ever runs. */
object WorkScheduler {
    private const val MINING_WORK_NAME = "aos_mining_claim"
    private const val NODE_SYNC_WORK_NAME = "aos_node_sync"

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Queues the one-shot claim job to fire in [delaySeconds] (the server's reported cooldown,
     * or a re-check interval while a claim is temporarily blocked). Replaces any job already
     * queued for this wallet, so repeated "Start Mining" taps don't stack up duplicate claims.
     */
    fun scheduleMiningClaim(context: Context, delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<MiningWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(MINING_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelMining(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(MINING_WORK_NAME)
    }

    /** Starts (or stops) the periodic lightweight background ledger sync behind the "node" toggle. */
    fun setNodeSyncEnabled(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(NODE_SYNC_WORK_NAME)
            return
        }
        // 15 minutes is WorkManager's floor for periodic work; frequent enough to keep the
        // explorer/wallet screens warm without meaningfully touching battery.
        val request = PeriodicWorkRequestBuilder<NodeSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
            .build()
        manager.enqueueUniquePeriodicWork(NODE_SYNC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
