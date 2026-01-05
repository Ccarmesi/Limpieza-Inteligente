package com.empresa.autocleaner

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkerScheduler {

    fun schedule(context: Context) {
        val daysToKeep = SettingsManager.getDaysToKeep(context)
        val frequencyHours = SettingsManager.getExecutionFrequency(context)
        val inputData = Data.Builder()
            .putLong(CleanWorker.KEY_DAYS_OLD, daysToKeep.toLong())
            .build()

        val workRequest =
            PeriodicWorkRequest.Builder(CleanWorker::class.java, frequencyHours.toLong(), TimeUnit.HOURS)
                .setInputData(inputData)
                .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            "AutoClean",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun runNow(context: Context) {
        val daysToKeep = SettingsManager.getDaysToKeep(context)
        val inputData = Data.Builder()
            .putLong(CleanWorker.KEY_DAYS_OLD, daysToKeep.toLong())
            .build()

        val workRequest = OneTimeWorkRequest.Builder(CleanWorker::class.java)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
    }
}