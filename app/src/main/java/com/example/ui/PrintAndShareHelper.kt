package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

// ============================================================================
// 1. UNIVERSAL PHOTO SYNC, BASE64 SELF-HEALING & COIL MODEL RESOLVER
// ============================================================================

fun isGeneratedFallbackPhoto(uri: String?): Boolean {
    if (uri.isNullOrBlank()) return true
    val s = uri.trim()
    return s.startsWith("data:image/svg+xml")
}

fun sanitizeImageUri(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    var s = raw.trim()
    if (s.contains("base64,file://")) {
        s = s.replace(Regex("base64,(file://)+"), "base64,")
    }
    if (s.startsWith("file://data:image")) {
        s = s.removePrefix("file://")
    }
    if (s.startsWith("file:///9j/") || s.startsWith("/9j/")) {
        val cleanB64 = s.replaceFirst(Regex("^(file://)+"), "")
        return "data:image/jpeg;base64,$cleanB64"
    }
    if (s.startsWith("file://iVBOR") || s.startsWith("iVBOR")) {
        val cleanB64 = s.replaceFirst(Regex("^(file://)+"), "")
        return "data:image/png;base64,$cleanB64"
    }
    if (s == "data:image/jpeg;base64" || s == "data:image/png;base64" || s == "data:image/webp;base64") {
        return null
    }
    return s
}

fun parseExtraImageUris(rawExtra: String?): List<String> {
    if (rawExtra.isNullOrBlank()) return emptyList()
    val repairedRaw = rawExtra.replace(Regex("base64,(file://)+"), "base64,")
    val pipeParts = repairedRaw.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    val result = mutableListOf<String>()
    for (part in pipeParts) {
        if (part.startsWith("data:image/svg+xml")) {
            result.add(part)
            continue
        }
        val commaTokens = part.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        var i = 0
        while (i < commaTokens.size) {
            val tk = commaTokens[i]
            if (tk.startsWith("data:image") && !tk.contains(",") && i + 1 < commaTokens.size) {
                val payload = commaTokens[i + 1].replaceFirst(Regex("^(file://)+"), "")
                result.add("$tk,$payload")
                i += 2
            } else {
                val san = sanitizeImageUri(tk)
                if (!san.isNullOrBlank()) result.add(san)
                i += 1
            }
        }
    }
    return result.distinct()
}

fun primaryContainerPhoto(card: ContainerCard): String? {
    val main = sanitizeImageUri(card.imageUri)
    val extras = parseExtraImageUris(card.extraImages)
    // Prefer real user photo (JPEG/PNG/file/http) over generated SVG fallback
    if (!main.isNullOrBlank() && !isGeneratedFallbackPhoto(main)) {
        return main
    }
    val realExtra = extras.firstOrNull { !isGeneratedFallbackPhoto(it) }
    if (!realExtra.isNullOrBlank()) {
        return realExtra
    }
    if (!main.isNullOrBlank()) return main
    return extras.firstOrNull()
}

fun extractPhotoTimestamp(
    context: Context?,
    uri: android.net.Uri? = null,
    file: File? = null
): Long {
    val exifPatterns = listOf(
        "yyyy:MM:dd HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy:MM:dd HH:mm"
    )
    fun parseExifDate(str: String?): Long? {
        val clean = str?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("0000") } ?: return null
        for (pat in exifPatterns) {
            try {
                val sdf = java.text.SimpleDateFormat(pat, java.util.Locale.getDefault())
                val parsed = sdf.parse(clean)
                if (parsed != null && parsed.time > 946684800000L) {
                    return parsed.time
                }
            } catch (_: Exception) {}
        }
        return null
    }

    // 1. Try EXIF from Uri InputStream (before any file modification)
    if (context != null && uri != null) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = android.media.ExifInterface(stream)
                val dt = exif.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(android.media.ExifInterface.TAG_DATETIME_DIGITIZED)
                    ?: exif.getAttribute(android.media.ExifInterface.TAG_DATETIME)
                parseExifDate(dt)?.let { return it }
            }
        } catch (_: Exception) {}
    }

    // 2. Try EXIF from File
    if (file != null && file.exists()) {
        try {
            val exif = android.media.ExifInterface(file.absolutePath)
            val dt = exif.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(android.media.ExifInterface.TAG_DATETIME_DIGITIZED)
                ?: exif.getAttribute(android.media.ExifInterface.TAG_DATETIME)
            parseExifDate(dt)?.let { return it }
        } catch (_: Exception) {}
    }

    // 3. Try MediaStore / DocumentColumns from ContentResolver
    if (context != null && uri != null) {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val colNames = listOf("datetaken", "last_modified", "date_modified", "date_added")
                    for (col in colNames) {
                        val idx = cursor.getColumnIndex(col)
                        if (idx >= 0 && !cursor.isNull(idx)) {
                            val raw = cursor.getLong(idx)
                            val ms = if (raw in 100000000L..9999999999L) raw * 1000L else raw
                            if (ms > 946684800000L) return ms
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // 4. Fallback to file lastModified or current time
    val fileTime = file?.lastModified() ?: 0L
    return if (fileTime > 946684800000L) fileTime else System.currentTimeMillis()
}

/**
 * Crops the photo to keep the entire container visible plus a small (~4%) margin around it,
 * and returns the cropped Bitmap along with (biasXPercent, biasYPercent) in -100..100
 * pointing at the container's designation so previews always keep the designation visible.
 */
fun cropContainerBitmapWithSmallMargin(
    bitmap: Bitmap,
    containerBox: List<Int>?,
    designationBox: List<Int>?
): Pair<Bitmap, Pair<Int, Int>> {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 32 || h <= 32) return bitmap to (45 to -75)

    fun validBox(box: List<Int>?): IntArray? {
        if (box == null || box.size < 4) return null
        val ymin = box[0].coerceIn(0, 1000)
        val xmin = box[1].coerceIn(0, 1000)
        val ymax = box[2].coerceIn(0, 1000)
        val xmax = box[3].coerceIn(0, 1000)
        if (ymax <= ymin + 10 || xmax <= xmin + 10) return null
        return intArrayOf(ymin, xmin, ymax, xmax)
    }

    val cBox = validBox(containerBox)
    val dBox = validBox(designationBox)

    var cropYMin = 0f
    var cropXMin = 0f
    var cropYMax = 1000f
    var cropXMax = 1000f

    if (cBox != null && (cBox[2] - cBox[0] >= 120) && (cBox[3] - cBox[1] >= 120)) {
        var ymin = cBox[0].toFloat()
        var xmin = cBox[1].toFloat()
        var ymax = cBox[2].toFloat()
        var xmax = cBox[3].toFloat()
        // Ensure designationBox is always inside the container crop
        if (dBox != null) {
            ymin = minOf(ymin, dBox[0].toFloat())
            xmin = minOf(xmin, dBox[1].toFloat())
            ymax = maxOf(ymax, dBox[2].toFloat())
            xmax = maxOf(xmax, dBox[3].toFloat())
        }
        val boxW = xmax - xmin
        val boxH = ymax - ymin
        // Small margin around the container (~4.5% of container dimensions)
        val padX = maxOf(22f, boxW * 0.045f)
        val padY = maxOf(22f, boxH * 0.045f)
        cropXMin = (xmin - padX).coerceIn(0f, 1000f)
        cropYMin = (ymin - padY).coerceIn(0f, 1000f)
        cropXMax = (xmax + padX).coerceIn(0f, 1000f)
        cropYMax = (ymax + padY).coerceIn(0f, 1000f)
    }

    val pxLeft = ((cropXMin / 1000f) * w).toInt().coerceIn(0, w - 1)
    val pxTop = ((cropYMin / 1000f) * h).toInt().coerceIn(0, h - 1)
    val pxRight = ((cropXMax / 1000f) * w).toInt().coerceIn(pxLeft + 1, w)
    val pxBottom = ((cropYMax / 1000f) * h).toInt().coerceIn(pxTop + 1, h)
    val cropW = pxRight - pxLeft
    val cropH = pxBottom - pxTop

    val croppedBmp = if (cropW >= w * 0.98f && cropH >= h * 0.98f) {
        bitmap
    } else {
        try {
            Bitmap.createBitmap(bitmap, pxLeft, pxTop, cropW, cropH)
        } catch (_: Exception) {
            bitmap
        }
    }

    // Calculate relative position of designation inside the cropped image for preview alignment
    val spanX = (cropXMax - cropXMin).coerceAtLeast(1f)
    val spanY = (cropYMax - cropYMin).coerceAtLeast(1f)
    val (biasXInt, biasYInt) = if (dBox != null) {
        val dCenterX = (dBox[1] + dBox[3]) / 2f
        val dCenterY = (dBox[0] + dBox[2]) / 2f
        val relX = ((dCenterX - cropXMin) / spanX).coerceIn(0f, 1f)
        val relY = ((dCenterY - cropYMin) / spanY).coerceIn(0f, 1f)
        val bx = ((relX * 2f - 1f) * 100f).toInt().coerceIn(-100, 100)
        val by = ((relY * 2f - 1f) * 100f).toInt().coerceIn(-100, 100)
        bx to by
    } else {
        // Default: upper-right region where container designations are located
        45 to -75
    }

    return croppedBmp to (biasXInt to biasYInt)
}

fun containerPreviewAlignment(imageUri: String?): androidx.compose.ui.Alignment {
    if (!imageUri.isNullOrBlank()) {
        val match = Regex("""_bx(-?\d+)_by(-?\d+)""").find(imageUri)
        if (match != null) {
            val bx = (match.groupValues[1].toIntOrNull() ?: 45).coerceIn(-100, 100) / 100f
            val by = (match.groupValues[2].toIntOrNull() ?: -75).coerceIn(-100, 100) / 100f
            return androidx.compose.ui.BiasAlignment(bx, by)
        }
    }
    return androidx.compose.ui.BiasAlignment(0.45f, -0.75f)
}

private fun renderFallbackContainerBitmap(labelText: String = "HYUNDAI 8x3m"): Bitmap {
    val bmp = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = AndroidColor.parseColor("#DBEAFE")
    canvas.drawRect(0f, 0f, 640f, 260f, paint)
    paint.color = AndroidColor.parseColor("#CBD5E1")
    canvas.drawRect(0f, 260f, 640f, 400f, paint)
    paint.color = AndroidColor.parseColor("#F8FAFC")
    canvas.drawRoundRect(RectF(60f, 90f, 580f, 325f), 6f, 6f, paint)
    paint.color = AndroidColor.parseColor("#0B1B3D")
    canvas.drawRect(60f, 90f, 580f, 118f, paint)
    canvas.drawRect(60f, 308f, 580f, 325f, paint)
    paint.color = AndroidColor.parseColor("#1E3A8A")
    canvas.drawRoundRect(RectF(120f, 148f, 220f, 235f), 4f, 4f, paint)
    canvas.drawRoundRect(RectF(255f, 148f, 355f, 235f), 4f, 4f, paint)
    paint.color = AndroidColor.WHITE
    paint.textSize = 16f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("KONTEJNERY OSTRAVA • $labelText", 84f, 109f, paint)
    return bmp
}

/**
 * Converts `data:image/...;base64,...` strings into a ByteArray so Coil 2's AsyncImage
 * renders Base64 images from Firestore/PC natively without failing on `data:` URI scheme.
 */
fun resolveCoilImageModel(uri: String?): Any? {
    val clean = sanitizeImageUri(uri) ?: return null
    if (clean.startsWith("data:image/svg+xml")) {
        return try {
            val bmp = renderFallbackContainerBitmap("HYUNDAI 8x3m")
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
            out.toByteArray()
        } catch (_: Exception) {
            clean
        }
    }
    if (clean.startsWith("data:image") && clean.contains("base64,")) {
        return try {
            val b64 = clean.substringAfter("base64,").trim()
            Base64.decode(b64, Base64.DEFAULT)
        } catch (_: Exception) {
            clean
        }
    }
    if (clean.startsWith("/data/") || clean.startsWith("/storage/") || clean.startsWith("/sdcard/")) {
        val f = File(clean)
        if (f.exists()) return f
    }
    return clean
}

fun decodeBitmapFromAnyUri(uri: String?): Bitmap? {
    val clean = sanitizeImageUri(uri) ?: return null
    return try {
        if (clean.startsWith("data:image/svg+xml")) {
            renderFallbackContainerBitmap("HYUNDAI 8x3m")
        } else if (clean.startsWith("data:image") && clean.contains("base64,")) {
            val bytes = Base64.decode(clean.substringAfter("base64,").trim(), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } else {
            val path = clean.removePrefix("file://")
            val f = File(path)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        }
    } catch (_: Exception) {
        null
    }
}

// ============================================================================
// 2. UNIVERSAL 2-STEP DELETE CONFIRMATION DIALOG
// ("Před smazáním nějaké informace. Se vždy ještě jednou zeptej. Jestli to chci.")
// ============================================================================

@Composable
fun DoubleConfirmDeleteDialog(
    itemTitle: String,
    itemDescription: String = "",
    onDismiss: () -> Unit,
    onConfirmedDelete: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Potvrzení smazání",
                tint = if (step == 1) Color(0xFFD97706) else Color(0xFFDC2626),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (step == 1) "Smazat informaci? (Krok 1/2)" else "Ještě jednou pro jistotu! (Krok 2/2)",
                fontWeight = FontWeight.ExtraBold,
                color = if (step == 1) Color(0xFF92400E) else Color(0xFF991B1B)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step == 1) {
                    Text(
                        text = "Opravdu chcete smazat: $itemTitle?",
                        fontWeight = FontWeight.Bold
                    )
                    if (itemDescription.isNotBlank()) {
                        Text(
                            text = itemDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                    }
                } else {
                    Surface(
                        color = Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFF87171))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Ptáme se ještě jednou: Opravdu si přejete tuto informaci TRVALE SMAZAT?",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF991B1B)
                            )
                            Text(
                                text = "Položka: $itemTitle",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7F1D1D)
                            )
                            Text(
                                text = "Tato akce je nevratná a odstraní záznam z mobilu i PC.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7F1D1D)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (step == 1) {
                Button(
                    onClick = { step = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text("Ano, pokračovat ke smazání", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        onConfirmedDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ANO, CHCI TO SMAZAT", color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(if (step == 1) "Zrušit" else "Ne, nemazat", fontWeight = FontWeight.Bold)
            }
        }
    )
}

// ============================================================================
// 3. AGENDA PRINT / WHATSAPP / EMAIL ACTION BAR
// ============================================================================

@Composable
fun PrintShareActionBar(
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    onPrint: () -> Unit,
    onWhatsApp: () -> Unit,
    onEmail: () -> Unit
) {
    AgendaPrintShareBar(
        modifier = modifier,
        compact = compact,
        onPrint = onPrint,
        onWhatsApp = onWhatsApp,
        onEmail = onEmail
    )
}

@Composable
fun AgendaPrintShareBar(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onPrint: () -> Unit,
    onWhatsApp: () -> Unit,
    onEmail: () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPrint,
            modifier = Modifier.weight(1f).heightIn(min = if (compact) 34.dp else 40.dp),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, Color(0xFF1B5E8B)),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFFF0F9FF),
                contentColor = Color(0xFF0F3D5E)
            )
        ) {
            Icon(Icons.Default.Print, contentDescription = "Tisk karty", modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Tisk", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        OutlinedButton(
            onClick = onWhatsApp,
            modifier = Modifier.weight(1f).heightIn(min = if (compact) 34.dp else 40.dp),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, Color(0xFF15803D)),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFFF0FDF4),
                contentColor = Color(0xFF166534)
            )
        ) {
            Icon(Icons.Default.Share, contentDescription = "Sdílet přes WhatsApp", modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        OutlinedButton(
            onClick = onEmail,
            modifier = Modifier.weight(1f).heightIn(min = if (compact) 34.dp else 40.dp),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, Color(0xFF1D4ED8)),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFFEFF6FF),
                contentColor = Color(0xFF1E3A8A)
            )
        ) {
            Icon(Icons.Default.Email, contentDescription = "Odeslat e-mailem", modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("E-mail", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

// ============================================================================
// 4. OFFICIAL LOGO KONTEJNERY OSTRAVA 2025 & PATIČKA MAIL 2026 PDF DRAWING
// ============================================================================

private fun formatCzCurrency(value: Double): String {
    val nf = NumberFormat.getInstance(Locale.forLanguageTag("cs-CZ"))
    return "${nf.format(value.roundToLong())} Kč"
}

/**
 * Draws the official LOGO Kontejnery Ostrava 2025_5 (dark navy #001B44 rectangle with white
 * perspective container roof, corner posts with ISO holes, right window trapezoid,
 * bold KONTEJNERY OSTRAVA, PRODEJ | PRONÁJEM | DOPRAVA).
 */
fun drawKontejneryOstravaLogo(canvas: Canvas, logoX: Float, logoY: Float, logoW: Float, logoH: Float) {
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#011C45")
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(logoX, logoY, logoX + logoW, logoY + logoH), 5f, 5f, bgPaint)

    val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }
    val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#011C45")
        style = Paint.Style.FILL
    }

    fun sx(x760: Float) = logoX + ((x760 - 8f) / 760f) * logoW
    fun sy(y490: Float) = logoY + ((y490 + 6f) / 490f) * logoH

    // Left corner post with rounded cap & oval ISO hole
    canvas.drawRoundRect(RectF(sx(106f), sy(144f), sx(146f), sy(238f)), logoW * 0.02f, logoW * 0.02f, whiteFill)
    canvas.drawRect(sx(106f), sy(180f), sx(146f), sy(238f), whiteFill)
    canvas.drawOval(RectF(sx(115f), sy(159f), sx(133f), sy(183f)), holePaint)

    // Left angled roof beam
    val leftBeam = Path().apply {
        moveTo(sx(136f), sy(158f))
        lineTo(sx(399f), sy(51f))
        lineTo(sx(399f), sy(86f))
        lineTo(sx(136f), sy(191f))
        close()
    }
    canvas.drawPath(leftBeam, whiteFill)

    // Center tall corner post with rounded cap & oval ISO hole
    canvas.drawRoundRect(RectF(sx(405f), sy(19f), sx(452f), sy(238f)), logoW * 0.025f, logoW * 0.025f, whiteFill)
    canvas.drawRect(sx(405f), sy(60f), sx(452f), sy(238f), whiteFill)
    canvas.drawOval(RectF(sx(418f), sy(35f), sx(438f), sy(61f)), holePaint)

    // Right angled roof beam
    val rightBeam = Path().apply {
        moveTo(sx(458f), sy(49f))
        lineTo(sx(632f), sy(126f))
        lineTo(sx(632f), sy(160f))
        lineTo(sx(458f), sy(85f))
        close()
    }
    canvas.drawPath(rightBeam, whiteFill)

    // Right corner post with rounded cap & oval ISO hole
    canvas.drawRoundRect(RectF(sx(630f), sy(113f), sx(670f), sy(238f)), logoW * 0.02f, logoW * 0.02f, whiteFill)
    canvas.drawRect(sx(630f), sy(150f), sx(670f), sy(238f), whiteFill)
    canvas.drawOval(RectF(sx(640f), sy(129f), sx(658f), sy(153f)), holePaint)

    // Perspective window trapezoid on right wall
    val windowPoly = Path().apply {
        moveTo(sx(518f), sy(153f))
        lineTo(sx(582f), sy(172f))
        lineTo(sx(582f), sy(226f))
        lineTo(sx(518f), sy(226f))
        close()
    }
    canvas.drawPath(windowPoly, whiteFill)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val cx = logoX + logoW * 0.5f
    textPaint.textSize = logoH * 0.145f
    textPaint.letterSpacing = 0.03f
    canvas.drawText("KONTEJNERY", cx, sy(316f), textPaint)

    textPaint.textSize = logoH * 0.142f
    textPaint.letterSpacing = 0.22f
    canvas.drawText("OSTRAVA", cx, sy(392f), textPaint)

    textPaint.textSize = (logoH * 0.072f).coerceAtLeast(4.5f)
    textPaint.letterSpacing = 0.02f
    canvas.drawText("PRODEJ | PRONÁJEM | DOPRAVA", cx, sy(454f), textPaint)
}

/**
 * Draws the official Dodavatel & Odběratel top header block in the style of Předávací protokol & Fa_vzor.pdf.
 */
fun drawStandardDocumentHeader(
    canvas: Canvas,
    docTitleLeft: String,
    docTitleRight: String,
    docNumberLabel: String,
    docNumberValue: String,
    dateIssued: String,
    extraSubLabel: String = "",
    extraSubValue: String = "",
    customerCompany: String,
    customerName: String,
    customerAddress: String,
    customerIc: String,
    customerDic: String,
    customerPhone: String,
    customerEmail: String
) {
    val blueColor = AndroidColor.parseColor("#000080")
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        textSize = 10f
    }
    val paintBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 15f
    }
    val paintBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = blueColor
        textSize = 9f
    }
    val paintBlueBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = blueColor
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 12f
    }

    // Main title row
    canvas.drawText(docTitleLeft, 30f, 42f, paintBold)
    paintBlueBold.textAlign = Paint.Align.RIGHT
    canvas.drawText(docTitleRight, 565f, 42f, paintBlueBold)
    paintBlueBold.textAlign = Paint.Align.LEFT

    // Outer box for Dodavatel (left) and Odběratel (right)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f
    canvas.drawRect(30f, 50f, 565f, 214f, paint)
    canvas.drawLine(297f, 50f, 297f, 214f, paint)
    paint.style = Paint.Style.FILL

    paintBlue.textSize = 8.5f
    canvas.drawText("Dodavatel:", 35f, 62f, paintBlue)
    canvas.drawText("Odběratel:", 303f, 62f, paintBlue)

    // Draw official LOGO Kontejnery Ostrava 2025 centered in left column
    drawKontejneryOstravaLogo(canvas, 35f, 78f, 95f, 72f)

    // Dodavatel details (without Depo 1, IČO & DIČ directly following address, Tel. and Mobil on separate lines)
    val dx = 136f
    var dy = 79f
    paintBold.textSize = 10.2f
    canvas.drawText("KONTEJNERY OSTRAVA s.r.o.", dx, dy, paintBold)
    paint.textSize = 9f
    dy += 12f
    canvas.drawText("Pavlovova 2701/50", dx, dy, paint)
    dy += 11f
    canvas.drawText("700 30 Ostrava - Jih", dx, dy, paint)
    dy += 13f
    paintBlueBold.textSize = 8.8f
    canvas.drawText("IČ: 27771164  |  DIČ: CZ27771164", dx, dy, paintBlueBold)
    paint.textSize = 8.8f
    dy += 13f
    canvas.drawText("Tel.: +420 733 609 813", dx, dy, paint)
    dy += 11f
    canvas.drawText("Mobil: +420 608 813 484", dx, dy, paint)
    dy += 11f
    canvas.drawText("E-mail: info@kontejnery-ostrava.cz", dx, dy, paint)
    dy += 11f
    canvas.drawText("www.kontejnery-ostrava.cz", dx, dy, paint)

    // Sub-box under Dodavatel for document number & date
    paintBlueBold.textSize = 8.5f
    canvas.drawText(docNumberLabel, 35f, 190f, paintBlueBold)
    canvas.drawText("Vystaveno:", 35f, 203f, paint)
    paint.style = Paint.Style.STROKE
    canvas.drawRect(136f, 180f, 290f, 208f, paint)
    canvas.drawLine(136f, 194f, 290f, 194f, paint)
    paint.style = Paint.Style.FILL
    paintBold.textSize = 9f
    canvas.drawText(docNumberValue, 141f, 190f, paintBold)
    paint.textSize = 9f
    canvas.drawText(dateIssued, 141f, 203f, paint)

    // Odběratel details
    val ox = 303f
    var oy = 82f
    paintBold.textSize = 11f
    if (customerCompany.isNotBlank()) {
        canvas.drawText(customerCompany.take(38), ox, oy, paintBold)
        oy += 14f
    }
    if (customerName.isNotBlank() && customerName != customerCompany) {
        paintBold.textSize = 10f
        canvas.drawText(customerName.take(38), ox, oy, paintBold)
        oy += 13f
    }
    paint.textSize = 9.5f
    if (customerAddress.isNotBlank()) {
        canvas.drawText(customerAddress.take(42), ox, oy, paint)
        oy += 13f
    }
    if (customerIc.isNotBlank() || customerDic.isNotBlank()) {
        paintBlueBold.textSize = 9.5f
        val icDicLine = buildString {
            if (customerIc.isNotBlank()) append("IČ: $customerIc   ")
            if (customerDic.isNotBlank()) append("DIČ: $customerDic")
        }
        canvas.drawText(icDicLine, ox, oy, paintBlueBold)
        oy += 13f
    }
    if (customerPhone.isNotBlank()) {
        canvas.drawText("Tel.: $customerPhone", ox, oy, paint)
        oy += 12f
    }
    if (customerEmail.isNotBlank()) {
        canvas.drawText("E-mail: $customerEmail", ox, oy, paint)
        oy += 12f
    }
    if (extraSubLabel.isNotBlank() || extraSubValue.isNotBlank()) {
        oy = maxOf(oy + 4f, 192f)
        paintBlueBold.textSize = 9.5f
        canvas.drawText("$extraSubLabel $extraSubValue".take(44), ox, oy, paintBlueBold)
    }
}

/**
 * Draws the signature boxes and official Patička mail 2026 footer banner at the bottom of A4 page.
 */
fun drawStandardDocumentFooter(
    canvas: Canvas,
    leftSignTitle: String = "Převzal / Odběratel podpis:",
    rightSignTitle: String = "Předal / Vystavil podpis:",
    leftSignSubtext: String = ""
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        textSize = 10f
    }
    val paintBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 10f
    }
    val paintBlueBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#000080")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 9.5f
    }

    val sigY = 672f
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f
    canvas.drawRect(30f, sigY, 260f, sigY + 68f, paint)
    canvas.drawRect(335f, sigY, 565f, sigY + 68f, paint)
    paint.style = Paint.Style.FILL

    canvas.drawText(leftSignTitle, 36f, sigY + 14f, paintBold)
    if (leftSignSubtext.isNotBlank()) {
        canvas.drawText(leftSignSubtext.take(38), 36f, sigY + 30f, paintBlueBold)
    }
    canvas.drawText(rightSignTitle, 341f, sigY + 14f, paintBold)
    canvas.drawText("Kontejnery Ostrava, s.r.o.", 341f, sigY + 30f, paint)

    // Patička mail 2026 graphic bar at bottom (y = 750..818)
    val footerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#F8FAFC")
        style = Paint.Style.FILL
    }
    val footerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#0B1B3D")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    canvas.drawRoundRect(RectF(30f, 750f, 565f, 816f), 4f, 4f, footerBg)
    canvas.drawRoundRect(RectF(30f, 750f, 565f, 816f), 4f, 4f, footerBorder)

    drawKontejneryOstravaLogo(canvas, 34f, 754f, 58f, 58f)

    paintBold.color = AndroidColor.parseColor("#0B1B3D")
    paintBold.textSize = 10f
    canvas.drawText("Kontejnery Ostrava s.r.o.  •  PRODEJ | PRONÁJEM | PŘEPRAVA KONTEJNERŮ", 100f, 766f, paintBold)

    paint.color = AndroidColor.parseColor("#1E293B")
    paint.textSize = 8.5f
    canvas.drawText("Pavlovova 2701/50, 700 30 Ostrava - Jih  |  IČ: 27771164  |  DIČ: CZ27771164", 100f, 780f, paint)
    canvas.drawText("Tel.: +420 733 609 813  |  Mobil: +420 608 813 484  |  info@kontejnery-ostrava.cz", 100f, 793f, paint)

    paintBold.color = AndroidColor.parseColor("#000080")
    paintBold.textSize = 9.5f
    canvas.drawText("Pohodové zázemí za zvýhodněné ceny !!!", 100f, 808f, paintBold)
    paintBold.textAlign = Paint.Align.RIGHT
    canvas.drawText("www.kontejnery-ostrava.cz", 555f, 808f, paintBold)
    paintBold.textAlign = Paint.Align.LEFT
}

// ============================================================================
// 5. PDF GENERATORS FOR ALL AGENDAS
// ============================================================================

fun generateFakturaPdf(context: Context, faktura: FakturaRecord): File {
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = document.startPage(pageInfo)
    val canvas = page.canvas

    val isZalohova = faktura.typDokladu == "ZALOHOVA_FAKTURA"
    val isPronajem = faktura.ucelFaktury == "PRONAJEM"
    val docTitleLeft = if (isZalohova) "ZÁLOHOVÁ FAKTURA - VÝZVA K ÚHRADĚ" else "FAKTURA - DAŇOVÝ DOKLAD"
    val ucelBadge = if (isPronajem) "PRONÁJEM KONTEJNERU" else "PRODEJ KONTEJNERU"
    val cislo = faktura.cislo.ifBlank { "FV260001" }
    val today = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

    drawStandardDocumentHeader(
        canvas = canvas,
        docTitleLeft = docTitleLeft,
        docTitleRight = "$ucelBadge • č. $cislo",
        docNumberLabel = if (isZalohova) "Zálohová FA č.:" else "Faktura č.:",
        docNumberValue = cislo,
        dateIssued = faktura.datumVystaveni.ifBlank { today },
        extraSubLabel = "Variabilní symbol:",
        extraSubValue = faktura.variabilniSymbol.ifBlank { cislo.filter { it.isDigit() } },
        customerCompany = faktura.firma,
        customerName = faktura.jmeno,
        customerAddress = faktura.ulice,
        customerIc = faktura.ic,
        customerDic = faktura.dic,
        customerPhone = faktura.telefon,
        customerEmail = faktura.email
    )

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.BLACK; textSize = 9f }
    val paintBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 9.5f
    }
    val paintBlueBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#000080")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 9.5f
    }

    // Payment & dates bar (y = 220..268)
    paint.style = Paint.Style.STROKE
    canvas.drawRect(30f, 220f, 565f, 268f, paint)
    canvas.drawLine(297f, 220f, 297f, 268f, paint)
    paint.style = Paint.Style.FILL

    canvas.drawText("Bankovní spojení / Účet: ${faktura.cisloUctu}", 36f, 234f, paintBold)
    canvas.drawText("IBAN: ${faktura.iban}", 36f, 247f, paint)
    canvas.drawText("Forma úhrady: ${faktura.formaUhrady}   |   Středisko: ${faktura.depo} (${faktura.stredisko})", 36f, 260f, paint)

    canvas.drawText("Datum vystavení: ${faktura.datumVystaveni.ifBlank { today }}", 304f, 234f, paint)
    if (!isZalohova) {
        canvas.drawText("Datum zdan. plnění (DUZP): ${faktura.datumZdanitelnehoPlneni.ifBlank { faktura.datumVystaveni.ifBlank { today } }}", 304f, 247f, paint)
    } else {
        canvas.drawText("Doklad není daňovým dokladem (zálohová faktura)", 304f, 247f, paintBlueBold)
    }
    canvas.drawText("Datum splatnosti: ${faktura.datumSplatnosti.ifBlank { today }}", 304f, 260f, paintBold)

    // Container / Rental / Sale specification header
    var y = 278f
    val shadePaint = Paint().apply { color = AndroidColor.parseColor("#E2E8F0"); style = Paint.Style.FILL }
    canvas.drawRect(30f, y, 565f, y + 18f, shadePaint)
    paintBold.textSize = 10f
    val specHeader = if (isPronajem) {
        "Specifikace pronájmu kontejneru ${faktura.oznaceniKontejneru} ${if (faktura.obdobiPronajmu.isNotBlank()) "• Období: ${faktura.obdobiPronajmu}" else ""}:"
    } else {
        "Specifikace prodaného a předaného kontejneru ${faktura.oznaceniKontejneru}:"
    }
    canvas.drawText(specHeader, 35f, y + 13f, paintBold)
    y += 28f

    if (faktura.text.isNotBlank()) {
        canvas.drawText(faktura.text.take(90), 35f, y, paintBlueBold)
        y += 15f
    }
    if (faktura.mistoDodani.isNotBlank()) {
        canvas.drawText("Místo dodání / stavby: ${faktura.mistoDodani}", 35f, y, paint)
        y += 14f
    }

    // Table header
    canvas.drawRect(30f, y, 565f, y + 16f, shadePaint)
    paintBold.textSize = 8.5f
    canvas.drawText("Označení / Položka", 35f, y + 11f, paintBold)
    canvas.drawText("Počet", 290f, y + 11f, paintBold)
    canvas.drawText("Cena/MJ", 340f, y + 11f, paintBold)
    canvas.drawText("DPH %", 405f, y + 11f, paintBold)
    canvas.drawText("Bez DPH", 450f, y + 11f, paintBold)
    canvas.drawText("Celkem s DPH", 505f, y + 11f, paintBold)
    y += 22f

    var sumBez = 0.0
    var sumDph = 0.0
    val itemsToDraw = if (faktura.items.isNotEmpty()) {
        faktura.items
    } else {
        listOf(
            NabidkaItemRecord(
                kod = if (isPronajem) "PRON" else "PRODEJ",
                text = if (isPronajem) "Pronájem kontejneru ${faktura.typKontejneru} ${faktura.oznaceniKontejneru}" else "Prodej kontejneru ${faktura.typKontejneru} ${faktura.oznaceniKontejneru}",
                mnozstvi = 1.0,
                mj = if (isPronajem) "měs." else "ks",
                jCena = 0.0,
                dphPct = 21.0
            )
        )
    }

    paint.textSize = 9f
    itemsToDraw.take(10).forEach { item ->
        val bez = if (item.castkaBez != 0.0) item.castkaBez else (item.mnozstvi * item.jCena * (1.0 - item.sleva / 100.0))
        val dph = if (item.pdp) 0.0 else if (item.dphVal != 0.0) item.dphVal else (bez * (item.dphPct / 100.0))
        val celkem = bez + dph
        sumBez += bez
        sumDph += dph

        val label = "${item.kod.ifBlank { "•" }}  ${item.text}".take(46)
        canvas.drawText(label, 35f, y, paintBold)
        canvas.drawText("${item.mnozstvi.toInt()} ${item.mj}", 290f, y, paint)
        canvas.drawText(formatCzCurrency(item.jCena), 340f, y, paint)
        canvas.drawText(if (item.pdp) "PDP 0%" else "${item.dphPct.toInt()}%", 405f, y, paint)
        canvas.drawText(formatCzCurrency(bez), 450f, y, paint)
        canvas.drawText(formatCzCurrency(celkem), 505f, y, paintBold)
        y += 16f
    }

    paint.style = Paint.Style.STROKE
    canvas.drawLine(30f, y, 565f, y, paint)
    paint.style = Paint.Style.FILL
    y += 16f

    // Totals box on the right
    val celkemKuchrade = sumBez + sumDph
    canvas.drawRect(330f, y, 565f, y + 54f, shadePaint)
    paint.style = Paint.Style.STROKE
    canvas.drawRect(330f, y, 565f, y + 54f, paint)
    paint.style = Paint.Style.FILL

    canvas.drawText("Základ daně (bez DPH):", 338f, y + 15f, paint)
    canvas.drawText(formatCzCurrency(sumBez), 485f, y + 15f, paintBold)
    canvas.drawText("DPH (21 %):", 338f, y + 30f, paint)
    canvas.drawText(formatCzCurrency(sumDph), 485f, y + 30f, paintBold)
    paintBlueBold.textSize = 11.5f
    canvas.drawText("CELKEM K ÚHRADĚ:", 338f, y + 47f, paintBlueBold)
    canvas.drawText(formatCzCurrency(celkemKuchrade), 475f, y + 47f, paintBlueBold)

    // Handover / Sale / Rental clauses (from Fa_vzor & Předávací protokol)
    var cy = y + 72f
    paintBlueBold.textSize = 9f
    if (isPronajem) {
        canvas.drawText("1. Nájemce potvrzuje převzetí kontejneru(ů) v řádném a provozuschopném stavu.", 35f, cy, paintBlueBold)
        cy += 13f
        canvas.drawText("2. Po dobu pronájmu nese nájemce odpovědnost za poškození, ztrátu a dodržování podmínek nájmu.", 35f, cy, paintBlueBold)
        cy += 13f
        canvas.drawText("3. Faktura slouží současně jako vyúčtování pronájmu a související přepravy / manipulace.", 35f, cy, paintBlueBold)
    } else {
        canvas.drawText("1. Kupující prohlašuje, že si kontejner(y) před úhradou této faktury řádně prohlédl.", 35f, cy, paintBlueBold)
        cy += 13f
        canvas.drawText("2. Zkontroloval jeho stav osobně nebo vlastními odborníky a je s jeho stavem a opotřebením srozuměn.", 35f, cy, paintBlueBold)
        cy += 13f
        canvas.drawText("3. Kontejner v tomto stavu jak leží a běží kupuje bez výhrad. Předáním přechází nebezpečí škody na kupujícího.", 35f, cy, paintBlueBold)
    }

    if (faktura.poznamka.isNotBlank()) {
        cy += 18f
        canvas.drawText("Poznámka: ${faktura.poznamka.take(90)}", 35f, cy, paint)
    }

    drawStandardDocumentFooter(
        canvas = canvas,
        leftSignTitle = "Převzal / Odběratel podpis:",
        rightSignTitle = "Vystavil / Předal podpis:"
    )

    document.finishPage(page)
    val safeNum = cislo.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val prefix = if (isZalohova) "Zalohova_faktura" else "Faktura"
    val file = File(context.cacheDir, "${prefix}_${safeNum}.pdf")
    FileOutputStream(file).use { document.writeTo(it) }
    document.close()
    return file
}

fun generateContainerCardPdf(context: Context, container: ContainerCard): File {
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = document.startPage(pageInfo)
    val canvas = page.canvas

    val cisloDoc = container.timestamp.toString().takeLast(8)
    val vystaveno = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

    drawStandardDocumentHeader(
        canvas = canvas,
        docTitleLeft = "PŘEDÁVACÍ PROTOKOL / KARTA",
        docTitleRight = "KONTEJNER ${container.designation.ifBlank { container.id }}",
        docNumberLabel = "DODACÍ LIST č.:",
        docNumberValue = cisloDoc,
        dateIssued = vystaveno,
        extraSubLabel = "Středisko / Stav:",
        extraSubValue = "${container.depo} • ${container.status}",
        customerCompany = container.customerCompany.ifBlank { container.soldCustomerName }.ifBlank { container.reservedName },
        customerName = container.reservedName.ifBlank { container.soldCustomerName },
        customerAddress = container.soldDelivery,
        customerIc = container.soldCustomerId,
        customerDic = "",
        customerPhone = container.soldPhone.ifBlank { container.reservedPhone },
        customerEmail = container.soldEmail.ifBlank { container.reservedEmail }
    )

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.BLACK; textSize = 10f }
    val paintBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 11f
    }
    val paintBlueBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#000080")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 10f
    }

    val shadePaint = Paint().apply { color = AndroidColor.parseColor("#F0F0F0"); style = Paint.Style.FILL }
    canvas.drawRect(30f, 222f, 565f, 240f, shadePaint)
    canvas.drawText("Specifikace kontejneru a předávací protokol:", 35f, 235f, paintBold)

    var y = 258f
    canvas.drawText("Označení kontejneru: ${container.designation}   |   Typ: ${container.type}   |   Barva: ${container.color.ifBlank { "standard" }}", 35f, y, paintBold)
    y += 16f
    canvas.drawText("Cena bez DPH: ${formatPrice(container.price)}   |   Vlastník: ${container.vlastnik}   |   SPZ přepravy: ${container.spz.ifBlank { "-" }}", 35f, y, paint)
    if (container.description.isNotBlank() || container.poznamka.isNotBlank()) {
        y += 15f
        canvas.drawText("Popis / Poznámka: ${(container.description + " " + container.poznamka).trim().take(85)}", 35f, y, paint)
    }

    // Draw container photo if available
    val bmp = decodeBitmapFromAnyUri(primaryContainerPhoto(container))
    if (bmp != null) {
        y += 12f
        val dst = RectF(35f, y, 265f, y + 135f)
        canvas.drawBitmap(bmp, null, dst, null)
        // Draw legal clauses next to the photo
        var ty = y + 14f
        paintBlueBold.textSize = 8.5f
        canvas.drawText("1. Kupující/Nájemce kontejner řádně prohlédl.", 275f, ty, paintBlueBold)
        ty += 14f
        canvas.drawText("2. Je s technickým stavem a opotřebením srozuměn.", 275f, ty, paintBlueBold)
        ty += 14f
        canvas.drawText("3. Kontejner přebírá bez výhrad.", 275f, ty, paintBlueBold)
        ty += 14f
        canvas.drawText("4. Předáním přechází odpovědnost na přebírajícího.", 275f, ty, paintBlueBold)
        y += 148f
    } else {
        y += 22f
        canvas.drawText("1. Kupující prohlašuje, že si kontejner(y) před úhradou řádně prohlédl.", 35f, y, paintBlueBold)
        y += 14f
        canvas.drawText("2. Zkontroloval jeho stav osobně nebo vlastními odborníky a je s jeho stavem a opotřebením srozuměn.", 35f, y, paintBlueBold)
        y += 14f
        canvas.drawText("3. Kontejner v tomto stavu jak leží a běží kupuje/přebírá bez výhrad.", 35f, y, paintBlueBold)
        y += 22f
    }

    val containerInitials = com.example.data.computeCustomerInitials(
        container.reservedName.ifBlank { container.soldCustomerName },
        container.customerCompany
    )
    paintBold.textSize = 12f
    canvas.drawText("Osobně zastupuji odběratele a potvrzuji převzetí kontejneru(ů):", 35f, y, paintBold)
    y += 20f
    paintBold.textSize = 10.5f
    canvas.drawText("Převzal - hůlkově:  ${container.reservedName.ifBlank { container.soldCustomerName }.ifBlank { "........................................................................" }}", 35f, y, paintBold)
    y += 18f
    canvas.drawText("Iniciály zákazníka: ${containerInitials.ifBlank { "........................................................................" }}", 35f, y, paintBlueBold)
    y += 18f
    canvas.drawText("Společnost / Adresa: ${container.customerCompany.ifBlank { "........................................................................" }}", 35f, y, paintBold)
    y += 18f
    canvas.drawText("Číslo OP / Telefon:   ${container.soldPhone.ifBlank { container.reservedPhone }.ifBlank { "........................................................................" }}", 35f, y, paintBold)
    y += 18f
    canvas.drawText("Místo předání:        ${container.soldDelivery.ifBlank { container.depo }}", 35f, y, paintBold)

    drawStandardDocumentFooter(
        canvas = canvas,
        leftSignSubtext = if (containerInitials.isNotBlank()) "Iniciály: $containerInitials" else ""
    )
    document.finishPage(page)

    val safeDes = container.designation.ifBlank { "kontejner" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val file = File(context.cacheDir, "Karta_Protokol_${safeDes}.pdf")
    FileOutputStream(file).use { document.writeTo(it) }
    document.close()
    return file
}

fun generateNabidkaPdf(context: Context, nabidka: NabidkaRecord): File {
    val asFaktura = FakturaRecord(
        cislo = nabidka.cislo.ifBlank { "CN" },
        typDokladu = "FAKTURA",
        ucelFaktury = "PRODEJ",
        depo = nabidka.depo,
        datumVystaveni = nabidka.datum,
        datumSplatnosti = nabidka.platnost,
        ic = nabidka.ic,
        dic = nabidka.dic,
        firma = nabidka.firma,
        jmeno = nabidka.jmeno,
        ulice = nabidka.ulice,
        telefon = nabidka.telefon,
        email = nabidka.email,
        text = nabidka.text,
        items = nabidka.items
    )
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = document.startPage(pageInfo)
    val canvas = page.canvas
    val today = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

    drawStandardDocumentHeader(
        canvas = canvas,
        docTitleLeft = "CENOVÁ NABÍDKA (CN)",
        docTitleRight = "CENOVÁ NABÍDKA KONTEJNERU",
        docNumberLabel = "Nabídka č.:",
        docNumberValue = asFaktura.cislo,
        dateIssued = nabidka.datum.ifBlank { today },
        extraSubLabel = "Platnost do:",
        extraSubValue = nabidka.platnost.ifBlank { "14 dní" },
        customerCompany = nabidka.firma,
        customerName = nabidka.jmeno,
        customerAddress = nabidka.ulice,
        customerIc = nabidka.ic,
        customerDic = nabidka.dic,
        customerPhone = nabidka.telefon,
        customerEmail = nabidka.email
    )

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.BLACK; textSize = 9.5f }
    val paintBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 10f
    }
    val paintBlueBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#000080")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 10.5f
    }
    val shadePaint = Paint().apply { color = AndroidColor.parseColor("#E2E8F0"); style = Paint.Style.FILL }

    var y = 224f
    canvas.drawRect(30f, y, 565f, y + 18f, shadePaint)
    canvas.drawText("Specifikace nabízených kontejnerů, vybavení a služeb:", 35f, y + 13f, paintBold)
    y += 28f

    if (nabidka.text.isNotBlank()) {
        canvas.drawText("Předmět nabídky: ${nabidka.text.take(80)}", 35f, y, paintBlueBold)
        y += 18f
    }

    var sumBez = 0.0
    var sumDph = 0.0
    nabidka.items.take(12).forEach { item ->
        val bez = if (item.castkaBez != 0.0) item.castkaBez else (item.mnozstvi * item.jCena * (1.0 - item.sleva / 100.0))
        val dph = if (item.pdp) 0.0 else if (item.dphVal != 0.0) item.dphVal else (bez * (item.dphPct / 100.0))
        sumBez += bez
        sumDph += dph
        canvas.drawText("${item.kod}  ${item.text}".take(48), 35f, y, paintBold)
        canvas.drawText("${item.mnozstvi.toInt()} ${item.mj} × ${formatCzCurrency(item.jCena)}", 320f, y, paint)
        canvas.drawText(formatCzCurrency(bez + dph), 480f, y, paintBold)
        y += 16f
    }

    y += 10f
    canvas.drawRect(320f, y, 565f, y + 50f, shadePaint)
    canvas.drawText("Celkem bez DPH: ${formatCzCurrency(sumBez)}", 330f, y + 16f, paintBold)
    canvas.drawText("DPH: ${formatCzCurrency(sumDph)}", 330f, y + 31f, paint)
    canvas.drawText("CELKEM S DPH: ${formatCzCurrency(sumBez + sumDph)}", 330f, y + 45f, paintBlueBold)

    drawStandardDocumentFooter(canvas, "Akceptoval (Odběratel):", "Vypracoval (Dodavatel):")
    document.finishPage(page)
    val safeNum = asFaktura.cislo.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val file = File(context.cacheDir, "Cenova_nabidka_${safeNum}.pdf")
    FileOutputStream(file).use { document.writeTo(it) }
    document.close()
    return file
}

fun generateZakazkaPdf(context: Context, zakazka: ZakazkaRecord): File {
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = document.startPage(pageInfo)
    val canvas = page.canvas
    val today = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
    val cislo = zakazka.cisloZakazky.ifBlank { "ZAK" }

    drawStandardDocumentHeader(
        canvas = canvas,
        docTitleLeft = "ZAKÁZKOVÝ LIST / PROTOKOL",
        docTitleRight = "ZAKÁZKA č. $cislo (${zakazka.stredisko.ifBlank { "Pronájem" }})",
        docNumberLabel = "Zakázka č.:",
        docNumberValue = cislo,
        dateIssued = today,
        extraSubLabel = "Lokalita / Přeprava:",
        extraSubValue = "${zakazka.lokalita} ${zakazka.prepravaInfo}".trim(),
        customerCompany = zakazka.firma,
        customerName = zakazka.vyrizuje,
        customerAddress = zakazka.ulice.ifBlank { zakazka.lokalita },
        customerIc = zakazka.ic,
        customerDic = zakazka.dic,
        customerPhone = zakazka.telefon,
        customerEmail = zakazka.email
    )

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.BLACK; textSize = 9.5f }
    val paintBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 10f
    }
    val paintBlueBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#000080")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 10.5f
    }
    val shadePaint = Paint().apply { color = AndroidColor.parseColor("#E2E8F0"); style = Paint.Style.FILL }

    var y = 224f
    canvas.drawRect(30f, y, 565f, y + 18f, shadePaint)
    canvas.drawText("Položky zakázky • Středisko: ${zakazka.stredisko.ifBlank { "Pronájem" }} (${zakazka.depo}):", 35f, y + 13f, paintBold)
    y += 28f

    if (zakazka.prepravaInfo.isNotBlank()) {
        canvas.drawText("Přeprava: ${zakazka.prepravaInfo}", 35f, y, paintBlueBold)
        y += 18f
    }

    var sumBez = 0.0
    var sumDph = 0.0
    zakazka.items.take(12).forEach { item ->
        val bez = if (item.castkaBez != 0.0) item.castkaBez else (item.mnozstvi * item.jCena * (1.0 - item.sleva / 100.0))
        val dph = if (item.pdp) 0.0 else if (item.dphVal != 0.0) item.dphVal else (bez * (item.dphPct / 100.0))
        sumBez += bez
        sumDph += dph
        canvas.drawText("${item.kod}  ${item.text} [${item.stav}]".take(48), 35f, y, paintBold)
        canvas.drawText("${item.mnozstvi.toInt()} ${item.mj} × ${formatCzCurrency(item.jCena)}", 320f, y, paint)
        canvas.drawText(formatCzCurrency(bez + dph), 480f, y, paintBold)
        y += 16f
    }

    y += 10f
    canvas.drawRect(320f, y, 565f, y + 50f, shadePaint)
    canvas.drawText("Celkem bez DPH: ${formatCzCurrency(sumBez)}", 330f, y + 16f, paintBold)
    canvas.drawText("DPH: ${formatCzCurrency(sumDph)}", 330f, y + 31f, paint)
    canvas.drawText("CELKEM S DPH: ${formatCzCurrency(sumBez + sumDph)}", 330f, y + 45f, paintBlueBold)

    drawStandardDocumentFooter(canvas, "Převzal zákazník:", "Předal za Kontejnery Ostrava:")
    document.finishPage(page)
    val safeNum = cislo.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val file = File(context.cacheDir, "Zakazka_${safeNum}.pdf")
    FileOutputStream(file).use { document.writeTo(it) }
    document.close()
    return file
}

fun generateCustomerCardPdf(context: Context, customer: CustomerRecord, events: List<CustomerEventRecord> = emptyList()): File {
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = document.startPage(pageInfo)
    val canvas = page.canvas
    val today = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
    val cislo = customer.cisloZakaznika.ifBlank { customer.id.takeLast(6) }

    drawStandardDocumentHeader(
        canvas = canvas,
        docTitleLeft = if (customer.isOrder && !customer.isAdresar) "KARTA OBJEDNÁVKY / POPTÁVKY" else "KARTA ZÁKAZNÍKA (ADRESÁŘ)",
        docTitleRight = "ZÁKAZNÍK č. $cislo • BONITA ${customer.bonita.ifBlank { "AAA" }}",
        docNumberLabel = "Zákazník č.:",
        docNumberValue = cislo,
        dateIssued = today,
        extraSubLabel = "Bonita firmy:",
        extraSubValue = "${customer.bonita.ifBlank { "AAA" }} (${customer.bonitaDetail.take(28)})",
        customerCompany = customer.company,
        customerName = customer.name,
        customerAddress = customer.ulice,
        customerIc = customer.ic,
        customerDic = customer.dic,
        customerPhone = customer.phone,
        customerEmail = customer.email
    )

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.BLACK; textSize = 9.5f }
    val paintBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 10f
    }
    val shadePaint = Paint().apply { color = AndroidColor.parseColor("#E2E8F0"); style = Paint.Style.FILL }

    var y = 226f
    canvas.drawRect(30f, y, 565f, y + 18f, shadePaint)
    canvas.drawText("Detail zákazníka, bonita a přehled událostí (angažmá):", 35f, y + 13f, paintBold)
    y += 28f

    canvas.drawText("Bonita: ${customer.bonita.ifBlank { "AAA" }} — ${customer.bonitaDetail}", 35f, y, paintBold)
    y += 16f
    if (customer.inquiryType.isNotBlank() || customer.message.isNotBlank()) {
        canvas.drawText("Poptávka / Poznámka: ${customer.inquiryType} ${customer.message}".take(85), 35f, y, paint)
        y += 18f
    }

    val allEv = if (events.isNotEmpty()) events else customer.udalosti
    if (allEv.isNotEmpty()) {
        canvas.drawText("Historie angažmá firmy (${allEv.size} záznamů):", 35f, y, paintBold)
        y += 16f
        allEv.take(12).forEach { ev ->
            canvas.drawText("• [${ev.typ}] ${ev.datum} ${ev.cisloDokladu} — ${ev.popis}".take(72), 35f, y, paint)
            if (ev.castka > 0) {
                canvas.drawText(formatCzCurrency(ev.castka), 485f, y, paintBold)
            }
            y += 14f
        }
    }

    if (customer.photos.isNotEmpty()) {
        y += 8f
        canvas.drawText("Přiložené fotografie zákazníka (${customer.photos.size}):", 35f, y, paintBold)
        y += 8f
        var photoX = 35f
        customer.photos.take(3).forEach { photoUri ->
            try {
                val bmp = decodeBitmapFromAnyUri(photoUri)
                if (bmp != null) {
                    canvas.drawBitmap(bmp, null, android.graphics.RectF(photoX, y, photoX + 90f, y + 68f), null)
                    photoX += 100f
                }
            } catch (_: Exception) {}
        }
        y += 75f
    }

    drawStandardDocumentFooter(canvas)
    document.finishPage(page)
    val safeName = customer.company.ifBlank { customer.name }.ifBlank { "zakaznik" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val file = File(context.cacheDir, "Karta_Zakaznika_${safeName}.pdf")
    FileOutputStream(file).use { document.writeTo(it) }
    document.close()
    return file
}

fun generateTransportCardPdf(context: Context, transport: TransportRecord): File {
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = document.startPage(pageInfo)
    val canvas = page.canvas
    val today = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

    val custCompany = transport.customerCompany.ifBlank { transport.company }
    val custName = transport.customerName
    val initials = transport.customerInitials.ifBlank {
        com.example.data.computeCustomerInitials(custName, custCompany)
    }
    val custAddress = transport.customerAddress.ifBlank { transport.depo }
    val headerCustomerName = buildString {
        if (custName.isNotBlank()) append(custName)
        if (initials.isNotBlank()) {
            if (isNotEmpty()) append(" • ")
            append("Iniciály: $initials")
        }
    }.ifBlank { transport.driver }

    drawStandardDocumentHeader(
        canvas = canvas,
        docTitleLeft = if (transport.isVraceni) "PŘEDÁVACÍ PROTOKOL VRÁCENÍ" else "PŘEDÁVACÍ PROTOKOL PŘEPRAVY",
        docTitleRight = "KONTEJNER ${transport.designation.ifBlank { transport.spz }}",
        docNumberLabel = "Přeprava ze dne:",
        docNumberValue = transport.dateText.ifBlank { today },
        dateIssued = today,
        extraSubLabel = "SPZ / Řidič:",
        extraSubValue = "${transport.spz.ifBlank { "-" }} • ${transport.driver.ifBlank { "-" }}",
        customerCompany = custCompany,
        customerName = headerCustomerName,
        customerAddress = custAddress,
        customerIc = transport.customerIc,
        customerDic = transport.customerDic,
        customerPhone = transport.customerPhone,
        customerEmail = transport.customerEmail
    )

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.BLACK; textSize = 10f }
    val paintBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 10.5f
    }
    val paintBlueBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#000080")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 10f
    }
    val shadePaint = Paint().apply { color = AndroidColor.parseColor("#E2E8F0"); style = Paint.Style.FILL }

    var y = 224f
    canvas.drawRect(30f, y, 565f, y + 18f, shadePaint)
    canvas.drawText("Specifikace přepravy kontejneru a odběratele:", 35f, y + 13f, paintBold)
    y += 26f
    canvas.drawText("Kontejner / Označení: ${transport.designation.ifBlank { "-" }}   |   Typ: ${transport.containerType.ifBlank { "-" }}   |   Stav: ${transport.status}", 35f, y, paintBold)
    y += 16f
    canvas.drawText("Zákazník / Firma: ${custCompany.ifBlank { "-" }}   |   IČ: ${transport.customerIc.ifBlank { "-" }}   |   DIČ: ${transport.customerDic.ifBlank { "-" }}", 35f, y, paint)
    y += 16f
    canvas.drawText("Přebírající osoba: ${custName.ifBlank { "-" }}   |   INICIÁLY ZÁKAZNÍKA: ${initials.ifBlank { "-" }}", 35f, y, paintBlueBold)
    y += 16f
    canvas.drawText("Místo vykládky / Adresa: ${custAddress.ifBlank { "-" }}   |   Tel.: ${transport.customerPhone.ifBlank { "-" }}", 35f, y, paint)
    y += 16f
    canvas.drawText("Vozidlo SPZ: ${transport.spz.ifBlank { "-" }}   |   Řidič: ${transport.driver.ifBlank { "-" }}   |   Středisko: ${transport.depo}", 35f, y, paint)
    if (transport.cisloDokladuZdroj.isNotBlank() || transport.customerSource.isNotBlank()) {
        y += 15f
        canvas.drawText("Zdroj zákazníka: ${transport.customerSource.ifBlank { "Ručně" }} ${if (transport.cisloDokladuZdroj.isNotBlank()) "(${transport.cisloDokladuZdroj})" else ""}", 35f, y, paint)
    }
    if (transport.note.isNotBlank()) {
        y += 15f
        canvas.drawText("Poznámka / Trasa: ${transport.note.take(85)}", 35f, y, paint)
    }

    val photoUriToDraw = transport.containerImageUri?.takeIf { it.isNotBlank() } ?: transport.imageUri
    val bmp = decodeBitmapFromAnyUri(photoUriToDraw)
    if (bmp != null) {
        y += 10f
        val dst = RectF(35f, y, 250f, y + 120f)
        canvas.drawBitmap(bmp, null, dst, null)
        var ty = y + 14f
        paintBlueBold.textSize = 8.5f
        canvas.drawText("1. Odběratel/Zákazník kontejner při převzetí řádně prohlédl.", 260f, ty, paintBlueBold)
        ty += 14f
        canvas.drawText("2. Je s technickým stavem a umístěním srozuměn.", 260f, ty, paintBlueBold)
        ty += 14f
        canvas.drawText("3. Kontejner přebírá bez výhrad (potvrzeno iniciály).", 260f, ty, paintBlueBold)
        ty += 14f
        canvas.drawText("4. Předáním přechází odpovědnost na přebírajícího.", 260f, ty, paintBlueBold)
        y += 132f
    } else {
        y += 18f
        paintBlueBold.textSize = 9f
        canvas.drawText("1. Odběratel/Zákazník potvrzuje řádné převzetí a složení kontejneru bez výhrad.", 35f, y, paintBlueBold)
        y += 14f
        canvas.drawText("2. Zkontroloval stav kontejneru a převzetí stvrzuje svými iniciály a podpisem níže.", 35f, y, paintBlueBold)
        y += 18f
    }

    paintBold.textSize = 11.5f
    canvas.drawText("Předávací protokol — Potvrzení převzetí a iniciály zákazníka:", 35f, y, paintBold)
    y += 19f
    paintBold.textSize = 10f
    canvas.drawText("Převzal (Jméno / Firma): ${custName.ifBlank { custCompany }.ifBlank { "........................................................................" }}", 35f, y, paintBold)
    y += 18f
    paintBlueBold.textSize = 10.5f
    canvas.drawText("INICIÁLY ZÁKAZNÍKA DO PROTOKOLU:   ${initials.ifBlank { "........................................................" }}", 35f, y, paintBlueBold)
    y += 18f
    canvas.drawText("Společnost / IČ:         ${listOf(custCompany, if (transport.customerIc.isNotBlank()) "IČ: ${transport.customerIc}" else "").filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "........................................................................" }}", 35f, y, paintBold)
    y += 18f
    canvas.drawText("Místo složení / Tel.:    ${listOf(custAddress, transport.customerPhone).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "........................................................................" }}", 35f, y, paintBold)

    val footerSub = buildString {
        if (initials.isNotBlank()) append("Iniciály: $initials")
        val who = custName.ifBlank { custCompany }
        if (who.isNotBlank()) {
            if (isNotEmpty()) append(" • ")
            append(who)
        }
    }
    drawStandardDocumentFooter(
        canvas = canvas,
        leftSignTitle = "Převzal (Zákazník / Odběratel):",
        rightSignTitle = "Předal (Kontejnery Ostrava / Řidič):",
        leftSignSubtext = footerSub
    )
    document.finishPage(page)
    val safeDes = transport.designation.ifBlank { "preprava" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val file = File(context.cacheDir, "Karta_Prepravy_${safeDes}.pdf")
    FileOutputStream(file).use { document.writeTo(it) }
    document.close()
    return file
}

fun generateCatalogItemPdf(context: Context, item: CatalogItemRecord): File {
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = document.startPage(pageInfo)
    val canvas = page.canvas
    val today = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

    drawStandardDocumentHeader(
        canvas = canvas,
        docTitleLeft = "KATALOGOVÁ KARTA POLOŽKY",
        docTitleRight = "${item.sekce} • ${item.oznaceni}",
        docNumberLabel = "Kód položky:",
        docNumberValue = item.oznaceni,
        dateIssued = today,
        extraSubLabel = "Sekce:",
        extraSubValue = item.kategorie,
        customerCompany = item.nazev,
        customerName = "Katalogová položka",
        customerAddress = "Sekce: ${item.kategorie} (${item.sekce})",
        customerIc = "",
        customerDic = "",
        customerPhone = "",
        customerEmail = ""
    )

    val paintBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 11f
    }
    val shadePaint = Paint().apply { color = AndroidColor.parseColor("#E2E8F0"); style = Paint.Style.FILL }
    var y = 228f
    canvas.drawRect(30f, y, 565f, y + 18f, shadePaint)
    canvas.drawText("Cenová a technická specifikace položky:", 35f, y + 13f, paintBold)
    y += 30f
    canvas.drawText("Název položky: ${item.nazev}", 35f, y, paintBold)
    y += 20f
    canvas.drawText("Kategorie / Sekce: ${item.kategorie} (${item.sekce})", 35f, y, paintBold)
    y += 20f
    canvas.drawText("Cena bez DPH: ${formatCzCurrency(item.cenaBezDph)}   |   DPH: ${item.dphPct.toInt()} %   |   Celkem s DPH: ${formatCzCurrency(item.celkemSDph)}", 35f, y, paintBold)

    drawStandardDocumentFooter(canvas)
    document.finishPage(page)
    val file = File(context.cacheDir, "Polozka_${item.oznaceni}.pdf")
    FileOutputStream(file).use { document.writeTo(it) }
    document.close()
    return file
}

// ============================================================================
// 6. SYSTEM PRINT, WHATSAPP SHARE & EMAIL SEND DISPATCHERS
// ============================================================================

fun printPdfFile(context: Context, pdfFile: File, jobName: String) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Tisková služba není dostupná", Toast.LENGTH_SHORT).show()
            return
        }
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(pdfFile.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    if (destination != null) {
                        FileInputStream(pdfFile).use { input ->
                            FileOutputStream(destination.fileDescriptor).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage)
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    } catch (e: Exception) {
        Toast.makeText(context, "Chyba tisku: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun sharePdfViaWhatsApp(context: Context, pdfFile: File, summaryText: String, phone: String = "") {
    val fullText = buildString {
        appendLine(summaryText)
        appendLine()
        appendLine("— Kontejnery Ostrava s.r.o.")
        appendLine("📍 Pavlovova 2701/50, 700 30 Ostrava - Jih")
        appendLine("📞 +420 733 609 813 | +420 608 813 484")
        appendLine("✉️ info@kontejnery-ostrava.cz | 🌐 www.kontejnery-ostrava.cz")
        append("Pohodové zázemí za zvýhodněné ceny !!!")
    }
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", pdfFile)
        val waIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, fullText)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (waIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(waIntent)
            return
        }
        // Fallback: open chooser with PDF + text
        val chooserIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, fullText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(chooserIntent, "Sdílet kartu přes WhatsApp"))
    } catch (e: Exception) {
        val cleanPhone = phone.replace(Regex("[^0-9+]"), "")
        val waUrl = "https://wa.me/${cleanPhone.removePrefix("+")}?text=${Uri.encode(fullText)}"
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)))
        } catch (_: Exception) {
            Toast.makeText(context, "Nelze otevřít WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }
}

fun sharePdfViaEmail(
    context: Context,
    pdfFile: File,
    recipientEmail: String = "",
    subject: String = "",
    bodyIntro: String = "",
    body: String = ""
) {
    val effectiveBody = bodyIntro.ifBlank { body }
    val emailBodyWithPaticka = buildString {
        appendLine("Dobrý den,")
        appendLine()
        appendLine(effectiveBody)
        appendLine("V příloze Vám zasíláme tiskový dokument v PDF (včetně předávacího protokolu / specifikace).")
        appendLine()
        appendLine("S přáním pěkného dne,")
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLine("KONTEJNERY OSTRAVA s.r.o.")
        appendLine("PRODEJ | PRONÁJEM | PŘEPRAVA KONTEJNERŮ")
        appendLine("Pavlovova 2701/50, 700 30 Ostrava - Jih")
        appendLine("IČ: 27771164 | DIČ: CZ27771164")
        appendLine("Tel.: +420 733 609 813 | Mobil: +420 608 813 484")
        appendLine("E-mail: info@kontejnery-ostrava.cz")
        appendLine("Web: www.kontejnery-ostrava.cz")
        appendLine("Pohodové zázemí za zvýhodněné ceny !!!")
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", pdfFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            if (recipientEmail.isNotBlank()) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail.trim()))
            }
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, emailBodyWithPaticka)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Odeslat kartu e-mailem"))
    } catch (e: Exception) {
        Toast.makeText(context, "Nelze otevřít e-mailového klienta", Toast.LENGTH_SHORT).show()
    }
}

// ============================================================================
// 7. STORMWARE POHODA XML v2.0 GENERATOR FOR INVOICES & ADVANCE INVOICES
// ============================================================================

private fun escXmlStr(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun toIsoDate(czOrIso: String): String {
    val clean = czOrIso.trim()
    if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(clean)) return clean
    val m = Regex("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})$").find(clean)
    if (m != null) {
        val d = m.groupValues[1].padStart(2, '0')
        val mo = m.groupValues[2].padStart(2, '0')
        val y = m.groupValues[3]
        return "$y-$mo-$d"
    }
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}

fun buildPohodaInvoicesXml(list: List<FakturaRecord>, ico: String = "25388720"): String {
    val cleanIco = ico.ifBlank { "25388720" }
    val packItems = list.mapIndexed { index, f ->
        val isZalohova = f.typDokladu == "ZALOHOVA_FAKTURA"
        val invType = if (isZalohova) "issuedAdvanceInvoice" else "issuedInvoice"
        val dateIso = toIsoDate(f.datumVystaveni)
        val dateTaxIso = toIsoDate(f.datumZdanitelnehoPlneni.ifBlank { f.datumVystaveni })
        val dateDueIso = toIsoDate(f.datumSplatnosti.ifBlank { f.datumVystaveni })
        val symVar = f.variabilniSymbol.ifBlank { f.cislo.filter { it.isDigit() } }.ifBlank { "${index + 1}" }
        val ucelText = if (f.ucelFaktury == "PRONAJEM") "Pronájem kontejneru ${f.oznaceniKontejneru}" else "Prodej kontejneru ${f.oznaceniKontejneru}"
        val headerText = f.text.ifBlank { ucelText }

        val detailXml = f.items.joinToString("\n") { it ->
            val rateVat = when {
                it.pdp || it.dphPct <= 0.0 -> "none"
                it.dphPct <= 15.0 -> "low"
                else -> "high"
            }
            val bez = if (it.castkaBez != 0.0) it.castkaBez else (it.mnozstvi * it.jCena * (1.0 - it.sleva / 100.0))
            val dph = if (it.pdp) 0.0 else if (it.dphVal != 0.0) it.dphVal else (bez * (it.dphPct / 100.0))
            """        <inv:invoiceItem>
          <inv:text>${escXmlStr(it.text)}</inv:text>
          <inv:quantity>${it.mnozstvi}</inv:quantity>
          <inv:unit>${escXmlStr(it.mj.ifBlank { "ks" })}</inv:unit>
          <inv:payVAT>false</inv:payVAT>
          <inv:rateVAT>$rateVat</inv:rateVAT>
          <inv:discountPercentage>${it.sleva}</inv:discountPercentage>
          <inv:homeCurrency>
            <typ:unitPrice>${it.jCena}</typ:unitPrice>
            <typ:price>$bez</typ:price>
            <typ:priceVAT>$dph</typ:priceVAT>
            <typ:priceSum>${bez + dph}</typ:priceSum>
          </inv:homeCurrency>
          <inv:code>${escXmlStr(it.kod.take(20))}</inv:code>
        </inv:invoiceItem>"""
        }

        """  <dat:dataPackItem id="FA_${escXmlStr(f.cislo.ifBlank { "${index + 1}" })}" version="2.0">
    <inv:invoice version="2.0">
      <inv:invoiceHeader>
        <inv:invoiceType>$invType</inv:invoiceType>
        <inv:number>
          <typ:numberRequested>${escXmlStr(f.cislo)}</typ:numberRequested>
        </inv:number>
        <inv:symVar>${escXmlStr(symVar)}</inv:symVar>
        <inv:date>$dateIso</inv:date>
        ${if (!isZalohova) "<inv:dateTax>$dateTaxIso</inv:dateTax>" else ""}
        <inv:dateDue>$dateDueIso</inv:dateDue>
        <inv:text>${escXmlStr(headerText)}</inv:text>
        <inv:partnerIdentity>
          <typ:address>
            <typ:company>${escXmlStr(f.firma)}</typ:company>
            <typ:name>${escXmlStr(f.jmeno)}</typ:name>
            <typ:street>${escXmlStr(f.ulice)}</typ:street>
            <typ:ico>${escXmlStr(f.ic)}</typ:ico>
            <typ:dic>${escXmlStr(f.dic)}</typ:dic>
            <typ:phone>${escXmlStr(f.telefon)}</typ:phone>
            <typ:email>${escXmlStr(f.email)}</typ:email>
          </typ:address>
        </inv:partnerIdentity>
        <inv:paymentType>
          <typ:paymentType>${if (f.formaUhrady.contains("Hotov", true)) "cash" else "draft"}</typ:paymentType>
        </inv:paymentType>
        <inv:contract>
          <typ:ids>${escXmlStr(f.cisloZakazky.take(12))}</typ:ids>
        </inv:contract>
        <inv:note>${escXmlStr("Kontejner: ${f.oznaceniKontejneru} | Středisko: ${f.depo} (${f.stredisko}) | ${f.poznamka}")}</inv:note>
      </inv:invoiceHeader>
      <inv:invoiceDetail>
$detailXml
      </inv:invoiceDetail>
      <inv:invoiceSummary>
        <inv:roundingDocument>math2one</inv:roundingDocument>
      </inv:invoiceSummary>
    </inv:invoice>
  </dat:dataPackItem>"""
    }.joinToString("\n")

    return """<?xml version="1.0" encoding="UTF-8"?>
<dat:dataPack id="FA_PACK_${System.currentTimeMillis()}" ico="${escXmlStr(cleanIco)}" application="KontejneryOstravaERP" version="2.0" note="Import Faktur a Zalohovych faktur do SW POHODA" xmlns:dat="http://www.stormware.cz/schema/version_2/data.xsd" xmlns:inv="http://www.stormware.cz/schema/version_2/invoice.xsd" xmlns:typ="http://www.stormware.cz/schema/version_2/type.xsd">
$packItems
</dat:dataPack>"""
}
