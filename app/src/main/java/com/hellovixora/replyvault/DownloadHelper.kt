package com.hellovixora.replyvault

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

/**
 * Saves downloaded files to the device's public Downloads folder.
 *
 * - On API 29+ (Q and above) this uses [MediaStore.Downloads], which requires
 *   no storage permission at all.
 * - On API 24-28 it writes directly into the legacy public Downloads
 *   directory, which requires the WRITE_EXTERNAL_STORAGE permission
 *   (declared with maxSdkVersion="28" in the manifest, and requested at
 *   runtime by MainActivity before this is called).
 */
object DownloadHelper {

    sealed class Result {
        data class Success(val displayName: String) : Result()
        data class Failure(val reason: String) : Result()
    }

    fun saveBytes(context: Context, bytes: ByteArray, filename: String, mimeType: String): Result {
        val safeName = sanitizeFileName(filename)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, bytes, safeName, mimeType)
            } else {
                saveLegacy(bytes, safeName)
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Unknown error")
        }
    }

    fun saveBase64(context: Context, base64Data: String, filename: String, mimeType: String): Result {
        return try {
            val cleaned = base64Data.substringAfter("base64,", base64Data)
            val bytes = Base64.decode(cleaned, Base64.DEFAULT)
            saveBytes(context, bytes, filename, mimeType)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Invalid file data")
        }
    }

    private fun saveViaMediaStore(context: Context, bytes: ByteArray, filename: String, mimeType: String): Result {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)
            ?: return Result.Failure("Could not create file")

        resolver.openOutputStream(uri)?.use { out -> out.write(bytes) }
            ?: return Result.Failure("Could not open output stream")

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return Result.Success(filename)
    }

    private fun saveLegacy(bytes: ByteArray, filename: String): Result {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        var target = File(downloadsDir, filename)
        var counter = 1
        val dotIndex = filename.lastIndexOf('.')
        val base = if (dotIndex >= 0) filename.substring(0, dotIndex) else filename
        val ext = if (dotIndex >= 0) filename.substring(dotIndex) else ""
        while (target.exists()) {
            target = File(downloadsDir, "$base ($counter)$ext")
            counter++
        }

        FileOutputStream(target).use { it.write(bytes) }
        return Result.Success(target.name)
    }

    private fun sanitizeFileName(name: String): String {
        val trimmed = name.trim().ifBlank { "replyvault-download" }
        return trimmed.replace(Regex("[/\\\\:*?\"<>|]"), "_")
    }
}
