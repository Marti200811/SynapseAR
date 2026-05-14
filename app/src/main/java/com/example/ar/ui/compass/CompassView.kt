package com.example.ar.ui.compass

import android.content.Context
import android.graphics.*
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.ar.R
import com.example.ar.ThemeManager
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.abs

class CompassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    enum class Mode { HORIZONTAL, VERTICAL }

    // Paleta dinámica (cambia según el tema Pro)
    private val pal get() = ThemeManager.getPalette(context)

    // Colors fijos de fondo (no varían con el tema)
    private val cBg    = ContextCompat.getColor(context, R.color.bg_panel)
    private val cGrid  = ContextCompat.getColor(context, R.color.grid_line)

    private val ringPaint       = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val tickPaint       = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f }
    private val majorTickPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f }
    private val cardinalPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val degPaint        = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val readoutPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val readoutSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val needlePaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val targetPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint       = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bgPaint         = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cBg; style = Paint.Style.FILL }
    private val elevBarPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val elevLabelPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val calBgPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val calTxtPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true; color = Color.WHITE
    }

    var azimuth: Float = 0f
        set(value) { field = value; invalidate() }
    var targetBearing: Float? = null
        set(value) { field = value; invalidate() }
    var pitch: Float = 0f
        set(value) { field = value; invalidate() }
    var distanceMeters: Double? = null
        set(value) { field = value; invalidate() }
    var mode: Mode = Mode.HORIZONTAL
        set(value) { field = value; invalidate() }
    var targetElevation: Float? = null
        set(value) { field = value; invalidate() }
    var calibrationLevel: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val p = pal
        // Actualizar colores desde la paleta activa
        ringPaint.color       = cGrid
        tickPaint.color       = p.textSub
        majorTickPaint.color  = p.text
        cardinalPaint.color   = p.text
        degPaint.color        = p.textSub
        readoutPaint.color    = p.primary
        readoutSubPaint.color = p.textSub
        needlePaint.color     = p.needle
        targetPaint.color     = p.secondary
        glowPaint.color       = p.glow

        if (mode == Mode.HORIZONTAL) drawDial(canvas) else drawVerticalHud(canvas)
        drawCalibrationBanner(canvas)
    }

    private fun drawCalibrationBanner(canvas: Canvas) {
        if (calibrationLevel >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) return
        val w = width.toFloat()
        val h = height.toFloat()
        val bannerH = h * 0.06f
        calBgPaint.color = if (calibrationLevel <= SensorManager.SENSOR_STATUS_UNRELIABLE)
            Color.argb(210, 200, 30, 30) else Color.argb(210, 180, 120, 0)
        canvas.drawRect(0f, 0f, w, bannerH, calBgPaint)
        calTxtPaint.textSize = bannerH * 0.55f
        val msg = if (calibrationLevel <= SensorManager.SENSOR_STATUS_UNRELIABLE)
            "⚠ BRÚJULA SIN CALIBRAR — Tocá para calibrar"
        else
            "⚠ Calibración baja — Tocá para calibrar"
        canvas.drawText(msg, w / 2f, bannerH * 0.70f, calTxtPaint)
    }

    // ── Modo horizontal ───────────────────────────────────────────────────────

    private fun drawDial(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Reservar espacio a la derecha para la barra de elevación
        val elevBarW = w * 0.14f
        val dialRight = w - elevBarW - w * 0.02f
        val cx = dialRight / 2f
        val cy = h / 2f
        val radius = min(dialRight, h) / 2f * 0.92f

        // Fondo brillo
        glowPaint.color = cGlow
        canvas.drawCircle(cx, cy, radius, glowPaint)
        canvas.drawCircle(cx, cy, radius * 0.97f, bgPaint)

        // Anillos
        ringPaint.color = cGrid
        for (frac in listOf(0.55f, 0.72f, 0.88f, 0.97f))
            canvas.drawCircle(cx, cy, radius * frac, ringPaint)

        // Dial rotado
        canvas.save()
        canvas.rotate(-azimuth, cx, cy)

        cardinalPaint.textSize = radius * 0.16f
        degPaint.textSize = radius * 0.07f
        for (deg in 0 until 360 step 5) {
            val a = Math.toRadians(deg.toDouble())
            val isMajor = deg % 30 == 0
            val len = if (isMajor) radius * 0.10f else radius * 0.05f
            val rOuter = radius * 0.95f
            canvas.drawLine(
                cx + (rOuter - len) * sin(a).toFloat(), cy - (rOuter - len) * cos(a).toFloat(),
                cx + rOuter * sin(a).toFloat(),         cy - rOuter * cos(a).toFloat(),
                if (isMajor) majorTickPaint else tickPaint
            )
            if (isMajor && deg % 90 != 0) {
                val tr = radius * 0.78f
                canvas.drawText(deg.toString(),
                    cx + tr * sin(a).toFloat(),
                    cy - tr * cos(a).toFloat() + degPaint.textSize / 3f, degPaint)
            }
        }

        drawCardinal(canvas, "N", cx, cy, radius * 0.78f, 0.0,   cCyan)
        drawCardinal(canvas, "E", cx, cy, radius * 0.78f, 90.0,  cText)
        drawCardinal(canvas, "S", cx, cy, radius * 0.78f, 180.0, cText)
        drawCardinal(canvas, "W", cx, cy, radius * 0.78f, 270.0, cText)

        // Triángulo objetivo (azimut)
        targetBearing?.let { bearing ->
            val a = Math.toRadians(bearing.toDouble())
            val tx = cx + (radius * 0.92f) * sin(a).toFloat()
            val ty = cy - (radius * 0.92f) * cos(a).toFloat()
            val path = Path().apply {
                moveTo(tx, ty)
                lineTo(tx - radius * 0.04f, ty + radius * 0.08f)
                lineTo(tx + radius * 0.04f, ty + radius * 0.08f)
                close()
            }
            canvas.drawPath(path, targetPaint)
        }
        canvas.restore()

        // Aguja roja fija
        val needle = Path().apply {
            moveTo(cx, cy - radius * 0.55f)
            lineTo(cx - radius * 0.05f, cy)
            lineTo(cx + radius * 0.05f, cy)
            close()
        }
        canvas.drawPath(needle, needlePaint)
        canvas.drawCircle(cx, cy, radius * 0.05f, needlePaint)

        // Lectura central
        readoutPaint.textSize = radius * 0.22f
        canvas.drawText("%03.0f°".format(azimuth), cx, cy + radius * 0.30f, readoutPaint)
        readoutSubPaint.textSize = radius * 0.09f
        val sub = distanceMeters?.let { distString(it) } ?: cardinalLabel(azimuth)
        canvas.drawText(sub, cx, cy + radius * 0.45f, readoutSubPaint)

        // ── Barra de elevación (derecha) ──────────────────────────────
        drawElevationBar(canvas, dialRight + w * 0.02f, w - w * 0.01f, h * 0.10f, h * 0.90f)
    }

    /**
     * Barra vertical de elevación.
     * Escala: 0° (abajo) → 90° (arriba).
     * Línea ámbar = elevación requerida del satélite.
     * Línea cyan  = pitch actual del teléfono (inclinación).
     */
    private fun drawElevationBar(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float) {
        val barH = bottom - top
        val barCx = (left + right) / 2f

        // Fondo de la barra
        elevBarPaint.color = cGrid
        elevBarPaint.strokeWidth = 1.5f
        canvas.drawRect(left, top, right, bottom, elevBarPaint)

        // Ticks cada 10°
        elevLabelPaint.textSize = (right - left) * 0.55f
        elevLabelPaint.color = cText2
        for (deg in 0..90 step 10) {
            val y = bottom - (deg / 90f) * barH
            val isMajor = deg % 30 == 0
            val tickLen = if (isMajor) (right - left) * 0.6f else (right - left) * 0.3f
            elevBarPaint.color = if (isMajor) cText else cGrid
            elevBarPaint.strokeWidth = if (isMajor) 2f else 1f
            canvas.drawLine(left, y, left + tickLen, y, elevBarPaint)
            if (isMajor) {
                elevLabelPaint.textAlign = Paint.Align.LEFT
                canvas.drawText("$deg°", left + tickLen + 4f, y + elevLabelPaint.textSize / 3f, elevLabelPaint)
            }
        }

        // Etiqueta "EL"
        elevLabelPaint.color = cText2
        elevLabelPaint.textAlign = Paint.Align.CENTER
        elevLabelPaint.textSize = (right - left) * 0.5f
        canvas.drawText("EL", barCx, top - 6f, elevLabelPaint)

        // Línea ámbar = elevación objetivo del satélite
        targetElevation?.let { elev ->
            val clampedEl = elev.coerceIn(0f, 90f)
            val y = bottom - (clampedEl / 90f) * barH
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = cAmber; strokeWidth = 4f; style = Paint.Style.STROKE
            }
            canvas.drawLine(left, y, right, y, p)
            // Etiqueta valor
            val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = cAmber; textSize = (right - left) * 0.6f
                textAlign = Paint.Align.CENTER; isFakeBoldText = true
            }
            canvas.drawText("%.0f°".format(clampedEl), barCx, y - 6f, lp)
        }

        // Línea cyan = pitch actual del teléfono
        val pitchClamped = pitch.coerceIn(0f, 90f)
        val pitchY = bottom - (pitchClamped / 90f) * barH
        val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cCyan; strokeWidth = 3f; style = Paint.Style.STROKE
        }
        canvas.drawLine(left, pitchY, right, pitchY, pp)

        // Indicador de coincidencia (verde si están cerca)
        targetElevation?.let { elev ->
            val diff = abs(pitch - elev)
            if (diff < 3f) {
                val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF00FF88.toInt(); strokeWidth = 5f; style = Paint.Style.STROKE
                }
                val y = bottom - (elev.coerceIn(0f, 90f) / 90f) * barH
                canvas.drawLine(left, y, right, y, gp)
            }
        }
    }

    // ── Modo vertical ─────────────────────────────────────────────────────────

    private fun drawVerticalHud(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val pad = w * 0.05f
        val stripTop = h * 0.30f; val stripBottom = h * 0.50f

        ringPaint.color = cGrid
        canvas.drawRect(pad, stripTop, w - pad, stripBottom, ringPaint)

        readoutPaint.textSize = h * 0.12f
        canvas.drawText("%03.0f°".format(azimuth), w / 2f, stripTop - h * 0.04f, readoutPaint)
        readoutSubPaint.textSize = h * 0.04f
        canvas.drawText(cardinalLabel(azimuth), w / 2f, stripTop - h * 0.005f, readoutSubPaint)

        val degSpan = 120
        val pxPerDeg = (w - 2 * pad) / degSpan
        cardinalPaint.textSize = h * 0.045f
        degPaint.textSize = h * 0.028f

        for (offset in -degSpan / 2..degSpan / 2) {
            val deg = ((azimuth + offset).toInt() % 360 + 360) % 360
            val x = w / 2f + offset * pxPerDeg
            val isMajor = deg % 30 == 0
            val tickH = if (isMajor) (stripBottom - stripTop) * 0.55f else (stripBottom - stripTop) * 0.25f
            canvas.drawLine(x, stripTop, x, stripTop + tickH, if (isMajor) majorTickPaint else tickPaint)
            if (isMajor) {
                val label = when (deg) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> deg.toString() }
                cardinalPaint.color = if (deg == 0) cCyan else cText
                canvas.drawText(label, x, stripBottom + h * 0.045f, cardinalPaint)
            }
        }

        // Aguja central
        needlePaint.color = cRed
        val ind = Path().apply {
            moveTo(w / 2f, stripTop - h * 0.012f)
            lineTo(w / 2f - h * 0.02f, stripTop - h * 0.04f)
            lineTo(w / 2f + h * 0.02f, stripTop - h * 0.04f)
            close()
        }
        canvas.drawPath(ind, needlePaint)

        // Triángulo objetivo
        targetBearing?.let { bearing ->
            val diff = ((bearing - azimuth + 540f) % 360f) - 180f
            if (diff in -degSpan / 2f..degSpan / 2f) {
                val tx = w / 2f + diff * pxPerDeg
                val t = Path().apply {
                    moveTo(tx, stripBottom + h * 0.012f)
                    lineTo(tx - h * 0.02f, stripBottom + h * 0.04f)
                    lineTo(tx + h * 0.02f, stripBottom + h * 0.04f)
                    close()
                }
                canvas.drawPath(t, targetPaint)
            } else {
                val arrowX = if (diff < 0) pad + h * 0.03f else w - pad - h * 0.03f
                val arrow = Path().apply {
                    if (diff < 0) {
                        moveTo(arrowX - h * 0.02f, (stripTop + stripBottom) / 2f)
                        lineTo(arrowX + h * 0.02f, (stripTop + stripBottom) / 2f - h * 0.025f)
                        lineTo(arrowX + h * 0.02f, (stripTop + stripBottom) / 2f + h * 0.025f)
                    } else {
                        moveTo(arrowX + h * 0.02f, (stripTop + stripBottom) / 2f)
                        lineTo(arrowX - h * 0.02f, (stripTop + stripBottom) / 2f - h * 0.025f)
                        lineTo(arrowX - h * 0.02f, (stripTop + stripBottom) / 2f + h * 0.025f)
                    }
                    close()
                }
                canvas.drawPath(arrow, targetPaint)
            }
        }

        // Línea de horizonte / pitch
        val horizonY = h * 0.75f + (pitch.coerceIn(-45f, 45f) / 45f) * h * 0.10f
        majorTickPaint.color = cCyan
        canvas.drawLine(pad, horizonY, w - pad, horizonY, majorTickPaint)
        readoutSubPaint.textSize = h * 0.03f
        canvas.drawText("PITCH %+.1f°".format(pitch), w / 2f, horizonY - h * 0.012f, readoutSubPaint)

        // Línea de elevación objetivo (ámbar) en modo vertical
        targetElevation?.let { elev ->
            val elevY = h * 0.75f - (elev.coerceIn(0f, 90f) / 90f) * h * 0.20f
            val ep = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cAmber; strokeWidth = 3f }
            canvas.drawLine(pad, elevY, w - pad, elevY, ep)
            readoutSubPaint.textSize = h * 0.03f
            readoutSubPaint.color = cAmber
            canvas.drawText("EL %.0f°".format(elev), w / 2f, elevY - h * 0.012f, readoutSubPaint)
            readoutSubPaint.color = cText2
        }

        distanceMeters?.let {
            readoutPaint.textSize = h * 0.05f
            canvas.drawText(distString(it), w / 2f, h * 0.93f, readoutPaint)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun drawCardinal(c: Canvas, txt: String, cx: Float, cy: Float, r: Float, deg: Double, color: Int) {
        val a = Math.toRadians(deg)
        cardinalPaint.color = color
        c.drawText(txt, cx + r * sin(a).toFloat(), cy - r * cos(a).toFloat() + cardinalPaint.textSize / 3f, cardinalPaint)
    }

    private fun cardinalLabel(az: Float): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return dirs[((az + 22.5f) / 45f).toInt() % 8]
    }

    private fun distString(m: Double): String =
        if (m >= 1000) "%.2f km".format(m / 1000.0) else "%.0f m".format(m)
}
