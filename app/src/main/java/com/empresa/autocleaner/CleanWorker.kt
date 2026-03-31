package com.empresa.autocleaner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class CleanWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val KEY_DAYS_OLD = "KEY_DAYS_OLD"
        private const val CHANNEL_ID = "AutoCleanerChannel"
    }

    override fun doWork(): Result {
        // 1. Verificación de seguridad: ¿Tenemos permiso real para borrar?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            showNotification("Error", "Falta de permiso de Acceso a Todos los Archivos")
            return Result.failure()
        }

        val daysOld = inputData.getLong(KEY_DAYS_OLD, 30L)
        val cutoffMillis = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000)
        val cutoffSeconds = cutoffMillis / 1000 // MediaStore usa segundos

        return try {
            //2.Ejecución Masiva (Bulk Delete)
            //En lugar de un bucle, lanzamos una sola petición SQL al sistema operativo.
            val deletedImages = deleteBulk(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cutoffSeconds)
            val deletedVideos = deleteBulk(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cutoffSeconds)

            val total = deletedImages + deletedVideos

            showNotification(
                "Limpieza completada",
                if (total > 0) "Se eliminaron $total archivos antiguos." else "No se encontraron archivos para borrar."
            )

            Log.i("CleanWorker", "Limpieza masiva terminada. Total: $total")
            Result.success()
        } catch (e: Exception) {
            Log.e("CleanWorker", "Fallo en limpieza masiva", e)
            Result.failure()
        }
    }
    private fun deleteBulk(contentUri: Uri, cutoffSeconds: Long): Int {
        val selection = "${MediaStore.MediaColumns.DATE_MODIFIED} < ?"
        val selectionArgs = arrayOf(cutoffSeconds.toString())

        //Esta es la instrucción más rápida de Android:
        //El ContentResolver borra físicamente
        return applicationContext.contentResolver.delete(contentUri, selection, selectionArgs)
    }

    private fun showNotification(title: String, message: String){
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Limpieza", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(1, notification)
    }
}
