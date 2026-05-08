package com.empresa.autocleaner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread
import java.io.File

class FastCleanService : Service() {

    companion object {
        const val EXTRA_DAYS_OLD = "EXTRA_DAYS_OLD"
        const val EXTRA_TARGET_BYTES = "EXTRA_TARGET_BYTES"

        private const val CHANNEL_ID = "FastCleanServiceChannel"
        private const val NOTIFICATION_ID = 2001
        private const val DEFAULT_TARGET_BYTES = 200L * 1024L * 1024L * 1024L // 200 GB
    }

    private data class ScanResult(
        var deleted: Int = 0,
        var found: Int = 0,
        var scanned: Int = 0,
        var bytesDeleted: Long = 0L
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Preparando limpieza rápida...")
        startForeground(NOTIFICATION_ID, notification)

        thread {
            runFastCleanup(intent)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun runFastCleanup(intent: Intent?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            updateNotification("Falta permiso: activa 'Acceso a todos los archivos'.")
            return
        }

        val daysOld = intent?.getLongExtra(EXTRA_DAYS_OLD, 30L) ?: 30L
        val targetBytes = intent?.getLongExtra(EXTRA_TARGET_BYTES, DEFAULT_TARGET_BYTES) ?: DEFAULT_TARGET_BYTES
        val cutoffMillis = System.currentTimeMillis() - (daysOld * 24L * 60L * 60L * 1000L)

        try {
            updateNotification("Borrando videos grandes...")
            val videosResult = deleteOldMedia(
                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                cutoffMillis = cutoffMillis,
                targetBytes = targetBytes,
                bytesDeletedSoFar = 0L
            )

            val imagesResult =
                if (videosResult.bytesDeleted < targetBytes) {
                    updateNotification("Borrando imágenes grandes...")
                    deleteOldMedia(
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cutoffMillis = cutoffMillis,
                        targetBytes = targetBytes,
                        bytesDeletedSoFar = videosResult.bytesDeleted
                    )
                } else {
                    ScanResult()
                }

            val totalDeleted = videosResult.deleted + imagesResult.deleted
            val totalBytesDeleted = videosResult.bytesDeleted + imagesResult.bytesDeleted
            val deletedGb = totalBytesDeleted / (1024L * 1024L * 1024L)

            updateNotification("Limpieza terminada: $totalDeleted archivos, aprox. $deletedGb GB liberados.")
        } catch (e: Exception) {
            Log.e("FastCleanService", "Error en limpieza rápida", e)
            updateNotification("Error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun deleteOldMedia(
        contentUri: Uri,
        cutoffMillis: Long,
        targetBytes: Long,
        bytesDeletedSoFar: Long
    ): ScanResult {
        val resolver = applicationContext.contentResolver
        val result = ScanResult()

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            "datetaken"
        )

        val selection = "${MediaStore.MediaColumns.DATE_MODIFIED} < ?"
        val selectionArgs = arrayOf((cutoffMillis / 1000L).toString())

        resolver.query(
            contentUri,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.SIZE} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val dateModifiedColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val dateTakenColumn = cursor.getColumnIndex("datetaken")
            val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)

            while (cursor.moveToNext()) {
                if (bytesDeletedSoFar + result.bytesDeleted >= targetBytes) {
                    break
                }

                result.scanned++

                val id = cursor.getLong(idColumn)
                val path = if (dataColumn != -1) cursor.getString(dataColumn) else null
                val dateModifiedSecs = if (dateModifiedColumn != -1) cursor.getLong(dateModifiedColumn) else 0L
                val dateTakenMillis = if (dateTakenColumn != -1 && !cursor.isNull(dateTakenColumn)) {
                    cursor.getLong(dateTakenColumn)
                } else {
                    null
                }
                val fileDateMillis = dateTakenMillis ?: (dateModifiedSecs * 1000L)
                val sizeBytes = if (sizeColumn != -1 && !cursor.isNull(sizeColumn)) {
                    cursor.getLong(sizeColumn)
                } else {
                    0L
                }

                if (fileDateMillis < cutoffMillis) {
                    result.found++

                    if (deleteFile(path, contentUri, id, resolver)) {
                        result.deleted++
                        result.bytesDeleted += sizeBytes
                    }
                }
            }
        }

        return result
    }

    private fun deleteFile(
        path: String?,
        contentUri: Uri,
        id: Long,
        resolver: ContentResolver
    ): Boolean {
        return try {
            val itemUri = ContentUris.withAppendedId(contentUri, id)

            val rowsDeleted = resolver.delete(itemUri, null, null)
            if (rowsDeleted > 0) {
                Log.d("FastCleanService", "Archivo eliminado: $itemUri")
                return true
            }

            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists() && file.delete()) {
                    try {
                        resolver.delete(itemUri, null, null)
                    } catch (_: Exception) {
                    }
                    return true
                }
            }

            false
        } catch (e: Exception) {
            Log.e("FastCleanService", "No se pudo eliminar ID: $id", e)
            false
        }
    }

    private fun buildNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle("Limpieza rápida en progreso")
            .setContentText(message)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Limpieza rápida",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }
}