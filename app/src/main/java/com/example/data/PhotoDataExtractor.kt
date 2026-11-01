package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import com.example.api.analyzeContainer
import com.example.api.analyzePreprava
import com.example.api.analyzeProtocol
import com.example.api.analyzeVizitka
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoDataExtractor {

    fun extractExifTimestamp(file: File): Long? {
        if (!file.exists()) return null
        return try {
            val exif = ExifInterface(file.absolutePath)
            val dtStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            if (!dtStr.isNullOrBlank()) {
                val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                sdf.parse(dtStr)?.time
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun formatEpochToCzechDate(epochMs: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    private fun calculateOptimalSampleSize(width: Int, height: Int, maxDim: Int = 2560): Int {
        var inSampleSize = 1
        val longest = maxOf(width, height)
        while (longest / (inSampleSize * 2) >= maxDim) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    fun loadBitmap(context: Context, pathOrUri: String): Bitmap? {
        return try {
            val file = File(pathOrUri)
            if (file.exists() && file.isFile) {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = calculateOptimalSampleSize(boundsOpts.outWidth, boundsOpts.outHeight)
                }
                BitmapFactory.decodeFile(file.absolutePath, opts)
            } else if (pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://")) {
                val uri = Uri.parse(pathOrUri)
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = calculateOptimalSampleSize(boundsOpts.outWidth, boundsOpts.outHeight)
                    }
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun extractCustomerData(
        context: Context,
        pathOrUri: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        try {
            val file = File(pathOrUri)
            if (file.exists()) {
                val exifTs = extractExifTimestamp(file)
                if (exifTs != null) {
                    result["datum"] = formatEpochToCzechDate(exifTs)
                    result["timestamp"] = exifTs.toString()
                }
            }
            val bmp = loadBitmap(context, pathOrUri)
            if (bmp != null) {
                val rawJson = analyzeVizitka(bmp)
                val parsed = parseJsonFields(rawJson)
                result.putAll(parsed)
            }
        } catch (e: Exception) {
            android.util.Log.e("PhotoDataExtractor", "Customer extraction failed: ${e.message}")
        }
        result
    }

    suspend fun extractContainerData(
        context: Context,
        pathOrUri: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        try {
            val file = File(pathOrUri)
            if (file.exists()) {
                val exifTs = extractExifTimestamp(file)
                if (exifTs != null) {
                    result["datum"] = formatEpochToCzechDate(exifTs)
                    result["timestamp"] = exifTs.toString()
                }
            }
            val bmp = loadBitmap(context, pathOrUri)
            if (bmp != null) {
                val hint = file.nameWithoutExtension.ifBlank { pathOrUri.substringAfterLast('/') }
                val rawJson = analyzeContainer(bmp, hint)
                val parsed = parseJsonFields(rawJson)
                result.putAll(parsed)
            }
        } catch (e: Exception) {
            android.util.Log.e("PhotoDataExtractor", "Container extraction failed: ${e.message}")
        }
        result
    }

    suspend fun extractTransportData(
        context: Context,
        pathOrUri: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        try {
            val file = File(pathOrUri)
            if (file.exists()) {
                val exifTs = extractExifTimestamp(file)
                if (exifTs != null) {
                    result["date"] = formatEpochToCzechDate(exifTs)
                    result["timestamp"] = exifTs.toString()
                }
            }
            val bmp = loadBitmap(context, pathOrUri)
            if (bmp != null) {
                val rawJson = analyzePreprava(bmp)
                val parsed = parseJsonFields(rawJson)
                result.putAll(parsed)
            }
        } catch (e: Exception) {
            android.util.Log.e("PhotoDataExtractor", "Transport extraction failed: ${e.message}")
        }
        result
    }

    suspend fun extractProtocolData(
        context: Context,
        pathOrUri: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        try {
            val bmp = loadBitmap(context, pathOrUri)
            if (bmp != null) {
                val rawJson = analyzeProtocol(bmp)
                val parsed = parseJsonFields(rawJson)
                result.putAll(parsed)
            }
        } catch (e: Exception) {
            android.util.Log.e("PhotoDataExtractor", "Protocol extraction failed: ${e.message}")
        }
        result
    }

    fun parseJsonFields(jsonStr: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (jsonStr.isBlank()) return map
        try {
            var str = jsonStr.trim()
            val firstBrace = str.indexOf('{')
            val lastBrace = str.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                str = str.substring(firstBrace, lastBrace + 1)
            }
            val json = JSONObject(str)
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (!json.isNull(k)) {
                    val v = json.optString(k, "").trim()
                    if (v.isNotBlank() && !v.equals("null", ignoreCase = true)) {
                        map[k] = v
                    }
                }
            }
        } catch (_: Exception) {}
        return map
    }
}
