package com.quick.voice.recorder.demo

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.quick.voice.recorder.R
import kotlin.math.min
import kotlin.math.max

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var chunkAlignTo: AlignTo = AlignTo.CENTER
    private var chunkColor: Int = Color.parseColor("#6200EE")
    private val chunkHeights = ArrayList<ChunkData>()
    private var chunkMaxHeight: Float = UNINITIALIZED
    private var chunkMinHeight: Float = dp(3)
    private val chunkPaint = Paint()
    private var chunkRoundedCorners: Boolean = true
    private var chunkSoftTransition: Boolean = true
    private var chunkSpace: Float = dp(1.5f)
    private var chunkWidth: Float = dp(3)
    private var direction: Direction = Direction.LeftToRight
    private var lastUpdateTime: Long = 0
    private val maxReportableAmp: Float = 22760.0f
    private var topBottomPadding: Float = dp(8)

    // Enhanced features
    private var useGradient: Boolean = true
    private var gradientStartColor: Int = Color.parseColor("#6200EE")
    private var gradientEndColor: Int = Color.parseColor("#03DAC5")
    private var waveHeightMultiplier: Float = 2.0f
    private var glowEffect: Boolean = true
    private var glowRadius: Float = dp(4)

    private var gradientShader: LinearGradient? = null
    private val glowPaint = Paint()

    init {
        attrs?.let { initAttrs(it) } ?: initDefault()
        setupPaints()
    }

    private fun setupPaints() {
        chunkPaint.apply {
            isAntiAlias = true
            strokeWidth = chunkWidth
            strokeCap = if (chunkRoundedCorners) Paint.Cap.ROUND else Paint.Cap.BUTT
            style = Paint.Style.STROKE
        }

        glowPaint.apply {
            isAntiAlias = true
            strokeWidth = chunkWidth + dp(2)
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
            alpha = 60
            maskFilter = BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL)
        }

        setLayerType(LAYER_TYPE_SOFTWARE, null) // Enable blur effects
    }

    private fun initDefault() {
        chunkPaint.strokeWidth = chunkWidth
        chunkPaint.color = chunkColor
    }

    private fun initAttrs(attrs: AttributeSet) {
        val typedArray = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.AudioRecordView,
            0, 0
        )

        try {
            chunkSpace = typedArray.getDimension(R.styleable.AudioRecordView_chunkSpaceDim, chunkSpace)
            chunkMaxHeight = typedArray.getDimension(R.styleable.AudioRecordView_chunkMaxHeightDim, chunkMaxHeight)
            chunkMinHeight = typedArray.getDimension(R.styleable.AudioRecordView_chunkMinHeightDim, chunkMinHeight)
            chunkRoundedCorners = typedArray.getBoolean(R.styleable.AudioRecordView_chunkRoundedCornersDim, true)
            chunkWidth = typedArray.getDimension(R.styleable.AudioRecordView_chunkWidthDim, chunkWidth)
            chunkColor = typedArray.getColor(R.styleable.AudioRecordView_chunkColorDim, chunkColor)

            val alignValue = typedArray.getInt(R.styleable.AudioRecordView_chunkAlignToDim, 0)
            chunkAlignTo = if (alignValue == 2) AlignTo.BOTTOM else AlignTo.CENTER

            val dirValue = typedArray.getInt(R.styleable.AudioRecordView_directionDim, 2)
            direction = if (dirValue == 1) Direction.RightToLeft else Direction.LeftToRight

            chunkSoftTransition = typedArray.getBoolean(R.styleable.AudioRecordView_chunkSoftTransitionDim, true)

            setWillNotDraw(false)
            setChunkRoundedCorners(chunkRoundedCorners)
            setChunkWidth(chunkWidth)
            setChunkColor(chunkColor)
        } finally {
            typedArray.recycle()
        }
    }

    // Public API for customization
    fun setGradientColors(startColor: Int, endColor: Int) {
        gradientStartColor = startColor
        gradientEndColor = endColor
        useGradient = true
        gradientShader = null // Reset shader to recalculate
        invalidate()
    }

    fun setWaveHeightMultiplier(multiplier: Float) {
        waveHeightMultiplier = multiplier.coerceIn(0.5f, 2.0f)
        invalidate()
    }

    fun setGlowEffect(enabled: Boolean) {
        glowEffect = enabled
        invalidate()
    }

    fun setChunkColor(color: Int) {
        chunkColor = color
        useGradient = false
        invalidate()
    }

    fun setChunkWidth(width: Float) {
        chunkPaint.strokeWidth = width
        glowPaint.strokeWidth = width + dp(2)
        chunkWidth = width
    }

    fun setChunkRoundedCorners(rounded: Boolean) {
        chunkPaint.strokeCap = if (rounded) Paint.Cap.ROUND else Paint.Cap.BUTT
        glowPaint.strokeCap = Paint.Cap.ROUND
        chunkRoundedCorners = rounded
    }

    fun recreate() {
        chunkHeights.clear()
        invalidate()
    }

    fun update(amplitude: Int, color: Int = chunkColor) {
        if (height == 0) {
            Log.w(TAG, "View must be displayed before calling update")
            return
        }

        try {
            handleNewAmplitude(amplitude, color)
            invalidate()
            setWillNotDraw(false)
            lastUpdateTime = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating waveform: ${e.message}", e)
        }
    }

    private fun handleNewAmplitude(amplitude: Int, color: Int) {
        val chunkSpacing = chunkWidth + chunkSpace
        val maxChunks = (width / chunkSpacing).toInt()

        // Remove oldest chunk if we've reached max capacity
        if (chunkHeights.size >= maxChunks && maxChunks > 0) {
            chunkHeights.removeAt(0)
        }

        // Initialize max height if needed
        if (chunkMaxHeight == UNINITIALIZED) {
            chunkMaxHeight = (height - (topBottomPadding * 2)) * waveHeightMultiplier
        } else if (chunkMaxHeight > height - (topBottomPadding * 2)) {
            chunkMaxHeight = (height - (topBottomPadding * 2)) * waveHeightMultiplier
        }

        val heightRange = chunkMaxHeight - chunkMinHeight
        if (heightRange != 0f) {
            val scaleFactor = maxReportableAmp / heightRange
            if (scaleFactor != 0f) {
                var calculatedHeight = (amplitude / scaleFactor) * waveHeightMultiplier

                if (chunkSoftTransition && chunkHeights.isNotEmpty()) {
                    val previousHeight = chunkHeights.last().height - chunkMinHeight
                    val timeDelta = System.currentTimeMillis() - lastUpdateTime
                    calculatedHeight = softTransition(
                        calculatedHeight,
                        previousHeight,
                        2.2f,
                        calculateScaleFactor(timeDelta)
                    )
                }

                val finalHeight = (calculatedHeight + chunkMinHeight).coerceIn(chunkMinHeight, chunkMaxHeight)

                // Add new chunk
                chunkHeights.add(ChunkData(finalHeight, color, 255))
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Recalculate gradient when size changes
        if (useGradient && h > 0) {
            createGradientShader(h)
        }
        // Clear chunks on size change to prevent index issues
        chunkHeights.clear()
    }

    private fun createGradientShader(height: Int) {
        gradientShader = LinearGradient(
            0f, 0f,
            0f, height.toFloat(),
            intArrayOf(gradientStartColor, gradientEndColor),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (useGradient && gradientShader == null && height > 0) {
            createGradientShader(height)
        }

        drawChunks(canvas)
    }

    private fun drawChunks(canvas: Canvas) {
        if (chunkAlignTo == AlignTo.BOTTOM) {
            drawAlignBottom(canvas)
        } else {
            drawAlignCenter(canvas)
        }
    }

    private fun drawAlignCenter(canvas: Canvas) {
        val centerY = height / 2f
        val chunkSpacing = chunkWidth + chunkSpace

        for (i in chunkHeights.indices) {
            val chunkX = getChunkX(i, chunkSpacing)
            val halfHeight = chunkHeights[i].height / 2
            val startY = centerY - halfHeight
            val stopY = centerY + halfHeight

            // Apply fade effect to older chunks
            val fadeAlpha = calculateFadeAlpha(i, chunkHeights.size)

            // Draw glow effect first
            if (glowEffect) {
                glowPaint.apply {
                    if (useGradient) {
                        shader = gradientShader
                    } else {
                        shader = null
                        color = chunkHeights[i].color
                    }
                    alpha = (fadeAlpha * 0.6f).toInt()
                }
                canvas.drawLine(chunkX, startY, chunkX, stopY, glowPaint)
            }

            // Draw main chunk
            chunkPaint.apply {
                if (useGradient) {
                    shader = gradientShader
                } else {
                    shader = null
                    color = chunkHeights[i].color
                }
                alpha = fadeAlpha
            }
            canvas.drawLine(chunkX, startY, chunkX, stopY, chunkPaint)
        }
    }

    private fun drawAlignBottom(canvas: Canvas) {
        val chunkSpacing = chunkWidth + chunkSpace

        for (i in chunkHeights.indices) {
            val chunkX = getChunkX(i, chunkSpacing)
            val bottomY = height - topBottomPadding
            val topY = bottomY - chunkHeights[i].height

            val fadeAlpha = calculateFadeAlpha(i, chunkHeights.size)

            // Draw glow effect
            if (glowEffect) {
                glowPaint.apply {
                    if (useGradient) {
                        shader = gradientShader
                    } else {
                        shader = null
                        color = chunkHeights[i].color
                    }
                    alpha = (fadeAlpha * 0.6f).toInt()
                }
                canvas.drawLine(chunkX, bottomY, chunkX, topY, glowPaint)
            }

            // Draw main chunk
            chunkPaint.apply {
                if (useGradient) {
                    shader = gradientShader
                } else {
                    shader = null
                    color = chunkHeights[i].color
                }
                alpha = fadeAlpha
            }
            canvas.drawLine(chunkX, bottomY, chunkX, topY, chunkPaint)
        }
    }

    private fun calculateFadeAlpha(index: Int, totalChunks: Int): Int {
        if (totalChunks <= 1) return 255

        // Fade older chunks (left side) gradually
        val fadePercentage = index.toFloat() / (totalChunks - 1)
        val minAlpha = 100
        val maxAlpha = 255
        return (minAlpha + (maxAlpha - minAlpha) * fadePercentage).toInt()
    }

    private fun getChunkX(index: Int, chunkSpacing: Float): Float {
        return if (direction == Direction.RightToLeft) {
            width - (chunkSpacing * (chunkHeights.size - index))
        } else {
            chunkSpacing * index
        }
    }

    private fun calculateScaleFactor(timeDelta: Long): Float {
        return min(timeDelta / 75.0f, 1.0f)
    }

    private fun softTransition(newValue: Float, previousValue: Float, speed: Float, scaleFactor: Float): Float {
        return previousValue + ((newValue - previousValue) / speed) * scaleFactor
    }

    private fun dp(value: Int): Float {
        return value * context.resources.displayMetrics.density
    }

    private fun dp(value: Float): Float {
        return value * context.resources.displayMetrics.density
    }

    data class ChunkData(
        val height: Float,
        val color: Int,
        val alpha: Int = 255
    )

    enum class AlignTo(val value: Int) {
        CENTER(0),
        BOTTOM(1)
    }

    enum class Direction(val value: Int) {
        LeftToRight(0),
        RightToLeft(1)
    }

    companion object {
        private const val TAG = "WaveformView"
        private const val UNINITIALIZED = 10f
    }
}
