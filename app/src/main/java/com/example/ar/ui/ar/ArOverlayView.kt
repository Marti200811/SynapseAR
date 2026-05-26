package com.example.ar.ui.ar

import android.content.Context
import android.graphics.*
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.View
import com.example.ar.ThemeManager
import com.example.ar.ThemePalette
import kotlin.math.*

/**
 * Overlay transparente sobre la cámara AR.
 *
 * Capas de dibujo:
 *  1. Banner de calibración (rojo/amarillo)
 *  2. Cinta de azimut (franja horizontal superior)
 *  3. Barra de elevación (izquierda)
 *  4. Línea de horizonte artificial
 *  5. Mira central fija (punto de referencia del teléfono)
 *  6. Retícula flotante del objetivo (se mueve en pantalla)
 *     - Flecha de borde cuando el objetivo está fuera de pantalla
 *     - Corchetes militares + anillo pulsante en rojo cuando LOCKED
 *  7. Mini brújula (esquina inferior derecha)
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
    var isVerticalMode: Boolean = false
        set(v) { field = v; invalidate() }

    /** Nombre del objetivo para mostrar en la retícula flotante */
    var targetLabel: String = ""
    /** Distancia formateada para mostrar en la retícula flotante */
    var targetDistanceStr: String = ""

    // ── Campo de visión asumido de la cámara trasera ─────────────────────────
    private val FOV_H = 65f   // grados horizontales
    private val FOV_V = 50f   // grados verticales

    // ── Paleta del tema ──────────────────────────────────────────────────────
    private val pal get() = ThemeManager.getPalette(context)

    // ── Paints ───────────────────────────────────────────────────────────────
    private val tapeBgPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tickPaint      = Paint(Paint.ANTI_ALIAS_FLAG)
    private val majTickPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f }
    private val labelPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val azPaint        = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val needlePaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val targetPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val elevPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
    private val pitchPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
    private val elevBgPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val elevLblPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val alignPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f }
    private val calBgPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val calTxtPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true; color = Color.WHITE
    }
    private val horizonPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val compassBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val compassRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val compassTickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val compassLblPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val p = pal

        tapeBgPaint.color  = p.background
        tickPaint.color    = p.textSub
        majTickPaint.color = p.text
        labelPaint.color   = p.text
        azPaint.color      = p.primary
        needlePaint.color  = p.needle
        targetPaint.color  = p.secondary
        elevPaint.color    = p.secondary
        pitchPaint.color   = p.primary
        horizonPaint.color = Color.argb(120, 255, 255, 255)

        drawCalibrationBanner(canvas, w, h)
        drawAzimuthTape(canvas, w, h, p)
        drawElevationBar(canvas, w, h, p)
        drawHorizonLine(canvas, w, h)
        drawCenterCrosshair(canvas, w, h, p)
        drawTargetReticle(canvas, w, h, p)
        drawCompassDial(canvas, w, h, p)
    }

    // ── 1. Banner de calibración ─────────────────────────────────────────────

    private fun drawCalibrationBanner(canvas: Canvas, w: Float, h: Float) {
        if (calibrationLevel >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) return
        val bannerH = h * 0.055f
        val isUnreliable = calibrationLevel <= SensorManager.SENSOR_STATUS_UNRELIABLE
        calBgPaint.color = if (isUnreliable) Color.argb(200, 200, 30, 30) else Color.argb(200, 180, 120, 0)
        canvas.drawRect(0f, 0f, w, bannerH, calBgPaint)
        calTxtPaint.textSize = bannerH * 0.58f
        val msg = if (isUnreliable)
            "⚠ BRÚJULA NO CALIBRADA — Tocá para calibrar"
        else
            "⚠ Calibración baja — Tocá para calibrar"
        canvas.drawText(msg, w / 2f, bannerH * 0.72f, calTxtPaint)
    }

    // ── 2. Cinta de azimut ───────────────────────────────────────────────────

    private fun drawAzimuthTape(canvas: Canvas, w: Float, h: Float, p: ThemePalette) {
        val tapeH  = h * 0.12f
        val tapeY1 = h * 0.07f
        val tapeY2 = tapeY1 + tapeH
        val cx     = w / 2f
        val degSpan = 90
        val pxPerDeg = w / degSpan

        tapeBgPaint.color = p.background
        canvas.drawRect(0f, tapeY1, w, tapeY2, tapeBgPaint)

        labelPaint.textSize = tapeH * 0.36f
        azPaint.textSize    = tapeH * 0.52f

        for (offset in -degSpan / 2..degSpan / 2) {
            val deg = ((azimuth + offset).toInt() % 360 + 360) % 360
            val x   = cx + offset * pxPerDeg
            val isMajor = deg % 30 == 0
            val isMinor = deg % 10 == 0
            val tickH = when { isMajor -> tapeH * 0.52f; isMinor -> tapeH * 0.28f; else -> tapeH * 0.14f }
            val paint = if (isMajor) majTickPaint else tickPaint
            paint.strokeWidth = if (isMajor) 3f else 1.5f
            canvas.drawLine(x, tapeY1, x, tapeY1 + tickH, paint)
            if (isMajor) {
                val label = when (deg) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> "$deg°" }
                labelPaint.color = if (deg % 90 == 0) p.primary else p.text
                canvas.drawText(label, x, tapeY2 - tapeH * 0.08f, labelPaint)
            }
        }

        // Triángulo del objetivo en la cinta — rojo si alineado
        targetAzimuth?.let { bearing ->
            val diff = ((bearing - azimuth + 540f) % 360f) - 180f
            val triColor = if (isAligned) Color.RED else p.secondary
            if (abs(diff) <= degSpan / 2f) {
                val tx = cx + diff * pxPerDeg
                targetPaint.color = triColor
                val tri = Path().apply {
                    moveTo(tx, tapeY1)
                    lineTo(tx - tapeH * 0.22f, tapeY1 - tapeH * 0.38f)
                    lineTo(tx + tapeH * 0.22f, tapeY1 - tapeH * 0.38f)
                    close()
                }
                canvas.drawPath(tri, targetPaint)
            } else {
                val arrowX = if (diff < 0) w * 0.06f else w * 0.94f
                val arrowY = tapeY1 + tapeH / 2f
                targetPaint.color = triColor
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

        // Aguja central
        needlePaint.color = p.needle
        val needle = Path().apply {
            moveTo(cx, tapeY2 + tapeH * 0.12f)
            lineTo(cx - tapeH * 0.16f, tapeY2 - tapeH * 0.04f)
            lineTo(cx + tapeH * 0.16f, tapeY2 - tapeH * 0.04f)
            close()
        }
        canvas.drawPath(needle, needlePaint)

        azPaint.color = p.primary
        canvas.drawText("%03.0f°".format(azimuth), cx, tapeY1 - tapeH * 0.12f, azPaint)
    }

    // ── 3. Barra de elevación ────────────────────────────────────────────────

    private fun drawElevationBar(canvas: Canvas, w: Float, h: Float, p: ThemePalette) {
        val barW   = w * 0.075f
        val left   = w * 0.015f
        val right  = left + barW
        val top    = h * 0.22f
        val bottom = h * 0.78f
        val barH   = bottom - top
        val cx     = (left + right) / 2f

        elevBgPaint.color = p.background
        canvas.drawRoundRect(left, top, right, bottom, 10f, 10f, elevBgPaint)

        for (deg in 0..90 step 15) {
            val y = bottom - (deg / 90f) * barH
            val isMaj = deg % 45 == 0
            tickPaint.color = if (isMaj) p.text else p.textSub
            tickPaint.strokeWidth = if (isMaj) 2f else 1f
            canvas.drawLine(left + barW * 0.15f, y, right - barW * 0.15f, y, tickPaint)
        }

        targetElevation?.let { elev ->
            val y = bottom - (elev.coerceIn(0f, 90f) / 90f) * barH
            val elvColor = if (isAligned) Color.RED else p.secondary
            elevPaint.color = elvColor
            elevPaint.strokeWidth = 4f
            canvas.drawLine(left, y, right, y, elevPaint)
            elevLblPaint.color    = elvColor
            elevLblPaint.textSize = barW * 0.68f
            canvas.drawText("%.0f°".format(elev), cx, y - 7f, elevLblPaint)
        }

        val pitchY = bottom - (pitch.coerceIn(0f, 90f) / 90f) * barH
        pitchPaint.color = p.primary
        pitchPaint.strokeWidth = 3f
        canvas.drawLine(left, pitchY, right, pitchY, pitchPaint)

        elevLblPaint.color    = p.primary
        elevLblPaint.textSize = barW * 0.60f
        canvas.drawText("%.0f°".format(pitch.coerceIn(0f, 90f)), cx, pitchY + barW * 0.7f, elevLblPaint)

        targetElevation?.let { elev ->
            if (abs(pitch - elev) < 3f) {
                val y = bottom - (elev.coerceIn(0f, 90f) / 90f) * barH
                alignPaint.color = Color.RED
                alignPaint.strokeWidth = 5f
                canvas.drawLine(left, y, right, y, alignPaint)
            }
        }

        elevLblPaint.color    = p.textSub
        elevLblPaint.textSize = barW * 0.58f
        canvas.drawText("EL", cx, top - 9f, elevLblPaint)
    }

    // ── 4. Horizonte artificial ──────────────────────────────────────────────

    private fun drawHorizonLine(canvas: Canvas, w: Float, h: Float) {
        val cy = h / 2f
        val margin = w * 0.22f
        val pitchOffset = -(pitch.coerceIn(-45f, 45f) / 45f) * h * 0.12f
        val horizonY = cy + pitchOffset

        horizonPaint.color = Color.argb(100, 255, 255, 255)
        horizonPaint.strokeWidth = 1.5f
        val gapHalf = w * 0.14f
        canvas.drawLine(margin, horizonY, w / 2f - gapHalf, horizonY, horizonPaint)
        canvas.drawLine(w / 2f + gapHalf, horizonY, w - margin * 0.3f, horizonY, horizonPaint)

        if (abs(pitch) > 2f) {
            val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(160, 200, 200, 200)
                textAlign = Paint.Align.LEFT
                textSize = h * 0.025f
            }
            canvas.drawText("%+.0f°".format(pitch), w - margin * 0.25f, horizonY + lp.textSize / 2f, lp)
        }
    }

    // ── 5. Mira central fija (punto de referencia del teléfono) ─────────────

    private fun drawCenterCrosshair(canvas: Canvas, w: Float, h: Float, p: ThemePalette) {
        val cx = w / 2f
        val cy = h / 2f
        val r  = w * 0.022f

        // Cuando está alineado, la mira se pone roja
        val color = if (isAligned) Color.RED else Color.argb(200, 255, 255, 255)
        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (isAligned) 3f else 2f
            this.color = color
        }

        canvas.drawCircle(cx, cy, r, crossPaint)
        val gap = r * 0.5f
        val len = r * 1.8f
        canvas.drawLine(cx - r - len, cy, cx - gap, cy, crossPaint)
        canvas.drawLine(cx + gap, cy, cx + r + len, cy, crossPaint)
        canvas.drawLine(cx, cy - r - len, cx, cy - gap, crossPaint)
        canvas.drawLine(cx, cy + gap, cx, cy + r + len, crossPaint)

        crossPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 4f, crossPaint)
    }

    // ── 6. Retícula flotante del objetivo ────────────────────────────────────

    private fun drawTargetReticle(canvas: Canvas, w: Float, h: Float, p: ThemePalette) {
        val targetAz = targetAzimuth ?: return   // sin objetivo, nada que dibujar

        // Error angular entre donde mira el teléfono y donde está el objetivo
        var azDiff = targetAz - azimuth
        while (azDiff > 180f) azDiff -= 360f
        while (azDiff < -180f) azDiff += 360f
        val elDiff = targetElevation?.let { it - pitch } ?: 0f

        // Proyección en pantalla según FOV
        val rawX = w / 2f + (azDiff / (FOV_H / 2f)) * (w / 2f)
        val rawY = h / 2f - (elDiff / (FOV_V / 2f)) * (h / 2f)

        val marginH = w * 0.1f
        val marginV = h * 0.15f
        val onScreen = rawX in marginH..(w - marginH) && rawY in marginV..(h - marginV)

        if (!onScreen) {
            drawEdgeArrow(canvas, w, h, rawX, rawY, p)
            return
        }

        // ── Corchetes militares ──────────────────────────────────────────────
        val armLen = w * 0.075f
        val gap    = w * 0.038f
        val locked = isAligned

        val bracketColor = if (locked) Color.RED else p.primary
        val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (locked) 5f else 3f
            color = bracketColor
        }

        // Top-left
        canvas.drawLine(rawX - gap - armLen, rawY - gap, rawX - gap,           rawY - gap,           bracketPaint)
        canvas.drawLine(rawX - gap,           rawY - gap - armLen, rawX - gap, rawY - gap,           bracketPaint)
        // Top-right
        canvas.drawLine(rawX + gap,           rawY - gap - armLen, rawX + gap, rawY - gap,           bracketPaint)
        canvas.drawLine(rawX + gap,           rawY - gap,           rawX + gap + armLen, rawY - gap, bracketPaint)
        // Bottom-left
        canvas.drawLine(rawX - gap - armLen, rawY + gap,           rawX - gap, rawY + gap,           bracketPaint)
        canvas.drawLine(rawX - gap,           rawY + gap,           rawX - gap, rawY + gap + armLen, bracketPaint)
        // Bottom-right
        canvas.drawLine(rawX + gap,           rawY + gap,           rawX + gap + armLen, rawY + gap, bracketPaint)
        canvas.drawLine(rawX + gap,           rawY + gap,           rawX + gap, rawY + gap + armLen, bracketPaint)

        // Punto central del objetivo
        bracketPaint.style = Paint.Style.FILL
        canvas.drawCircle(rawX, rawY, if (locked) 10f else 6f, bracketPaint)
        bracketPaint.style = Paint.Style.STROKE

        if (locked) {
            // ── Anillo pulsante rojo ─────────────────────────────────────────
            val pulse = (System.currentTimeMillis() % 1200L) / 1200f
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = Color.argb(((1f - pulse) * 220).toInt(), 255, 30, 30)
            }
            canvas.drawCircle(rawX, rawY, (gap + armLen) * (0.7f + pulse * 1.0f), ringPaint)

            // Segundo anillo más grande
            val ringPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.argb(((1f - pulse) * 120).toInt(), 255, 30, 30)
            }
            canvas.drawCircle(rawX, rawY, (gap + armLen) * (1.2f + pulse * 1.0f), ringPaint2)

            // Texto LOCKED
            val lockedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.RED
                textAlign = Paint.Align.CENTER
                textSize  = w * 0.052f
                isFakeBoldText = true
            }
            canvas.drawText("● LOCKED", rawX, rawY + gap + armLen + lockedPaint.textSize * 1.6f, lockedPaint)

            invalidate()   // mantener animación del anillo pulsante

        } else {
            // ── Etiqueta: nombre + distancia ─────────────────────────────────
            val labelY = rawY + gap + armLen + w * 0.04f
            if (targetLabel.isNotEmpty()) {
                val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(220, p.text.red(), p.text.green(), p.text.blue())
                    textAlign = Paint.Align.CENTER
                    textSize  = w * 0.033f
                }
                canvas.drawText(targetLabel, rawX, labelY, namePaint)
            }
            if (targetDistanceStr.isNotEmpty()) {
                val distPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = p.secondary
                    textAlign = Paint.Align.CENTER
                    textSize  = w * 0.040f
                    isFakeBoldText = true
                }
                canvas.drawText(targetDistanceStr, rawX, labelY + w * 0.05f, distPaint)
            }
        }
    }

    // ── Flecha de borde (objetivo fuera de pantalla) ─────────────────────────

    private fun drawEdgeArrow(canvas: Canvas, w: Float, h: Float,
                               targetX: Float, targetY: Float, p: ThemePalette) {
        val cx = w / 2f
        val cy = h / 2f

        val dx = targetX - cx
        val dy = targetY - cy
        val len = sqrt(dx * dx + dy * dy)
        val nx = dx / len
        val ny = dy / len

        // Calcular punto en el borde de la pantalla
        val marginH = w * 0.12f
        val marginV = h * 0.18f
        val scaleX = if (nx != 0f) (w / 2f - marginH) / abs(nx) else Float.MAX_VALUE
        val scaleY = if (ny != 0f) (h / 2f - marginV) / abs(ny) else Float.MAX_VALUE
        val scale  = min(scaleX, scaleY)

        val edgeX = (cx + nx * scale).coerceIn(marginH, w - marginH)
        val edgeY = (cy + ny * scale).coerceIn(marginV, h - marginV)

        val arrowSize = w * 0.055f
        val angle = atan2(ny, nx)

        // Fondo semitransparente detrás de la flecha
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(100, 0, 0, 0)
        }
        canvas.drawCircle(edgeX, edgeY, arrowSize * 1.4f, bgPaint)

        // Triángulo de flecha
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isAligned) Color.RED else p.secondary
        }
        val arrowPath = Path().apply {
            moveTo(edgeX + cos(angle) * arrowSize,
                   edgeY + sin(angle) * arrowSize)
            moveTo(edgeX + cos(angle) * arrowSize,
                   edgeY + sin(angle) * arrowSize)
            lineTo(edgeX + cos(angle + PI.toFloat() * 0.75f) * arrowSize * 0.7f,
                   edgeY + sin(angle + PI.toFloat() * 0.75f) * arrowSize * 0.7f)
            lineTo(edgeX + cos(angle - PI.toFloat() * 0.75f) * arrowSize * 0.7f,
                   edgeY + sin(angle - PI.toFloat() * 0.75f) * arrowSize * 0.7f)
            close()
        }
        canvas.drawPath(arrowPath, arrowPaint)

        // Distancia junto a la flecha
        if (targetDistanceStr.isNotEmpty()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = p.secondary
                textAlign = Paint.Align.CENTER
                textSize  = w * 0.034f
                isFakeBoldText = true
            }
            // Texto opuesto a la dirección de la flecha para no solapar
            canvas.drawText(targetDistanceStr,
                edgeX - nx * arrowSize * 1.8f,
                edgeY - ny * arrowSize * 1.8f + textPaint.textSize / 3f,
                textPaint)
        }
    }

    // ── 7. Mini brújula (esquina inferior derecha) ───────────────────────────

    private fun drawCompassDial(canvas: Canvas, w: Float, h: Float, p: ThemePalette) {
        val dialR = min(w, h) * 0.16f
        val cx    = w - dialR - w * 0.04f
        val cy    = h - dialR - h * 0.04f

        compassBgPaint.color = Color.argb(140, 10, 14, 26)
        canvas.drawCircle(cx, cy, dialR, compassBgPaint)

        compassRingPaint.color = Color.argb(180, p.primary.red(), p.primary.green(), p.primary.blue())
        compassRingPaint.strokeWidth = 1.5f
        canvas.drawCircle(cx, cy, dialR, compassRingPaint)
        compassRingPaint.color = Color.argb(60, p.primary.red(), p.primary.green(), p.primary.blue())
        canvas.drawCircle(cx, cy, dialR * 0.75f, compassRingPaint)

        canvas.save()
        canvas.rotate(-azimuth, cx, cy)

        compassTickPaint.strokeWidth = 1.5f
        compassLblPaint.textSize = dialR * 0.30f

        for (deg in 0 until 360 step 10) {
            val a = Math.toRadians(deg.toDouble())
            val isMaj = deg % 90 == 0
            val isMed = deg % 30 == 0
            val innerR = when { isMaj -> dialR * 0.72f; isMed -> dialR * 0.78f; else -> dialR * 0.82f }
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

        // Triángulo del objetivo en el dial — rojo si alineado
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
            targetPaint.color = if (isAligned) Color.RED else p.secondary
            canvas.drawPath(tri, targetPaint)
        }

        canvas.restore()

        // Aguja fija Norte
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

        needlePaint.color = p.primary
        canvas.drawCircle(cx, cy, dialR * 0.07f, needlePaint)

        val azLblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.primary; textAlign = Paint.Align.CENTER
            textSize = dialR * 0.32f; isFakeBoldText = true
        }
        canvas.drawText("%03.0f°".format(azimuth), cx, cy + dialR + dialR * 0.38f, azLblPaint)
    }

    // ── Helpers de color ─────────────────────────────────────────────────────
    private fun Int.red()   = (this shr 16) and 0xFF
    private fun Int.green() = (this shr 8)  and 0xFF
    private fun Int.blue()  = this           and 0xFF
}
