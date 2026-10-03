package com.mamblxon.autosense.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mamblxon.autosense.prefs.AppPrefs
import java.util.concurrent.TimeUnit

object WorkScheduler {

    fun schedulePeriodic(context: Context) {
        val prefs = AppPrefs(context)
        val hours = prefs.checkPeriodHours

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<ServiceCheckWorker>(
            hours.toLong(), TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ServiceCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runOnceNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<ServiceCheckWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ServiceCheckWorker.WORK_NAME + "_once",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}