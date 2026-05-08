package com.example.ar.ui.ar

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Overlay transparente que se dibuja encima de la cámara.
 *
 * Muestra:
 *  - Cinta horizontal de azimut (igual que modo vertical de la brújula)
 *  - Mira central con retícula
 *  - Flecha/indicador al objetivo cuando está en rango
 *  - Indicador de elevación vertical (barra izquierda)
 *  - Indicador de coincidencia (flash verde al apuntar bien)
 */
class ArOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Propiedades públicas ──────────────────────────────────────────────────
    var azimuth: Float = 0f
        set(v) { field = v; invalidate() }
    var pitch: Float = 0f
        set(v) { field = v; invalidate() }
    var targetAzimuth: Float? = null
        set(v) { field = v; invalidate() }
    var targetElevation: Float? = null
        set(v) { field = v; invalidate() }
    var isAligned: Boolean = false           // true cuando azimut Y elevación coinciden
        set(v) { field = v; invalidate() }

    // ── Colores ───────────────────────────────────────────────────────────────
    private val cCyan   = 0xFF00E5FF.toInt()
    private val cAmber  = 0xFFFFB300.toInt()
    private val cRed    = 0xFFFF3D6E.toInt()
    private val cGreen  = 0xFF00FF88.toInt()
    private val cWhite  = 0xFFFFFFFF.toInt()
    private val cDark   = 0xCC0A0E1A.toInt()
    private val cGrid   = 0x441F2A44.toInt()

    // ── Paints ────────────────────────────────────────────────────────────────
    private val tapePaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cDark; style = Paint.Style.FILL }
    private val tickPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7A8CA8.toInt(); strokeWidth = 2f }
    private val majTickPaint= Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cWhite; strokeWidth = 3f }
    private val labelPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cWhite; textAlign = Paint.Align.CENTER; textSize = 36f }
    private val azPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cCyan; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cRed; style = Paint.Style.FILL }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cAmber; style = Paint.Style.FILL }
    private val reticlePaint= Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cCyan; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val glowPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x3300E5FF.toInt(); style = Paint.Style.FILL }
    private val alignPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cGreen; style = Paint.Style.STROKE; strokeWidth = 6f }
    private val elevPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cAmber; style = Paint.Style.STROKE; strokeWidth = 4f }
    private val pitchPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cCyan; style = Paint.Style.STROKE; strokeWidth = 3f }
    private val elevBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cDark; style = Paint.Style.FILL }
    private val elevLblPaint= Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cAmber; textAlign = Paint.Align.CENTER; isFakeBoldText = true }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        drawAzimuthTape(canvas, w, h)
        drawReticle(canvas, w, h)
        drawElevationBar(canvas, w, h)
        if (isAligned) drawAlignedFlash(canvas, w, h)
    }

    // ── Cinta de azimut (franja horizontal superior) ─────────────────────────

    private fun drawAzimuthTape(canvas: Canvas, w: Float, h: Float) {
        val tapeH  = h * 0.13f
        val tapeY1 = h * 0.10f
        val tapeY2 = tapeY1 + tapeH
        val cx     = w / 2f

        // Fondo semitransparente
        canvas.drawRect(0f, tapeY1, w, tapeY2, tapePaint)

        val degSpan   = 90              // grados visibles en la cinta
        val pxPerDeg  = w / degSpan

        labelPaint.textSize  = tapeH * 0.38f
        azPaint.textSize     = tapeH * 0.55f

        for (offset in -degSpan / 2..degSpan / 2) {
            val deg = ((azimuth + offset).toInt() % 360 + 360) % 360
            val x   = cx + offset * pxPerDeg
            val isMajor = deg % 30 == 0
            val isMinor = deg % 10 == 0

            val tickH = when {
                isMajor -> tapeH * 0.55f
                isMinor -> tapeH * 0.30f
                else    -> tapeH * 0.15f
            }
            val paint = if (isMajor) majTickPaint else tickPaint
            canvas.drawLine(x, tapeY1, x, tapeY1 + tickH, paint)

            if (isMajor) {
                val label = when (deg) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> "$deg°" }
                labelPaint.color = if (deg % 90 == 0) cCyan else cWhite
                canvas.drawText(label, x, tapeY2 - tapeH * 0.08f, labelPaint)
            }
        }

        // Triángulo objetivo en la cinta
        targetAzimuth?.let { bearing ->
            val diff = ((bearing - azimuth + 540f) % 360f) - 180f
            if (abs(diff) <= degSpan / 2f) {
                val tx = cx + diff * pxPerDeg
                val tri = Path().apply {
                    moveTo(tx, tapeY1)
                    lineTo(tx - tapeH * 0.25f, tapeY1 - tapeH * 0.4f)
                    lineTo(tx + tapeH * 0.25f, tapeY1 - tapeH * 0.4f)
                    close()
                }
                canvas.drawPath(tri, targetPaint)
            } else {
                // Flecha fuera de rango
                val arrowX = if (diff < 0) w * 0.06f else w * 0.94f
                val arrowY = tapeY1 + tapeH / 2f
                val arrow = Path().apply {
                    if (diff < 0) {
                        moveTo(arrowX, arrowY)
                        lineTo(arrowX + tapeH * 0.4f, arrowY - tapeH * 0.25f)
                        lineTo(arrowX + tapeH * 0.4f, arrowY + tapeH * 0.25f)
                    } else {
                        moveTo(arrowX, arrowY)
                        lineTo(arrowX - tapeH * 0.4f, arrowY - tapeH * 0.25f)
                        lineTo(arrowX - tapeH * 0.4f, arrowY + tapeH * 0.25f)
                    }
                    close()
                }
                canvas.drawPath(arrow, targetPaint)
            }
        }

        // Aguja central (posición actual)
        val needle = Path().apply {
            moveTo(cx, tapeY2 + tapeH * 0.15f)
            lineTo(cx - tapeH * 0.18f, tapeY2 - tapeH * 0.05f)
            lineTo(cx + tapeH * 0.18f, tapeY2 - tapeH * 0.05f)
            close()
        }
        canvas.drawPath(needle, needlePaint)

        // Lectura de azimut actual
        azPaint.color = cCyan
        canvas.drawText("%03.0f°".format(azimuth), cx, tapeY1 - tapeH * 0.1f, azPaint)
    }

    // ── Retícula central ─────────────────────────────────────────────────────

    private fun drawReticle(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val r  = w * 0.06f

        // Círculo exterior
        reticlePaint.color = cCyan
        reticlePaint.strokeWidth = 2f
        canvas.drawCircle(cx, cy, r, reticlePaint)

        // Cruz interior
        val gap = r * 0.35f
        val len = r * 0.5f
        canvas.drawLine(cx - r - len, cy, cx - gap, cy, reticlePaint)
        canvas.drawLine(cx + gap, cy, cx + r + len, cy, reticlePaint)
        canvas.drawLine(cx, cy - r - len, cx, cy - gap, reticlePaint)
        canvas.drawLine(cx, cy + gap, cx, cy + r + len, reticlePaint)

        // Punto central
        reticlePaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 4f, reticlePaint)
        reticlePaint.style = Paint.Style.STROKE
    }

    // ── Barra de elevación (izquierda) ───────────────────────────────────────

    private fun drawElevationBar(canvas: Canvas, w: Float, h: Float) {
        val barW    = w * 0.07f
        val left    = w * 0.02f
        val right   = left + barW
        val top     = h * 0.25f
        val bottom  = h * 0.75f
        val barH    = bottom - top
        val cx      = (left + right) / 2f

        // Fondo
        canvas.drawRoundRect(left, top, right, bottom, 8f, 8f, elevBgPaint)

        // Ticks cada 15°
        tickPaint.color = 0xFF7A8CA8.toInt()
        for (deg in 0..90 step 15) {
            val y = bottom - (deg / 90f) * barH
            canvas.drawLine(left + barW * 0.2f, y, right - barW * 0.2f, y, tickPaint)
        }

        // Línea de elevación objetivo (ámbar)
        targetElevation?.let { elev ->
            val y = bottom - (elev.coerceIn(0f, 90f) / 90f) * barH
            canvas.drawLine(left, y, right, y, elevPaint)
            elevLblPaint.textSize = barW * 0.7f
            canvas.drawText("%.0f°".format(elev), cx, y - 6f, elevLblPaint)
        }

        // Línea de pitch actual (cyan)
        val pitchY = bottom - (pitch.coerceIn(0f, 90f) / 90f) * barH
        canvas.drawLine(left, pitchY, right, pitchY, pitchPaint)

        // Etiqueta "EL"
        elevLblPaint.color  = 0xFF7A8CA8.toInt()
        elevLblPaint.textSize = barW * 0.65f
        canvas.drawText("EL", cx, top - 8f, elevLblPaint)
        elevLblPaint.color = cAmber
    }

    // ── Flash verde cuando está apuntado ─────────────────────────────────────

    private fun drawAlignedFlash(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val r  = w * 0.06f

        // Halo verde
        glowPaint.color = 0x4400FF88.toInt()
        canvas.drawCircle(cx, cy, r * 3f, glowPaint)

        // Círculo verde
        alignPaint.strokeWidth = 6f
        canvas.drawCircle(cx, cy, r, alignPaint)

        // Texto LOCKED
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cGreen; textAlign = Paint.Align.CENTER
            textSize = w * 0.05f; isFakeBoldText = true
        }
        canvas.drawText("✓ LOCKED", cx, cy + r * 2.5f, lp)
    }
}
