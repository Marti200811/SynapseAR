package com.example.ar.ui.ar

import android.content.Context
import android.graphics.*
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.View
import com.example.ar.ThemeManager
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Overlay transparente sobre la cámara AR.
 *
 * Capas de dibujo:
 *  1. Banner de calibración (rojo/amarillo) — cuando el magnetómetro está mal calibrado
 *  2. Cinta de azimut (franja horizontal superior)
 *  3. Brújula circular semitransparente (esquina inferior derecha)
 *  4. Retícula central con halo de alineación
 *  5. Barra de elevación (izquierda) — funciona en horizontal y vertical
 *  6. Línea de horizonte artificial (centro)
 *  7. Flash verde "LOCKED" cuando azimut + elevación coinciden
 */
class ArOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Propiedades públicas ─────────────────────────────────────────────────
    var azimuth: Float = 0f
        set(v) { field = v; invalidate() }
    var pitch: Float = 0f
        set(v) { field = v; invalidate() }
    var roll: Float = 0f
        set(v) { field = v; invalidate() }
    var targetAzimuth: Float? = null
        set(v) { field = v; invalidate() }
    var targetElevation: Float? = null
        set(v) { field = v; invalidate() }
    var isAligned: Boolean = false
        set(v) { field = v; invalidate() }
    var calibrationLevel: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        set(v) { field = v; invalidate() }
    var isVerticalMode: Boolean = false  // true = teléfono apunta al cielo
        set(v) { field = v; invalidate() }

    // ── Paleta (del ThemeManager) ────────────────────────────────────────────
    private val pal get() = ThemeManager.getPalette(context)

    // ── Paints base (se actualizan en onDraw con la paleta actual) ───────────
    private val tapeBgPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tickPaint    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val majTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f }
    private val labelPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val azPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val needlePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val targetPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val glowPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val alignPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f }
    private val elevPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
    private val pitchPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
    private val elevBgPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val elevLblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val calBgPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val calTxtPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true; color = Color.WHITE
    }
    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val compassBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val compassRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val compassTickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val compassLblPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val p = pal   // snapshot de la paleta actual

        // Actualizar colores desde paleta
        tapeBgPaint.color  = p.background
        tickPaint.color    = p.textSub
        majTickPaint.color = p.text
        labelPaint.color   = p.text
        azPaint.color      = p.primary
        needlePaint.color  = p.needle
        targetPaint.color  = p.secondary
        reticlePaint.color = p.primary
        alignPaint.color   = p.align
        elevPaint.color    = p.secondary
        pitchPaint.color   = p.primary
        horizonPaint.color = Color.argb(120, 255, 255, 255)

        drawCalibrationBanner(canvas, w, h)
        drawAzimuthTape(canvas, w, h, p)
        drawElevationBar(canvas, w, h, p)
        drawHorizonLine(canvas, w, h)
        drawReticle(canvas, w, h, p)
        drawCompassDial(canvas, w, h, p)
        if (isAligned) drawAlignedFlash(canvas, w, h, p)
    }

    // ── 1. Banner de calibración ─────────────────────────────────────────────

    private fun drawCalibrationBanner(canvas: Canvas, w: Float, h: Float) {
        if (calibrationLevel >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) return

        val bannerH = h * 0.055f
        val isUnreliable = calibrationLevel <= SensorManager.SENSOR_STATUS_UNRELIABLE

        calBgPaint.color = if (isUnreliable)
            Color.argb(200, 200, 30, 30)
        else
            Color.argb(200, 180, 120, 0)

        canvas.drawRect(0f, 0f, w, bannerH, calBgPaint)

        calTxtPaint.textSize = bannerH * 0.58f
        val msg = if (isUnreliable)
            "⚠ BRÚJULA NO CALIBRADA — Tocá para calibrar"
        else
            "⚠ Calibración baja — Tocá para calibrar"
        canvas.drawText(msg, w / 2f, bannerH * 0.72f, calTxtPaint)
    }

    // ── 2. Cinta de azimut (parte superior) ──────────────────────────────────

    private fun drawAzimuthTape(canvas: Canvas, w: Float, h: Float, p: ThemePalette) {
        val tapeH  = h * 0.12f
        val tapeY1 = h * 0.07f
        val tapeY2 = tapeY1 + tapeH
        val cx     = w / 2f
        val degSpan = 90
        val pxPerDeg = w / degSpan

        // Fondo semitransparente
        tapeBgPaint.color = p.background
        canvas.drawRect(0f, tapeY1, w, tapeY2, tapeBgPaint)

        labelPaint.textSize = tapeH * 0.36f
        azPaint.textSize    = tapeH * 0.52f

        for (offset in -degSpan / 2..degSpan / 2) {
            val deg = ((azimuth + offset).toInt() % 360 + 360) % 360
            val x   = cx + offset * pxPerDeg
            val isMajor = deg % 30 == 0
            val isMinor = deg % 10 == 0

            val tickH = when {
                isMajor -> tapeH * 0.52f
                isMinor -> tapeH * 0.28f
                else    -> tapeH * 0.14f
            }
            val paint = if (isMajor) majTickPaint else tickPaint
            paint.strokeWidth = if (isMajor) 3f else 1.5f
            canvas.drawLine(x, tapeY1, x, tapeY1 + tickH, paint)

            if (isMajor) {
                val label = when (deg) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> "$deg°" }
                labelPaint.color = if (deg % 90 == 0) p.primary else p.text
                canvas.drawText(label, x, tapeY2 - tapeH * 0.08f, labelPaint)
            }
        }

        // Triángulo de objetivo en la cinta
        targetAzimuth?.let { bearing ->
            val diff = ((bearing - azimuth + 540f) % 360f) - 180f
            if (abs(diff) <= degSpan / 2f) {
                val tx = cx + diff * pxPerDeg
                targetPaint.color = p.secondary
                val tri = Path().apply {
                    moveTo(tx, tapeY1)
                    lineTo(tx - tapeH * 0.22f, tapeY1 - tapeH * 0.38f)
                    lineTo(tx + tapeH * 0.22f, tapeY1 - tapeH * 0.38f)
                    close()
                }
                canvas.drawPath(tri, targetPaint)
            } else {
                // Flecha lateral fuera de rango
                val arrowX = if (diff < 0) w * 0.06f else w * 0.94f
                val arrowY = tapeY1 + tapeH / 2f
                targetPaint.color = p.secondary
                val arrow = Path().apply {
                    if (diff < 0) {
                        moveTo(arrowX, arrowY)
                        lineTo(arrowX + tapeH * 0.38f, arrowY - tapeH * 0.22f)
                        lineTo(arrowX + tapeH * 0.38f, arrowY + tapeH * 0.22f)
                    } else {
                        moveTo(arrowX, arrowY)
                        lineTo(arrowX - tapeH * 0.38f, arrowY - tapeH * 0.22f)
                        lineTo(arrowX - tapeH * 0.38f, arrowY + tapeH * 0.22f)
                    }
                    close()
                }
                canvas.drawPath(arrow, targetPaint)
            }
        }

        // Aguja central (posición actual)
        needlePaint.color = p.needle
        val needle = Path().apply {
            moveTo(cx, tapeY2 + tapeH * 0.12f)
            lineTo(cx - tapeH * 0.16f, tapeY2 - tapeH * 0.04f)
            lineTo(cx + tapeH * 0.16f, tapeY2 - tapeH * 0.04f)
            close()
        }
        canvas.drawPath(needle, needlePaint)

        // Lectura de azimut
        azPaint.color = p.primary
        canvas.drawText("%03.0f°".format(azimuth), cx, tapeY1 - tapeH * 0.12f, azPaint)
    }

    // ── 3. Barra de elevación (izquierda) ─────────────────────────────────────

    private fun drawElevationBar(canvas: Canvas, w: Float, h: Float, p: ThemePalette) {
        val barW   = w * 0.075f
        val left   = w * 0.015f
        val right  = left + barW
        val top    = h * 0.22f
        val bottom = h * 0.78f
        val barH   = bottom - top
        val cx     = (left + right) / 2f

        // Fondo
        elevBgPaint.color = p.background
        canvas.drawRoundRect(left, top, right, bottom, 10f, 10f, elevBgPaint)

        // Ticks cada 15°
        for (deg in 0..90 step 15) {
            val y = bottom - (deg / 90f) * barH
            val isMaj = deg % 45 == 0
            tickPaint.color = if (isMaj) p.text else p.textSub
            tickPaint.strokeWidth = if (isMaj) 2f else 1f
            canvas.drawLine(left + barW * 0.15f, y, right - barW * 0.15f, y, tickPaint)
        }

        // Línea de elevación objetivo (color secundario)
        targetElevation?.let { elev ->
            val y = bottom - (elev.coerceIn(0f, 90f) / 90f) * barH
            elevPaint.color = p.secondary
            elevPaint.strokeWidth = 4f
            canvas.drawLine(left, y, right, y, elevPaint)
            elevLblPaint.color    = p.secondary
            elevLblPaint.textSize = barW * 0.68f
            canvas.drawText("%.0f°".format(elev), cx, y - 7f, elevLblPaint)
        }

        // Línea de pitch actual (color primario)
        val effectivePitch = if (isVerticalMode) pitch else pitch
        val pitchY = bottom - (effectivePitch.coerceIn(0f, 90f) / 90f) * barH
        pitchPaint.color = p.primary
        pitchPaint.strokeWidth = 3f
        canvas.drawLine(left, pitchY, right, pitchY, pitchPaint)

        // Lectura pitch numérica
        elevLblPaint.color    = p.primary
        elevLblPaint.textSize = barW * 0.60f
        canvas.drawText("%.0f°".format(effectivePitch.coerceIn(0f, 90f)), cx, pitchY + barW * 0.7f, elevLblPaint)

        // Coincidencia elevación (verde)
        targetElevation?.let { elev ->
            if (abs(effectivePitch - elev) < 3f) {
                val y = bottom - (elev.coerceIn(0f, 90f) / 90f) * barH
                alignPaint.color = p.align
                alignPaint.strokeWidth = 5f
                canvas.drawLine(left, y, right, y, alignPaint)
            }
        }

        // Etiqueta "EL"
        elevLblPaint.color    = p.textSub
        elevLblPaint.textSize = barW * 0.58f
        canvas.drawText("EL", cx, top - 9f, elevLblPaint)
    }

    // ── 4. Línea de horizonte artificial (centro) ────────────────────────────

    private fun drawHorizonLine(canvas: Canvas, w: Float, h: Float) {
        val cy     = h / 2f
        val margin = w * 0.22f   // no solapar con la barra de elevación

        // Desplazamiento vertical según pitch: 0°=centro, 90°=borde superior
        val pitchOffset = -(pitch.coerceIn(-45f, 45f) / 45f) * h * 0.12f
        val horizonY = cy + pitchOffset

        horizonPaint.color = Color.argb(100, 255, 255, 255)
        horizonPaint.strokeWidth = 1.5f
        // Líneas cortas a los costados (dejan libre el centro para la retícula)
        val gapHalf = w * 0.14f
        canvas.drawLine(margin, horizonY, w / 2f - gapHalf, horizonY, horizonPaint)
        canvas.drawLine(w / 2f + gapHalf, horizonY, w - margin * 0.3f, horizonY, horizonPaint)

        // Marcas de pitch a los lados
        if (abs(pitch) > 2f) {
            horizonPaint.color = Color.argb(140, 200, 200, 200)
            val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(160, 200, 200, 200)
                textAlign = Paint.Align.LEFT
                textSize = h * 0.025f
            }
            canvas.drawText("%+.0f°".format(pitch), w - margin * 0.25f, horizonY + lp.textSize / 2f, lp)
        }
    }

    // ── 5. Retícula central ───────────────────────────────────────────────────

    private fun drawReticle(canvas: Canvas, w: Float, h: Float, p: ThemePalette) {
        val cx = w / 2f
        val cy = h / 2f
        val r  = w * 0.055f

        reticlePaint.color = p.primary
        reticlePaint.strokeWidth = 2f
        canvas.drawCircle(cx, cy, r, reticlePaint)

        val gap = r * 0.38f
        val len = r * 0.55f
        canvas.drawLine(cx - r - len, cy, cx - gap, cy, reticlePaint)
        canvas.drawLine(cx + gap, cy, cx + r + len, cy, reticlePaint)
        canvas.drawLine(cx, cy - r - len, cx, cy - gap, reticlePaint)
        canvas.drawLine(cx, cy + gap, cx, cy + r + len, reticlePaint)

        // Punto central
        reticlePaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 4f, reticlePaint)
        reticlePaint.style = Paint.Style.STROKE
    }

    // ── 6. Brújula circular semitransparente (esquina inferior derecha) ───────

    private fun drawCompassDial(canvas: Canvas, w: Float, h: Float, p: ThemePalette) {
        val dialR  = min(w, h) * 0.16f   // radio del dial
        val cx     = w - dialR - w * 0.04f
        val cy     = h - dialR - h * 0.04f

        // Fondo semitransparente
        compassBgPaint.color = Color.argb(140, 10, 14, 26)
        canvas.drawCircle(cx, cy, dialR, compassBgPaint)

        // Anillo exterior
        compassRingPaint.color = Color.argb(180, p.primary.red(), p.primary.green(), p.primary.blue())
        compassRingPaint.strokeWidth = 1.5f
        canvas.drawCircle(cx, cy, dialR, compassRingPaint)
        compassRingPaint.color = Color.argb(60, p.primary.red(), p.primary.green(), p.primary.blue())
        canvas.drawCircle(cx, cy, dialR * 0.75f, compassRingPaint)

        // Ticks y cardinales (el dial ROTA con el azimut)
        canvas.save()
        canvas.rotate(-azimuth, cx, cy)

        compassTickPaint.strokeWidth = 1.5f
        compassLblPaint.textSize = dialR * 0.30f

        for (deg in 0 until 360 step 10) {
            val a = Math.toRadians(deg.toDouble())
            val isMaj = deg % 90 == 0
            val isMed = deg % 30 == 0
            val innerR = when {
                isMaj -> dialR * 0.72f
                isMed -> dialR * 0.78f
                else  -> dialR * 0.82f
            }
            compassTickPaint.color = when {
                isMaj -> p.text
                isMed -> Color.argb(200, p.textSub.red(), p.textSub.green(), p.textSub.blue())
                else  -> Color.argb(120, p.textSub.red(), p.textSub.green(), p.textSub.blue())
            }
            compassTickPaint.strokeWidth = if (isMaj) 2.5f else 1f
            val ox = cx + dialR * 0.95f * sin(a).toFloat()
            val oy = cy - dialR * 0.95f * cos(a).toFloat()
            val ix = cx + innerR * sin(a).toFloat()
            val iy = cy - innerR * cos(a).toFloat()
            canvas.drawLine(ix, iy, ox, oy, compassTickPaint)

            if (isMaj) {
                val lx = cx + dialR * 0.57f * sin(a).toFloat()
                val ly = cy - dialR * 0.57f * cos(a).toFloat() + compassLblPaint.textSize / 3f
                val lbl = when (deg) { 0 -> "N"; 90 -> "E"; 180 -> "S"; else -> "W" }
                compassLblPaint.color = if (deg == 0) p.primary else p.text
                canvas.drawText(lbl, lx, ly, compassLblPaint)
            }
        }

        // Triángulo del objetivo (ámbar)
        targetAzimuth?.let { bearing ->
            val a = Math.toRadians(bearing.toDouble())
            val tx = cx + dialR * 0.88f * sin(a).toFloat()
            val ty = cy - dialR * 0.88f * cos(a).toFloat()
            val tri = Path().apply {
                moveTo(tx, ty)
                lineTo(tx - dialR * 0.06f, ty + dialR * 0.12f)
                lineTo(tx + dialR * 0.06f, ty + dialR * 0.12f)
                close()
            }
            targetPaint.color = p.secondary
            canvas.drawPath(tri, targetPaint)
        }

        canvas.restore()   // restaurar rotación antes de dibujar la aguja fija

        // Aguja fija Norte (roja arriba, blanca abajo)
        needlePaint.color = p.needle
        val needleN = Path().apply {
            moveTo(cx, cy - dialR * 0.65f)
            lineTo(cx - dialR * 0.06f, cy)
            lineTo(cx + dialR * 0.06f, cy)
            close()
        }
        canvas.drawPath(needleN, needlePaint)
        needlePaint.color = Color.argb(200, 255, 255, 255)
        val needleS = Path().apply {
            moveTo(cx, cy + dialR * 0.65f)
            lineTo(cx - dialR * 0.06f, cy)
            lineTo(cx + dialR * 0.06f, cy)
            close()
        }
        canvas.drawPath(needleS, needlePaint)

        // Círculo central
        needlePaint.color = p.primary
        canvas.drawCircle(cx, cy, dialR * 0.07f, needlePaint)

        // Azimut numérico debajo del dial
        val azLblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.primary; textAlign = Paint.Align.CENTER
            textSize = dialR * 0.32f; isFakeBoldText = true
        }
        canvas.drawText("%03.0f°".format(azimuth), cx, cy + dialR + dialR * 0.38f, azLblPaint)
    }

    // ── 7. Flash verde LOCKED ─────────────────────────────────────────────────

    private fun drawAlignedFlash(canvas: Canvas, w: Float, h: Float, p: ThemePalette) {
        val cx = w / 2f
        val cy = h / 2f
        val r  = w * 0.055f

        glowPaint.color = Color.argb(60, p.align.red(), p.align.green(), p.align.blue())
        canvas.drawCircle(cx, cy, r * 3.2f, glowPaint)

        alignPaint.color = p.align
        alignPaint.strokeWidth = 6f
        canvas.drawCircle(cx, cy, r, alignPaint)

        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.align; textAlign = Paint.Align.CENTER
            textSize = w * 0.048f; isFakeBoldText = true
        }
        canvas.drawText("✓ LOCKED", cx, cy + r * 2.6f, lp)
    }

    // ── Helpers de color ─────────────────────────────────────────────────────

    private fun Int.red()   = (this shr 16) and 0xFF
    private fun Int.green() = (this shr 8)  and 0xFF
    private fun Int.blue()  = this           and 0xFF
}
