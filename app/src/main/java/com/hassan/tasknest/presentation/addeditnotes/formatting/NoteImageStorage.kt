package com.hassan.tasknest.presentation.addeditnotes.formatting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val MAX_DIMENSION_PX = 1600
private const val JPEG_QUALITY = 85

/** Manages saving picked note images to internal storage (never cache) and deleting them. */
class NoteImageStorage(private val context: Context) {

    private val imagesDir: File by lazy {
        File(context.filesDir, "note_images").apply {
            if (!exists()) mkdirs()
        }
    }

    /** Downsamples and saves the image at [sourceUri] to internal storage, returning its absolute path or null on failure. */
    suspend fun saveImage(sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            return@withContext null
        }

        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight)
        options.inJustDecodeBounds = false

        val bitmap: Bitmap = try {
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return@withContext null
        } catch (e: Exception) {
            return@withContext null
        }

        val fileName = "note_img_${System.currentTimeMillis()}_${(0..9999).random()}.jpg"
        val file = File(imagesDir, fileName)

        try {
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
        } catch (e: Exception) {
            return@withContext null
        }

        file.absolutePath
    }

    /** Best-effort deletion of a saved image at [filePath], ignoring failures and refusing paths outside [imagesDir]. */
    fun deleteImage(filePath: String) {
        val file = File(filePath)
        if (file.exists() && file.absolutePath.startsWith(imagesDir.absolutePath)) {
            file.delete()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var inSampleSize = 1
        val largestDimension = maxOf(width, height)
        while (largestDimension / (inSampleSize * 2) >= MAX_DIMENSION_PX) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
