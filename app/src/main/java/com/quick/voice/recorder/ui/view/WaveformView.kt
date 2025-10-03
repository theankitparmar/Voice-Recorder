package com.quick.voice.recorder.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.quick.voice.recorder.R

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var waveformData: FloatArray? = null
    private var progress: Float = 0f
    private var waveformColor: Int = Color.BLUE
    private var progressColor: Int = Color.GREEN
    private var backgroundColor: Int = Color.TRANSPARENT
    private var cornerRadius: Float = 0f
    private var showProgress: Boolean = true

    // Animation
    private var isAnimating: Boolean = false
    private var animationPhase: Float = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        addUpdateListener {
            animationPhase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setupAttributes(attrs)
    }

    private fun setupAttributes(attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.WaveformView)
        
        waveformColor = typedArray.getColor(
            R.styleable.WaveformView_waveformColo2,
            ContextCompat.getColor(context, R.color.waveform_color)
        )
        
        progressColor = typedArray.getColor(
            R.styleable.WaveformView_waveformProgressColor,
            ContextCompat.getColor(context, R.color.colorPrimary)
        )
        
        backgroundColor = typedArray.getColor(
            R.styleable.WaveformView_waveformBackgroundColor2,
            ContextCompat.getColor(context, R.color.waveform_background)
        )
        
        cornerRadius = typedArray.getDimension(
            R.styleable.WaveformView_waveformCornerRadius2,
            12f
        )
        
        showProgress = typedArray.getBoolean(
            R.styleable.WaveformView_showProgress,
            true
        )
        
        typedArray.recycle()
        
        waveformPaint.color = waveformColor
        progressPaint.color = progressColor
        backgroundPaint.color = backgroundColor
    }

    fun setWaveformData(data: FloatArray?) {
        waveformData = data
        invalidate()
    }

    fun setProgress(progress: Float) {
        this.progress = progress.coerceIn(0f, 1f)
        invalidate()
    }

    fun setWaveformColor(color: Int) {
        waveformColor = color
        waveformPaint.color = color
        invalidate()
    }

    fun setProgressColor(color: Int) {
        progressColor = color
        progressPaint.color = color
        invalidate()
    }

    fun startAnimation() {
        if (!isAnimating) {
            isAnimating = true
            animator.start()
        }
    }

    fun stopAnimation() {
        if (isAnimating) {
            isAnimating = false
            animator.cancel()
            animationPhase = 0f
            invalidate()
        }
    }

    fun generateDemoWaveform(duration: Long) {
        // Generate realistic-looking waveform data
        val pointCount = (width / 3).coerceAtLeast(50)
        val demoData = FloatArray(pointCount) { index ->
            val position = index.toFloat() / pointCount
            // Create a more natural waveform with peaks and valleys
            val base = (Math.sin(position * Math.PI * 8) * 0.3 + 0.5).toFloat()
            val noise = (Math.random() * 0.2).toFloat()
            val peak = if (position in 0.2f..0.3f || position in 0.6f..0.7f) 0.8f else 1.0f
            (base + noise) * peak * 0.8f
        }
        setWaveformData(demoData)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw background with rounded corners
        if (backgroundColor != Color.TRANSPARENT) {
            canvas.drawRoundRect(
                0f, 0f, width.toFloat(), height.toFloat(),
                cornerRadius, cornerRadius, backgroundPaint
            )
        }

        val data = waveformData
        if (data!!.isNotEmpty()) {
            drawPlaceholderWaveform(canvas)
            return
        }

        drawWaveform(canvas, data)
    }

    private fun drawPlaceholderWaveform(canvas: Canvas) {
        val placeholderData = FloatArray(25) { index ->
            val position = index.toFloat() / 25
            val wave = (Math.sin(position * Math.PI * 6) * 0.4 + 0.5).toFloat()
            wave.coerceIn(0.2f, 0.9f)
        }
        drawWaveform(canvas, placeholderData)
    }

    private fun drawWaveform(canvas: Canvas, data: FloatArray?) {
        val centerY = height / 2f
        val barWidth = width.toFloat() / data!!.size
        val maxBarHeight = height * 0.9f
        val progressWidth = width * progress

        // Create gradients
        val waveformGradient = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            adjustAlpha(waveformColor, 0.7f),
            waveformColor,
            Shader.TileMode.CLAMP
        )
        waveformPaint.shader = waveformGradient

        val progressGradient = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            progressColor,
            adjustAlpha(progressColor, 0.8f),
            Shader.TileMode.CLAMP
        )
        progressPaint.shader = progressGradient

        for (i in data.indices) {
            val amplitude = data[i].coerceIn(0.1f, 1.0f)
            val barHeight = amplitude * maxBarHeight
            val left = i * barWidth
            val top = centerY - barHeight / 2
            val right = left + barWidth * 0.7f
            val bottom = centerY + barHeight / 2

            val barCenterX = (left + right) / 2

            // Choose paint based on progress
            val paint = if (showProgress && barCenterX <= progressWidth) {
                // Add animation effect to progress bars
                if (isAnimating) {
                    val animationIntensity = (Math.sin(animationPhase * Math.PI * 2 + i * 0.3) * 0.1 + 1.0).toFloat()
                    progressPaint.alpha = (255 * animationIntensity).toInt()
                }
                progressPaint
            } else {
                waveformPaint
            }

            // Draw smooth waveform bars with rounded tops
            val path = Path()
            path.moveTo(left, centerY)
            path.lineTo(left, top)
            path.quadTo((left + right) / 2, top - barHeight * 0.1f, right, top)
            path.lineTo(right, bottom)
            path.quadTo((left + right) / 2, bottom + barHeight * 0.1f, left, bottom)
            path.close()

            canvas.drawPath(path, paint)
        }

        // Reset alpha and shaders
        waveformPaint.alpha = 255
        progressPaint.alpha = 255
        waveformPaint.shader = null
        progressPaint.shader = null

        // Draw progress line
        if (showProgress && progress > 0) {
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = progressColor
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }
            canvas.drawLine(progressWidth, 0f, progressWidth, height.toFloat(), linePaint)
        }
    }

    private fun adjustAlpha(color: Int, alpha: Float): Int {
        val alphaInt = (alpha * 255).toInt().coerceIn(0, 255)
        return color and 0x00FFFFFF or (alphaInt shl 24)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldw)
        if (waveformData == null) {
            generateDemoWaveform(0)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isAnimating) {
            startAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}