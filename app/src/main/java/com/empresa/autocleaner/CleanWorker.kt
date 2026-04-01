package com.empresa.autocleaner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import android.bluetooth.le.ScanResult
import java.io.File

class CleanWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val KEY_DAYS_OLD = "KEY_DAYS_OLD"
        const val KEY_TARGET_BYTES = "KEY_TARGET_BYTES"
        private const val CHANNEL_ID = "AutoCleanerChannel"
        private const val DEFAULT_TARGET_BYTES = 200L * 1024L * 1024L // 200 GB
    }

    override fun doWork(): Result {
        // 1. Verificación de seguridad: ¿Tenemos permiso real para borrar?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            showNotification(
                "Falta permiso crítico",
                "No se pueden borrar archivos. Ve a Ajustes y concede 'Acceso a todos los archivos'."
            )
            return Result.failure()
        }

        val daysOld = inputData.getLong(KEY_DAYS_OLD, 30L)
        val targetBytes = inputData.getLong(KEY_TARGET_BYTES, DEFAULT_TARGET_BYTES)
        val cutoffMillis = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000)

        return try {
            val imagesResult = deleteOldMedia(
                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                cutoffMillis = cutoffMillis,
                targetBytes = targetBytes,
                bytesDeletedSoFar = 0L
            )

            val videosResult =
                if (imagesResult.bytesDeleted < targetBytes) {
                    deleteOldMedia(
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        cutoffMillis = cutoffMillis,
                        targetBytes = targetBytes,
                        BytesDeletedSoFar = imagesResult.bytesDeleted
                    )
                } else {
                    ScanResult(stopReached = true)
                }

            val totalScanned = imagesResult.scanned + videosResult.scanned
            val totalFound = imagesResult.found + videosResult.found
            val totalDeleted = imagesResult.deleted + videosResult.deleted
            val totalBytesDeleted = imagesResult.bytesDeleted + videosResult.bytesDeleted

            val deletedFb = totalBytesDeleted / (1024L * 1024L * 1024L)

            val title: String
            val message: String

            when {
                totalScanned == 0 -> {
                    title = "No se encontraron archivos"
                    message = "Nose encontraron fotos ni videos para revisar."
                }

                totalFound == 0 -> {
                    title = "Sin cambios"
                    message =
                        "Se encontraron $totalScanned archivos, pero ninguno cumple la antiguedad."
                }

                totalDeleted == 0 -> {
                    title = "Error de permisos"
                    message = "Se encontraron $totalFound archivos, pero no se pudieron borrar."
                }
            }

            showNotification(title, message)
            Result.success()
        } catch (e: Exception) {
            Log.e("CleanWorker", "Error durante la tarea de limpieza", e)
            showNotification("Error en limpieza", "Ocurrió un error: ${e.message}")
            Result.failure()
        }
    }

    private data class ScanResult(
        var deleted: int = 0,
        var found: int = 0,
        var scanned: int = 0,
        var bytesDeleted: Long = 0L,
        var stopReached: Boolean = false
    )

    private fun deleteOldMedia(
        contentUri: Uri,
        cutoffMillis: Long,
        targetBytes: Long,
        bytesDeletedSofar: Long
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
        val selectionArgs = arrayOf((cutoffMillis / 1000L), toString())

        resolver.query(
            contentUri,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_MODIFIED}ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val dateModifiedColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val dateTakenColumn = cursor.getColumnIndex("datetaken")
            val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)

            while (cursor.moveToNext()) {
                result.scanned++

                val id = cursor.getLong(idColumn)
                val path = if (dataColumn != -1) cursor.getString(dataColumn) else null
                val dateModifiedSecs =
                    if (dateModifiedColumn != -1) cursor.getLong(dateModifiedColumn) else 0L
                val dateTakenMillis =
                    if (dateTakenColumn != -1 && !cursor.isNull(dateTakenColumn)) cursor.getLong(
                        dateTakenColumn
                    ) else null
                val fileDateMillis = dateTakenMillis ?: (dateModifiedSecs * 1000L)
                val sizeBytes =
                    if (sizeColumn != -1 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn) else 0L

                if (fileDateMillis < cutoffMillis) {
                    result.found++

                    if (deleteFile(path, contentUri, id, resolver)) {
                        result.deleted++
                        result.bytesDeleted += sizeBytes

                        if (bytesDeletedSoFar + result.bytesDeleted >= targetBytes) {
                            result.stopReached = true
                            break
                        }
                    }
                }
            }
        }
        Log.d(
            "CleanWorker",
            "URI: $contentUri -> Escaneados: ${result.scanned}, Antiguos: ${result.found}, Borrados: ${result.deleted}, Bytes: ${result.bytesDeleted}"
        )
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

            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists() && file.delete()) {
                    resolver.delete(itemUri, null, null) // limpia solo la entrada exacta
                    Log.d("CleanWorker", "Archivo eliminado por ruta: $path")
                    return true
                }
            }

            val rowsDeleted = resolver.delete(itemUri, null, null)
            if (rowsDeleted > 0) {
                Log.d("CleanWorker", "Archivo eliminado por MediaStore ID: $id")
                true
            } else {
                false
            }
        } catch (e: SecurityException) {
            Log.e("CleanWorker", "Error inesperado al eliminar ID: $id", e)
            false
        } catch (e: Exception) {
            Log.e("CleanWorker", "Error inesperado al eliminar ID: $id", e)
            false
        }
    }

    private fun showNotification(title: String, message: String) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if
    }
}
