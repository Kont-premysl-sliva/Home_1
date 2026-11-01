package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import java.io.File
import java.io.FileOutputStream
import androidx.compose.material.icons.automirrored.filled.*

private fun createDefaultEditableBitmap(): Bitmap {
    return decodeBitmapFromAnyUri("data:image/svg+xml;utf8,fallback")
        ?: Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888)
}

private fun ensureSoftwareBitmap(bmp: Bitmap?): Bitmap {
    if (bmp == null || bmp.width <= 1 || bmp.height <= 1) {
        return createDefaultEditableBitmap()
    }
    return try {
        if (bmp.config != Bitmap.Config.ARGB_8888) {
            bmp.copy(Bitmap.Config.ARGB_8888, false) ?: bmp
        } else {
            bmp
        }
    } catch (_: Throwable) {
        bmp
    }
}

private fun decodeEditableBitmap(context: android.content.Context, rawPath: String): Bitmap {
    return try {
        val clean = rawPath.trim()
        if (clean.isBlank() || clean.startsWith("data:image/svg+xml")) {
            return ensureSoftwareBitmap(decodeBitmapFromAnyUri(clean))
        }
        if (clean.startsWith("data:image")) {
            val commaIdx = clean.indexOf(',')
            if (commaIdx > 0) {
                val b64 = clean.substring(commaIdx + 1).trim().removePrefix("file://")
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1280)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ensureSoftwareBitmap(decoded)
            } else {
                createDefaultEditableBitmap()
            }
        } else if (clean.startsWith("content://")) {
            val uri = android.net.Uri.parse(clean)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null && bytes.isNotEmpty()) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1280)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ensureSoftwareBitmap(decoded)
            } else {
                createDefaultEditableBitmap()
            }
        } else {
            val filePath = clean.removePrefix("file://")
            val f = File(filePath)
            if (!f.exists()) {
                return ensureSoftwareBitmap(decodeBitmapFromAnyUri(clean))
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, options)
            options.inSampleSize = maxOf(1, maxOf(options.outWidth, options.outHeight) / 1280)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            val decoded = BitmapFactory.decodeFile(filePath, options)
            if (decoded != null) {
                val rotated = try {
                    val exif = android.media.ExifInterface(filePath)
                    val orientation = exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )
                    val degrees = when (orientation) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                    if (degrees != 0f) {
                        val matrix = Matrix().apply { postRotate(degrees) }
                        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                    } else {
                        decoded
                    }
                } catch (_: Throwable) {
                    decoded
                }
                ensureSoftwareBitmap(rotated)
            } else {
                createDefaultEditableBitmap()
            }
        }
    } catch (_: Throwable) {
        createDefaultEditableBitmap()
    }
}

private fun encodeBitmapToSyncDataUri(bmp: Bitmap): String {
    return try {
        val maxDim = 800
        val ratio = minOf(
            maxDim.toFloat() / bmp.width.coerceAtLeast(1),
            maxDim.toFloat() / bmp.height.coerceAtLeast(1),
            1f
        )
        val scaled = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * ratio).toInt().coerceAtLeast(1),
                (bmp.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else bmp
        val out = java.io.ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
        val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        "data:image/jpeg;base64,$b64"
    } catch (_: Throwable) {
        ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditImageScreen(
    imagePath: String,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onSaveImage: ((String) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.activity.compose.BackHandler {
        onCancel()
    }

    var bitmap by remember(imagePath) {
        mutableStateOf(decodeEditableBitmap(context, imagePath))
    }
    val imageBitmap = remember(bitmap) {
        ensureSoftwareBitmap(bitmap).asImageBitmap()
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(Size.Zero) }
    // 0 = 1:1 Čtverec, 1 = 4:3 Kontejner, 2 = 16:9 Širokoúhlý, 3 = Celý snímek (bez ořezu)
    var cropMode by remember { mutableIntStateOf(1) }
    var showCropMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Úprava fotografie", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            when (cropMode) {
                                0 -> "Ořez 1:1 • Otáčejte o 90° a přibližujte"
                                1 -> "Ořez 4:3 • Otáčejte o 90° a přibližujte"
                                2 -> "Ořez 16:9 • Otáčejte o 90° a přibližujte"
                                else -> "Celý snímek • Bez ořezu okrajů"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Clear, contentDescription = "Zrušit") }
                },
                actions = {
                    Box {
                        TextButton(onClick = { showCropMenu = true }) {
                            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                when (cropMode) {
                                    0 -> "1:1 Čtverec"
                                    1 -> "4:3 Kontejner"
                                    2 -> "16:9 Širokoúhlý"
                                    else -> "Celý snímek"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                        DropdownMenu(
                            expanded = showCropMenu,
                            onDismissRequest = { showCropMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("1:1 Čtverec (Standard)") },
                                onClick = { cropMode = 0; showCropMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("4:3 Kontejner (Tradiční)") },
                                onClick = { cropMode = 1; showCropMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("16:9 Širokoúhlý") },
                                onClick = { cropMode = 2; showCropMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Celý snímek (Bez ořezu okrajů)") },
                                onClick = { cropMode = 3; showCropMenu = false }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val bWidth = bitmap.width.toFloat().coerceAtLeast(1f)
                            val bHeight = bitmap.height.toFloat().coerceAtLeast(1f)
                            val vW = viewSize.width
                            val vH = viewSize.height

                            val newBitmap = try {
                                if (vW <= 10f || vH <= 10f || (cropMode == 3 && scale <= 1.02f && offset == Offset.Zero)) {
                                    bitmap
                                } else {
                                    val (cropWVal, cropHVal) = when (cropMode) {
                                        0 -> {
                                            val s = minOf(vW, vH) * 0.85f
                                            Pair(s, s)
                                        }
                                        1 -> {
                                            var w = vW * 0.90f
                                            var h = w * 0.75f
                                            if (h > vH * 0.70f) {
                                                h = vH * 0.70f
                                                w = h / 0.75f
                                            }
                                            Pair(w, h)
                                        }
                                        2 -> {
                                            var w = vW * 0.92f
                                            var h = w * (9f / 16f)
                                            if (h > vH * 0.70f) {
                                                h = vH * 0.70f
                                                w = h * (16f / 9f)
                                            }
                                            Pair(w, h)
                                        }
                                        else -> {
                                            val ratio = bWidth / bHeight
                                            var w = vW * 0.92f
                                            var h = w / ratio
                                            if (h > vH * 0.75f) {
                                                h = vH * 0.75f
                                                w = h * ratio
                                            }
                                            Pair(w, h)
                                        }
                                    }

                                    val fitScale = minOf(vW / bWidth, vH / bHeight) * 0.90f
                                    val effectiveScale = (fitScale * scale).coerceAtLeast(0.01f)
                                    val scaledW = bWidth * effectiveScale
                                    val scaledH = bHeight * effectiveScale

                                    val left = (vW - cropWVal) / 2f
                                    val top = (vH - cropHVal) / 2f

                                    val imgLeft = (vW - scaledW) / 2f + offset.x
                                    val imgTop = (vH - scaledH) / 2f + offset.y

                                    val startX = ((left - imgLeft) / effectiveScale).toInt().coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
                                    val startY = ((top - imgTop) / effectiveScale).toInt().coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))

                                    val cropW = (cropWVal / effectiveScale).toInt().coerceIn(1, (bitmap.width - startX).coerceAtLeast(1))
                                    val cropH = (cropHVal / effectiveScale).toInt().coerceIn(1, (bitmap.height - startY).coerceAtLeast(1))

                                    Bitmap.createBitmap(bitmap, startX, startY, cropW, cropH)
                                }
                            } catch (_: Throwable) {
                                bitmap
                            }

                            val savedUri: String = try {
                                if (!imagePath.startsWith("data:image")) {
                                    val outF = File(context.filesDir, "img_${System.currentTimeMillis()}.jpg")
                                    FileOutputStream(outF).use { out ->
                                        newBitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                                    }
                                    outF.absolutePath
                                } else {
                                    val dataUri = encodeBitmapToSyncDataUri(newBitmap)
                                    if (dataUri.isNotBlank()) {
                                        dataUri
                                    } else {
                                        val outF = File(context.filesDir, "img_${System.currentTimeMillis()}.jpg")
                                        FileOutputStream(outF).use { out ->
                                            newBitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                                        }
                                        outF.absolutePath
                                    }
                                }
                            } catch (_: Throwable) {
                                encodeBitmapToSyncDataUri(newBitmap).ifBlank { imagePath }
                            }

                            onSaveImage?.invoke(savedUri)
                            onSave()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Uložit foto", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Otočení o 90 stupňů doleva (-90°)
                    IconButton(onClick = {
                        try {
                            val matrix = Matrix().apply { postRotate(-90f) }
                            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            bitmap = ensureSoftwareBitmap(rotated)
                            scale = 1f
                            offset = Offset.Zero
                        } catch (_: Throwable) {}
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Otočit -90°", modifier = Modifier.size(22.dp))
                            Text("-90°", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Přiblížení: zmenšit (-)
                    IconButton(onClick = {
                        scale = (scale / 1.25f).coerceIn(0.25f, 8f)
                    }) {
                        Text("−", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    // Přiblížení: procenta a reset na 100%
                    TextButton(onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(scale * 100).toInt()}%", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            Text("Reset", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Přiblížení: zvětšit (+)
                    IconButton(onClick = {
                        scale = (scale * 1.25f).coerceIn(0.25f, 8f)
                    }) {
                        Text("+", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    // Otočení o 90 stupňů doprava (+90°)
                    IconButton(onClick = {
                        try {
                            val matrix = Matrix().apply { postRotate(90f) }
                            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            bitmap = ensureSoftwareBitmap(rotated)
                            scale = 1f
                            offset = Offset.Zero
                        } catch (_: Throwable) {}
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Otočit +90°", modifier = Modifier.size(22.dp))
                            Text("+90°", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .onSizeChanged { viewSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.25f, 8f)
                        offset += pan
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (size.width <= 10f || size.height <= 10f) return@Canvas
                val bW = imageBitmap.width.toFloat().coerceAtLeast(1f)
                val bH = imageBitmap.height.toFloat().coerceAtLeast(1f)
                val fitScale = minOf(size.width / bW, size.height / bH) * 0.90f
                val effectiveScale = (fitScale * scale).coerceAtLeast(0.01f)
                val dW = bW * effectiveScale
                val dH = bH * effectiveScale

                val left = (size.width - dW) / 2f + offset.x
                val top = (size.height - dH) / 2f + offset.y

                translate(left, top) {
                    scale(effectiveScale, pivot = Offset.Zero) {
                        drawImage(imageBitmap)
                    }
                }

                val (cropWVal, cropHVal) = when (cropMode) {
                    0 -> {
                        val s = minOf(size.width, size.height) * 0.85f
                        Pair(s, s)
                    }
                    1 -> {
                        var w = size.width * 0.90f
                        var h = w * 0.75f
                        if (h > size.height * 0.70f) {
                            h = size.height * 0.70f
                            w = h / 0.75f
                        }
                        Pair(w, h)
                    }
                    2 -> {
                        var w = size.width * 0.92f
                        var h = w * (9f / 16f)
                        if (h > size.height * 0.70f) {
                            h = size.height * 0.70f
                            w = h * (16f / 9f)
                        }
                        Pair(w, h)
                    }
                    else -> {
                        val ratio = bW / bH
                        var w = size.width * 0.92f
                        var h = w / ratio
                        if (h > size.height * 0.75f) {
                            h = size.height * 0.75f
                            w = h * ratio
                        }
                        Pair(w, h)
                    }
                }

                val cLeft = (size.width - cropWVal) / 2f
                val cTop = (size.height - cropHVal) / 2f

                if (cropMode != 3) {
                    val path = Path().apply {
                        addRect(Rect(0f, 0f, size.width, size.height))
                        addRect(Rect(cLeft, cTop, cLeft + cropWVal, cTop + cropHVal))
                        fillType = PathFillType.EvenOdd
                    }
                    drawPath(path, Color.Black.copy(alpha = 0.72f))

                    drawRect(
                        color = Color.White,
                        topLeft = Offset(cLeft, cTop),
                        size = Size(cropWVal, cropHVal),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Mřížka třetin (grid lines)
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(cLeft + cropWVal / 3f, cTop),
                        end = Offset(cLeft + cropWVal / 3f, cTop + cropHVal),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(cLeft + cropWVal * 2f / 3f, cTop),
                        end = Offset(cLeft + cropWVal * 2f / 3f, cTop + cropHVal),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(cLeft, cTop + cropHVal / 3f),
                        end = Offset(cLeft + cropWVal, cTop + cropHVal / 3f),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(cLeft, cTop + cropHVal * 2f / 3f),
                        end = Offset(cLeft + cropWVal, cTop + cropHVal * 2f / 3f),
                        strokeWidth = 1.dp.toPx()
                    )
                } else {
                    drawRect(
                        color = Color.White.copy(alpha = 0.6f),
                        topLeft = Offset(cLeft, cTop),
                        size = Size(cropWVal, cropHVal),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = "Tahem posuňte, gestem či +/- přibližte, dole otočte o 90°",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
