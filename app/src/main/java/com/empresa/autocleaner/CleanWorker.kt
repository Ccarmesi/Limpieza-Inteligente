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
import java.io.File

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
            showNotification("Falta permiso crítico", "No se pueden borrar archivos. Ve a Ajustes y concede 'Acceso a todos los archivos'.")
            return Result.failure()
        }

        val daysOld = inputData.getLong(KEY_DAYS_OLD, 30L)
        Log.d("CleanWorker", "Iniciando tarea de limpieza para archivos con más de $daysOld días.")

        // Calculamos el tiempo de corte en Milisegundos
        val currentTime = System.currentTimeMillis()
        val cutoffTimestampMillis = currentTime - (daysOld * 24 * 60 * 60 * 1000)

        return try {
            val imagesResult = deleteOldMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cutoffTimestampMillis)
            val videoResult = deleteOldMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cutoffTimestampMillis)
 
            var totalScanned = imagesResult.scanned
            var totalFound: Int
            var totalDeleted: Int
            var manualScanRan = false

            // FALLBACK: Si MediaStore dice que hay 0 archivos (error de índice),
            // activamos el escaneo manual directo en las carpetas.
            if (totalScanned == 0) {
                manualScanRan = true
                Log.w("CleanWorker", "MediaStore devolvió 0 archivos. Iniciando escaneo manual de carpetas...")
                val manualResult = scanFilesManual(cutoffTimestampMillis)
                totalScanned = manualResult.scanned
                totalFound = manualResult.found
                totalDeleted = manualResult.deleted
            } else {
                totalFound = imagesResult.found + videoResult.found
                totalDeleted = imagesResult.deleted + videoResult.deleted
            }

            Log.d("CleanWorker", "Limpieza fin. Escaneados: $totalScanned, Antiguos: $totalFound, Borrados: $totalDeleted")

            val title: String
            val message: String

            if (totalScanned == 0) {
                title = "No se encontraron archivos"
                message = "No se encontraron fotos ni videos en el dispositivo."
            } else if (totalFound == 0) {
                title = "Sin cambios"
                message = "Se escanearon $totalScanned archivos, pero ninguno es tan antiguo."
            } else if (totalDeleted == 0) {
                title = "Error de permisos"
                message = "Se encontraron $totalFound archivos antiguos pero no se pudieron borrar."
            } else {
                title = "Limpieza completada"
                message = "Se eliminaron $totalDeleted archivos de $totalFound antiguos detectados."
            }
            showNotification(title, message)
            Result.success()
        } catch (e: Exception) {
            Log.e("CleanWorker", "Error durante la tarea de limpieza", e)
            showNotification("Error en limpieza", "Ocurrió un error: ${e.message}")
            Result.failure()
        }
    }

    // Clase simple para guardar los resultados del escaneo
    private data class ScanResult(var deleted: Int = 0, var found: Int = 0, var scanned: Int = 0)

    private fun deleteOldMedia(contentUri: Uri, cutoffMillis: Long): ScanResult {
        val resolver = applicationContext.contentResolver
        val result = ScanResult()

        // We need the DATA column to get the file path for direct deletion.
        // It's deprecated for privacy reasons in Android 10+, but with MANAGE_EXTERNAL_STORAGE it should work.
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA, // Request file path
            MediaStore.MediaColumns.DATE_MODIFIED,
            // DATE_TAKEN suele estar disponible en Images y Video
            "datetaken" 
        )

        // IMPORTANTE: Quitamos el filtro SQL (selection = null) para escanear TODO y filtrar en código.
        // Esto soluciona el problema de "0 encontrados" si la base de datos tiene formatos de fecha mixtos.
        resolver.query(contentUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val dateModifiedColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val dateTakenColumn = cursor.getColumnIndex("datetaken")

            while (cursor.moveToNext()) {
                result.scanned++
                
                val id = cursor.getLong(idColumn)
                val dateModifiedSecs = if (dateModifiedColumn != -1) cursor.getLong(dateModifiedColumn) else 0L
                
                // Intentamos obtener la fecha real de captura (milisegundos)
                val dateTakenMillis = if (dateTakenColumn != -1 && !cursor.isNull(dateTakenColumn)) {
                    cursor.getLong(dateTakenColumn)
                } else {
                    null
                }

                // Si existe fecha de captura la usamos, si no, usamos la de modificación convertida a millis
                val fileDateMillis = dateTakenMillis ?: (dateModifiedSecs * 1000)

                if (fileDateMillis < cutoffMillis) {
                    result.found++
                    val path = if (dataColumn != -1) cursor.getString(dataColumn) else null
                    
                    if (deleteFile(path, contentUri, id, resolver)) {
                        result.deleted++
                    }
                }
            }
        }
        Log.d("CleanWorker", "URI: $contentUri -> Escaneados: ${result.scanned}, Antiguos: ${result.found}, Borrados: ${result.deleted}")
        return result
    }

    private fun scanFilesManual(cutoffMillis: Long): ScanResult {
        val result = ScanResult()
        val root = Environment.getExternalStorageDirectory()
        if (!root.canRead()) {
            return result
        }

        // Lista de carpetas comunes donde buscar fotos y videos si MediaStore falla
        val commonPaths = listOf(
            File(Environment.getExternalStorageDirectory(), "DCIM"),
            File(Environment.getExternalStorageDirectory(), "Pictures"),
            File(Environment.getExternalStorageDirectory(), "Movies"),
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media"),
            // Ruta para las versiones más nuevas de WhatsApp
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media")
        )

        val mediaExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "mkv", "mov", "avi", "3gp")

        for (dir in commonPaths) {
            if (dir.exists() && dir.isDirectory) {
                try {
                    dir.walkTopDown().maxDepth(10).forEach { file ->
                        if (file.isFile && file.extension.lowercase() in mediaExtensions) {
                            result.scanned++
                            if (file.lastModified() < cutoffMillis) {
                                result.found++
                                if (file.delete()) {
                                    result.deleted++
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CleanWorker", "Error walking directory ${dir.absolutePath}", e)
                }
            }
        }
        Log.i("CleanWorker", "Resultado escaneo manual -> Escaneados: ${result.scanned}, Antiguos: ${result.found}, Borrados: ${result.deleted}")
        return result
    }

    private fun deleteFile(path: String?, contentUri: Uri, id: Long, resolver: ContentResolver): Boolean {
        try {
            // First, try deleting the file directly via its path.
            // This is often more reliable with MANAGE_EXTERNAL_STORAGE.
            if (path != null) {
                val file = File(path)
                if (file.exists() && file.delete()) {
                    Log.d("CleanWorker", "Archivo eliminado por ruta: $path")
                    // Also remove it from MediaStore to avoid ghost entries.
                    resolver.delete(ContentUris.withAppendedId(contentUri, id), null, null)
                    return true
                }
            }

            // If file path deletion fails or path is null, fall back to ContentResolver deletion.
            val rowsDeleted = resolver.delete(ContentUris.withAppendedId(contentUri, id), null, null)
            if (rowsDeleted > 0) {
                Log.d("CleanWorker", "Archivo eliminado por ContentResolver: $id")
                return true
            }
        } catch (e: SecurityException) {
            Log.e("CleanWorker", "Fallo al eliminar archivo (ID: $id). ¿Permiso denegado?", e)
        } catch (e: Exception) {
            Log.e("CleanWorker", "Error inesperado al eliminar archivo (ID: $id)", e)
        }
        return false
    }

    private fun showNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Limpieza Automática", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_delete) // System icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            notificationManager.notify(1, notification)
        } catch (e: SecurityException) {
            Log.e("CleanWorker", "No se pudo mostrar la notificación. Falta el permiso POST_NOTIFICATIONS.")
        }
    }
}
