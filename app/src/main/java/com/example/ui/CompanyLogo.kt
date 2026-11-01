package com.example.ui

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Oficiální logo KONTEJNERY OSTRAVA s.r.o.
 * Zachovává přesný poměr stran 760 : 490 (1.551 : 1) bez jakékoliv deformace,
 * s vektorovou geometrií střešní konstrukce kontejneru, rohových sloupků s ISO oky,
 * perspektivního okna a precizní typografií.
 */
@Composable
fun CompanyLogo(
    modifier: Modifier = Modifier,
    size: Dp? = null,
    showSubtitle: Boolean = true
) {
    val layoutModifier = if (size != null) {
        modifier.width(size).aspectRatio(760f / 490f)
    } else {
        modifier.aspectRatio(760f / 490f)
    }

    Box(
        modifier = layoutModifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.45f), RoundedCornerShape(14.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { composeCanvas ->
                val canvas = composeCanvas.nativeCanvas
                val w = this.size.width
                val h = this.size.height
                val sx = w / 760f
                val sy = h / 490f

                // 1. Radiální gradient pozadí (#0A3977 -> #02204F -> #001538)
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        w * 0.5f,
                        h * 0.54f,
                        w * 0.62f,
                        intArrayOf(
                            AndroidColor.parseColor("#0A3977"),
                            AndroidColor.parseColor("#02204F"),
                            AndroidColor.parseColor("#001538")
                        ),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    style = Paint.Style.FILL
                }
                val cornerRadiusPx = 22f * sx
                canvas.drawRoundRect(RectF(0f, 0f, w, h), cornerRadiusPx, cornerRadiusPx, bgPaint)

                // 2. Barvy pro bílé prvky a tmavě modré otvory
                val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    style = Paint.Style.FILL
                }
                val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.parseColor("#02204F")
                    style = Paint.Style.FILL
                }

                // 3. Levý rohový sloupek s oválným okem
                // SVG: M 106 156 C 106 148 114 144 126 144 C 138 144 146 148 146 156 L 146 238 L 106 238 Z
                val leftPost = Path().apply {
                    moveTo(106f * sx, 156f * sy)
                    cubicTo(106f * sx, 148f * sy, 114f * sx, 144f * sy, 126f * sx, 144f * sy)
                    cubicTo(138f * sx, 144f * sy, 146f * sx, 148f * sy, 146f * sx, 156f * sy)
                    lineTo(146f * sx, 238f * sy)
                    lineTo(106f * sx, 238f * sy)
                    close()
                }
                canvas.drawPath(leftPost, whitePaint)
                canvas.drawOval(
                    RectF((126f - 8f) * sx, (161f - 11f) * sy, (126f + 8f) * sx, (161f + 11f) * sy),
                    holePaint
                )

                // 4. Levý šikmý střešní nosník
                // SVG: polygon points="136,158 399,51 399,86 136,191"
                val leftBeam = Path().apply {
                    moveTo(136f * sx, 158f * sy)
                    lineTo(399f * sx, 51f * sy)
                    lineTo(399f * sx, 86f * sy)
                    lineTo(136f * sx, 191f * sy)
                    close()
                }
                canvas.drawPath(leftBeam, whitePaint)

                // 5. Středový hlavní rohový sloupek s okem
                // SVG: M 405 44 C 405 28 416 19 432 19 C 446 19 452 28 452 44 L 452 238 L 405 238 Z
                val centerPost = Path().apply {
                    moveTo(405f * sx, 44f * sy)
                    cubicTo(405f * sx, 28f * sy, 416f * sx, 19f * sy, 432f * sx, 19f * sy)
                    cubicTo(446f * sx, 19f * sy, 452f * sx, 28f * sy, 452f * sx, 44f * sy)
                    lineTo(452f * sx, 238f * sy)
                    lineTo(405f * sx, 238f * sy)
                    close()
                }
                canvas.drawPath(centerPost, whitePaint)
                canvas.drawOval(
                    RectF((428f - 10f) * sx, (48f - 13f) * sy, (428f + 10f) * sx, (48f + 13f) * sy),
                    holePaint
                )

                // 6. Pravý šikmý střešní nosník
                // SVG: polygon points="458,49 632,126 632,160 458,85"
                val rightBeam = Path().apply {
                    moveTo(458f * sx, 49f * sy)
                    lineTo(632f * sx, 126f * sy)
                    lineTo(632f * sx, 160f * sy)
                    lineTo(458f * sx, 85f * sy)
                    close()
                }
                canvas.drawPath(rightBeam, whitePaint)

                // 7. Pravý rohový sloupek s okem
                // SVG: M 630 136 C 630 121 639 113 653 113 C 665 113 670 121 670 136 L 670 238 L 630 238 Z
                val rightPost = Path().apply {
                    moveTo(630f * sx, 136f * sy)
                    cubicTo(630f * sx, 121f * sy, 639f * sx, 113f * sy, 653f * sx, 113f * sy)
                    cubicTo(665f * sx, 113f * sy, 670f * sx, 121f * sy, 670f * sx, 136f * sy)
                    lineTo(670f * sx, 238f * sy)
                    lineTo(630f * sx, 238f * sy)
                    close()
                }
                canvas.drawPath(rightPost, whitePaint)
                canvas.drawOval(
                    RectF((649f - 9f) * sx, (141f - 12f) * sy, (649f + 9f) * sx, (141f + 12f) * sy),
                    holePaint
                )

                // 8. Perspektivní okno kontejneru vpravo
                // SVG: polygon points="518,153 582,172 582,226 518,226"
                val windowPoly = Path().apply {
                    moveTo(518f * sx, 153f * sy)
                    lineTo(582f * sx, 172f * sy)
                    lineTo(582f * sx, 226f * sy)
                    lineTo(518f * sx, 226f * sy)
                    close()
                }
                canvas.drawPath(windowPoly, whitePaint)

                // 9. Nápisy (Typography)
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                    color = AndroidColor.WHITE
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                }

                // "KONTEJNERY"
                textPaint.textSize = 66f * sy
                textPaint.letterSpacing = 0.04f
                canvas.drawText("KONTEJNERY", 384f * sx, 314f * sy, textPaint)

                // "OSTRAVA"
                textPaint.textSize = 66f * sy
                textPaint.letterSpacing = 0.28f
                canvas.drawText("OSTRAVA", 396f * sx, 392f * sy, textPaint)

                if (showSubtitle) {
                    // Decentní azurová dělící linka nad podtitulem
                    val cyanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = AndroidColor.parseColor("#009FE3")
                        style = Paint.Style.STROKE
                        strokeWidth = (2.2f * sy).coerceAtLeast(1.5f)
                    }
                    canvas.drawLine(106f * sx, 418f * sy, 670f * sx, 418f * sy, cyanLinePaint)

                    // "PRODEJ | PRONÁJEM | DOPRAVA"
                    textPaint.textSize = 31f * sy
                    textPaint.letterSpacing = 0.05f
                    canvas.drawText("PRODEJ | PRONÁJEM | DOPRAVA", 384f * sx, 458f * sy, textPaint)
                }
            }
        }
    }
}
