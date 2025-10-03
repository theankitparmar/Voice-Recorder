package com.quick.voice.recorder.ui.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.quick.voice.recorder.R
import kotlin.math.max
import kotlin.math.min

class WaveformPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var waveformData: FloatArray? = null
    private var waveformColor: Int = Color.BLUE
    private var backgroundColor: Int = Color.TRANSPARENT
    private var cornerRadius: Float = 0f

    init {
        setupAttributes(attrs)
    }

    private fun setupAttributes(attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.WaveformPreviewView)
        
        waveformColor = typedArray.getColor(
            R.styleable.WaveformPreviewView_waveformColor,
            ContextCompat.getColor(context, R.color.waveform_preview_color)
        )
        
        backgroundColor = typedArray.getColor(
            R.styleable.WaveformPreviewView_waveformBackgroundColor,
            Color.TRANSPARENT
        )
        
        cornerRadius = typedArray.getDimension(
            R.styleable.WaveformPreviewView_waveformCornerRadius,
            4f
        )
        
        typedArray.recycle()
        
        paint.color = waveformColor
    }

    fun setWaveformData(data: FloatArray?) {
        waveformData = data
        invalidate()
    }

    fun setWaveformColor(color: Int) {
        waveformColor = color
        paint.color = color
        invalidate()
    }

    fun generateRandomWaveform(duration: Long) {
        // Generate random waveform data for demonstration
        val pointCount = (width / 4).coerceAtLeast(20)
        val randomData = FloatArray(pointCount) { 
            (Math.random().toFloat() * 0.8f + 0.2f) * (if (it % 3 == 0) 0.6f else 1.0f)
        }
        setWaveformData(randomData)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw background with rounded corners
        if (backgroundColor != Color.TRANSPARENT) {
            val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = backgroundColor
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                0f, 0f, width.toFloat(), height.toFloat(),
                cornerRadius, cornerRadius, backgroundPaint
            )
        }

        val data = waveformData
        if (data!!.isEmpty()) {
            drawPlaceholderWaveform(canvas)
            return
        }

        drawWaveform(canvas, data)
    }

    private fun drawPlaceholderWaveform(canvas: Canvas) {
        val placeholderData = FloatArray(15) { index ->
            when (index % 5) {
                0 -> 0.3f
                1 -> 0.6f
                2 -> 0.8f
                3 -> 0.5f
                else -> 0.4f
            }
        }
        drawWaveform(canvas, placeholderData)
    }

    private fun drawWaveform(canvas: Canvas, data: FloatArray) {
        val centerY = height / 2f
        val barWidth = width.toFloat() / data.size
        val maxBarHeight = height * 0.8f

        // Create a gradient for the waveform
        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            waveformColor,
            adjustAlpha(waveformColor, 0.6f),
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient

        for (i in data.indices) {
            val amplitude = data[i].coerceIn(0.1f, 1.0f)
            val barHeight = amplitude * maxBarHeight
            val left = i * barWidth
            val top = centerY - barHeight / 2
            val right = left + barWidth * 0.8f // Leave some gap between bars
            val bottom = centerY + barHeight / 2

            // Draw rounded bars for modern look
            canvas.drawRoundRect(
                left, top, right, bottom,
                barWidth * 0.3f, barWidth * 0.3f,
                paint
            )
        }

        paint.shader = null
    }

    private fun adjustAlpha(color: Int, alpha: Float): Int {
        val alphaInt = (alpha * 255).toInt().coerceIn(0, 255)
        return color and 0x00FFFFFF or (alphaInt shl 24)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Generate initial placeholder if no data is set
        if (waveformData == null) {
            generateRandomWaveform(0)
        }
    }
}