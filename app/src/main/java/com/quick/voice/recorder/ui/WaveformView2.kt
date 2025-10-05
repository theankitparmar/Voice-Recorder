package com.quick.voice.recorder.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.linc.audiowaveform.model.WaveformAlignment
import com.quick.voice.recorder.R
import kotlin.collections.isNotEmpty

fun Context.dpToPx(dp: Int): Float = dp * resources.displayMetrics.density

class WaveformView2 @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val LOG_TAG = "WaveformView"

    // --- Spike Configuration ---
    private var spikeWidth: Float = context.dpToPx(3)
    private var spikePadding: Float = context.dpToPx(2)
    private var spikeRadius: Float = context.dpToPx(5)
    private var spikeMinHeight: Float = context.dpToPx(8)
    private var spikeMaxHeight: Float = -1f

    // --- Gradient Colors (Custom: Pink to Orange) ---
    private val gradientStartColor = Color.parseColor("#FD1D64") // Pink (starting side)
    private val gradientEndColor = Color.parseColor("#F5BA62")   // Orange (ending side)
    private var gradientShader: LinearGradient? = null

    // --- Primary Color ---
    private val primaryColor = Color.parseColor("#FA5E63") // Primary Red-Pink

    // --- Colors & Paint ---
    private val spikePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // --- Empty Timeline (right side) ---
    private var emptyTimelineColor: Int = Color.parseColor("#30FFFFFF")
    private val emptyTimelinePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // --- Data Storage ---
    private val amplitudes: ArrayList<AmplitudeData> = ArrayList()
    private var maxVisibleSpikes: Int = 0

    // --- Alignment ---
    private var alignment: WaveformAlignment = WaveformAlignment.CENTER

    // --- Scrolling Support ---
    private var scrollOffset: Float = 0f
    private var isScrollingMode: Boolean = false

    // --- Center Recording Line with Gradient ---
    private var showCenterLine: Boolean = false
    private val centerLineColor = Color.parseColor("#AF3444") // Base color
    private var centerLineGradientShader: LinearGradient? = null

    private val centerLinePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Center line circle paint (with 50% alpha)
    private val centerCirclePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#26FD1D40") // 50% alpha
        style = Paint.Style.FILL
    }

    // --- Dim Overlay (top & bottom) ---
    private var showDimOverlay: Boolean = true
    private val dimOverlayPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#26000000")
        style = Paint.Style.FILL
    }
    private var dimOverlayHeight: Float = context.dpToPx(30)

    // --- Audio Processing ---
    private val maxReportableAmp: Float = 32768.0f
    private var lastUpdateTime: Long = 0L
    private var smoothingEnabled: Boolean = true
    private val topBottomPadding: Float = context.dpToPx(8)

    // --- Center Line Position (always center) ---
    private var centerLinePosition: Float = 0.5f

    init {
        attrs?.let { initAttrs(it) } ?: initDefaults()
        setWillNotDraw(false)
    }

    private fun initDefaults() {
        emptyTimelinePaint.color = emptyTimelineColor
    }

    private fun initAttrs(attrs: AttributeSet) {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.WaveformView, 0, 0)
        try {
            spikeWidth = a.getDimension(R.styleable.WaveformView2_chunkWidth, spikeWidth)
            spikePadding = a.getDimension(R.styleable.WaveformView2_chunkSpace, spikePadding)
            spikeMinHeight = a.getDimension(R.styleable.WaveformView2_chunkMinHeight, spikeMinHeight)
            spikeMaxHeight = a.getDimension(R.styleable.WaveformView2_chunkMaxHeight, spikeMaxHeight)
        } finally {
            a.recycle()
        }
    }

    // --- Public Configuration Methods ---

    fun setScrollingMode(enabled: Boolean) {
        isScrollingMode = enabled
        showCenterLine = enabled
        invalidate()
    }

    fun setAlignment(alignment: WaveformAlignment) {
        this.alignment = alignment
        invalidate()
    }

    fun setSpikeStyle(width: Float, padding: Float, radius: Float) {
        this.spikeWidth = width
        this.spikePadding = padding
        this.spikeRadius = radius
        invalidate()
    }

    fun setDimOverlay(enabled: Boolean, height: Float = context.dpToPx(30)) {
        this.showDimOverlay = enabled
        this.dimOverlayHeight = height
        invalidate()
    }

    // --- Core Update Logic ---

    fun update(amplitude: Int) {
        if (height == 0) {
            post { update(amplitude) }
            return
        }

        try {
            handleNewAmplitude(amplitude)
            invalidate()
            lastUpdateTime = System.currentTimeMillis()
        } catch (e: Exception) {
            android.util.Log.e(LOG_TAG, "Error updating waveform: \${e.message}")
        }
    }

    fun recreate() {
        amplitudes.clear()
        scrollOffset = 0f
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)

        if (spikeMaxHeight <= 0 || spikeMaxHeight > h - (topBottomPadding * 2)) {
            spikeMaxHeight = h - (topBottomPadding * 2) - (dimOverlayHeight * 2)
        }

        val totalSpikeWidth = spikeWidth + spikePadding
        maxVisibleSpikes = (w / totalSpikeWidth).toInt() + 1

        // Create vertical gradient shader for waveform (top to bottom: Pink → Orange)
        val topY = topBottomPadding + dimOverlayHeight
        val bottomY = h - topBottomPadding - dimOverlayHeight

        gradientShader = LinearGradient(
            0f, topY,
            0f, bottomY,
            intArrayOf(gradientStartColor, gradientEndColor),
            null,
            Shader.TileMode.CLAMP
        )

        // Create center line gradient shader (50% → 100% → 50% alpha)
        val centerY = h / 2f

        // Colors with different alpha values
        val topColor = Color.parseColor("#80AF3444")    // 50% alpha at top
        val centerColor = Color.parseColor("#FFAF3444") // 100% alpha at center
        val bottomColor = Color.parseColor("#80AF3444") // 50% alpha at bottom

        centerLineGradientShader = LinearGradient(
            0f, topY,
            0f, bottomY,
            intArrayOf(topColor, centerColor, bottomColor),
            floatArrayOf(0f, 0.5f, 1f), // Position: top, center, bottom
            Shader.TileMode.CLAMP
        )
    }

    private fun handleNewAmplitude(amplitude: Int) {
        if (amplitude == 0 && amplitudes.isEmpty()) return

        if (amplitudes.size >= maxVisibleSpikes) {
            amplitudes.removeAt(0)
        }

        val normalizedAmplitude = if (maxReportableAmp > 0) {
            (amplitude.toFloat() / maxReportableAmp).coerceIn(0f, 1f)
        } else 0f

        val heightRange = spikeMaxHeight - spikeMinHeight
        var spikeHeight = spikeMinHeight + (heightRange * normalizedAmplitude)

        if (smoothingEnabled && amplitudes.isNotEmpty()) {
            val previousHeight = amplitudes.last().height
            val timeDelta = System.currentTimeMillis() - lastUpdateTime
            val smoothingFactor = (timeDelta / 75f).coerceIn(0f, 1f)

            spikeHeight = previousHeight + ((spikeHeight - previousHeight) / 2.2f) * smoothingFactor
        }

        spikeHeight = spikeHeight.coerceIn(spikeMinHeight, spikeMaxHeight)

        amplitudes.add(AmplitudeData(spikeHeight))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isScrollingMode) {
            if (showDimOverlay) drawDimOverlay(canvas)
            drawScrollingWaveform(canvas)
            drawEmptyTimeline(canvas)
            if (showCenterLine) drawCenterLine(canvas)
        } else {
            drawStaticWaveform(canvas)
        }
    }

    private fun drawStaticWaveform(canvas: Canvas) {
        val centerY = height / 2f
        val bottomY = height - topBottomPadding
        val topY = topBottomPadding
        val totalSpikeWidth = spikeWidth + spikePadding

        // Apply gradient
        spikePaint.shader = gradientShader

        for (i in amplitudes.indices) {
            val x = i * totalSpikeWidth
            val spikeHeight = amplitudes[i].height

            val rect = when (alignment) {
                WaveformAlignment.CENTER -> {
                    val halfHeight = spikeHeight / 2f
                    RectF(x, centerY - halfHeight, x + spikeWidth, centerY + halfHeight)
                }

                WaveformAlignment.BOTTOM -> {
                    RectF(x, bottomY - spikeHeight, x + spikeWidth, bottomY)
                }

                WaveformAlignment.TOP -> {
                    RectF(x, topY, x + spikeWidth, topY + spikeHeight)
                }
            }

            canvas.drawRoundRect(rect, spikeRadius, spikeRadius, spikePaint)
        }
    }

    private fun drawScrollingWaveform(canvas: Canvas) {
        val centerY = height / 2f
        val bottomY = height - topBottomPadding - dimOverlayHeight
        val topY = topBottomPadding + dimOverlayHeight
        val centerX = width * centerLinePosition
        val totalSpikeWidth = spikeWidth + spikePadding

        // Calculate scroll offset to keep latest spike at center line
        if (amplitudes.isNotEmpty()) {
            scrollOffset = (amplitudes.size * totalSpikeWidth) - centerX
        }

        // Apply gradient shader
        spikePaint.shader = gradientShader

        for (i in amplitudes.indices) {
            val x = (i * totalSpikeWidth) - scrollOffset

            // Only draw visible spikes
            if (x < -spikeWidth || x > width + spikeWidth) continue

            val spikeHeight = amplitudes[i].height

            val rect = when (alignment) {
                WaveformAlignment.CENTER -> {
                    val halfHeight = spikeHeight / 2f
                    RectF(x, centerY - halfHeight, x + spikeWidth, centerY + halfHeight)
                }

                WaveformAlignment.BOTTOM -> {
                    RectF(x, bottomY - spikeHeight, x + spikeWidth, bottomY)
                }

                WaveformAlignment.TOP -> {
                    RectF(x, topY, x + spikeWidth, topY + spikeHeight)
                }
            }

            canvas.drawRoundRect(rect, spikeRadius, spikeRadius, spikePaint)
        }
    }

    /**
     * Draw empty timeline on the right side of center line
     */
    private fun drawEmptyTimeline(canvas: Canvas) {
        val centerY = height / 2f
        val bottomY = height - topBottomPadding - dimOverlayHeight
        val topY = topBottomPadding + dimOverlayHeight
        val centerX = width * (centerLinePosition - context.dpToPx(2))
        val totalSpikeWidth = spikeWidth + spikePadding

        val emptySpikesCount = ((width - centerX) / totalSpikeWidth).toInt()

        emptyTimelinePaint.color = emptyTimelineColor

        for (i in 0 until emptySpikesCount) {
            val x = centerX + (i * totalSpikeWidth) + totalSpikeWidth

            if (x > width) break

            val emptyHeight = spikeMinHeight

            val rect = when (alignment) {
                WaveformAlignment.CENTER -> {
                    val halfHeight = emptyHeight / 2f
                    RectF(x, centerY - halfHeight, x + spikeWidth, centerY + halfHeight)
                }

                WaveformAlignment.BOTTOM -> {
                    RectF(x, bottomY - emptyHeight, x + spikeWidth, bottomY)
                }

                WaveformAlignment.TOP -> {
                    RectF(x, topY, x + spikeWidth, topY + emptyHeight)
                }
            }

            canvas.drawRoundRect(rect, spikeRadius, spikeRadius, emptyTimelinePaint)
        }
    }

    /**
     * Draw center recording indicator line with gradient (50% → 100% → 50% alpha)
     */
    private fun drawCenterLine(canvas: Canvas) {
        val centerX = width / 2f // Always at center
        val topY = topBottomPadding + dimOverlayHeight
        val bottomY = height - topBottomPadding - dimOverlayHeight

        // Apply gradient shader to center line paint
        centerLinePaint.shader = centerLineGradientShader

        // Draw vertical line with gradient and rounded ends
        val lineWidth = context.dpToPx(3)
        val lineRect = RectF(
            centerX - lineWidth / 2,
            topY,
            centerX + lineWidth / 2,
            bottomY
        )
        canvas.drawRoundRect(lineRect, lineWidth / 2, lineWidth / 2, centerLinePaint)

        // Draw circles at top and bottom (with 50% alpha)
        val circleRadius = context.dpToPx(0)
        canvas.drawCircle(centerX, topY, circleRadius, centerCirclePaint)
        canvas.drawCircle(centerX, bottomY, circleRadius, centerCirclePaint)
    }

    /**
     * Draw dim overlay on top and bottom
     */
    private fun drawDimOverlay(canvas: Canvas) {
        // Top dim overlay
        val topRect = RectF(0f, 0f, width.toFloat(), topBottomPadding + dimOverlayHeight)
        canvas.drawRect(topRect, dimOverlayPaint)

        // Bottom dim overlay
        val bottomRect = RectF(
            0f,
            height - topBottomPadding - dimOverlayHeight,
            width.toFloat(),
            height.toFloat()
        )
        canvas.drawRect(bottomRect, dimOverlayPaint)
    }

    data class AmplitudeData(val height: Float)

    enum class WaveformAlignment {
        CENTER, BOTTOM, TOP
    }
}
