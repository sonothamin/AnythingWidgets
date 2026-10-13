package com.sonothamin.anythingwidgets.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sonothamin.anythingwidgets.widget.data.Registry
import com.sonothamin.anythingwidgets.widget.data.SpecStore
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Hourly: pull spec updates for installed widgets, then re-render every instance.
 * Runs only while at least one widget exists (scheduled in onEnabled, cancelled in onDisabled).
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            Registry(applicationContext).updateInstalled(SpecStore(applicationContext))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline or registry hiccup: still refresh local data below.
        }
        // Feeds: only widgets the user allowed online access for, and only the ones that are due.
        val store = SpecStore(applicationContext)
        store.installed().filter { it.feeds.isNotEmpty() }.forEach { spec ->
            try {
                WidgetRuntime.refreshFeeds(applicationContext, spec)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        SpecWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "spec_widget_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
