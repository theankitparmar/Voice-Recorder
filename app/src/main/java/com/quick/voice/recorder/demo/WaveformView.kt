package com.quick.voice.recorder.demo

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.quick.voice.recorder.R
import kotlinx.coroutines.*
import kotlin.math.min
import kotlin.math.max

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Real-time recording mode data
    private val realtimeChunkHeights = ArrayList<ChunkData>()

    // Audio file mode data
    private var audioFileAmplitudes: List<Float> = emptyList()
    private var isAudioFileMode = false

    private var chunkAlignTo: AlignTo = AlignTo.CENTER
    private var chunkColor: Int = Color.parseColor("#6200EE")
    private var chunkMaxHeight: Float = UNINITIALIZED
    private var chunkMinHeight: Float = dp(3)
    private val chunkPaint = Paint()
    private var chunkRoundedCorners: Boolean = true
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
    private var waveHeightMultiplier: Float = 1.0f
    private var glowEffect: Boolean = true
    private var glowRadius: Float = dp(4)

    private var gradientShader: LinearGradient? = null
    private val glowPaint = Paint()

    private val audioExtractor = AudioWaveformExtractor(context)
    private var extractionJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Loading state
    private var isLoading = false
    private val loadingPaint = Paint().apply {
        color = Color.GRAY
        textSize = sp(16)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

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

        setLayerType(LAYER_TYPE_SOFTWARE, null)
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

            setWillNotDraw(false)
            setChunkRoundedCorners(chunkRoundedCorners)
            setChunkWidth(chunkWidth)
            setChunkColor(chunkColor)
        } finally {
            typedArray.recycle()
        }
    }

    // Public API for audio file loading
    fun loadAudioFile(
        audioFilePath: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        isLoading = true
        isAudioFileMode = true
        invalidate()

        extractionJob?.cancel()
        extractionJob = coroutineScope.launch {
            try {
                val targetSamples = calculateTargetSamples()
                val amplitudes = audioExtractor.extractWaveform(audioFilePath, targetSamples)

                if (amplitudes.isNotEmpty()) {
                    audioFileAmplitudes = amplitudes
                    isLoading = false
                    invalidate()
                    onComplete?.invoke(true)
                    Log.d(TAG, "Loaded \${amplitudes.size} amplitude samples")
                } else {
                    isLoading = false
                    invalidate()
                    onComplete?.invoke(false)
                    Log.e(TAG, "No amplitudes extracted")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading audio file: \${e.message}", e)
                isLoading = false
                invalidate()
                onComplete?.invoke(false)
            }
        }
    }

    private fun calculateTargetSamples(): Int {
        if (width == 0) return 500
        val chunkSpacing = chunkWidth + chunkSpace
        return (width / chunkSpacing).toInt().coerceAtLeast(100)
    }

    // Public API for customization
    fun setGradientColors(startColor: Int, endColor: Int) {
        gradientStartColor = startColor
        gradientEndColor = endColor
        useGradient = true
        gradientShader = null
        invalidate()
    }

    fun setWaveHeightMultiplier(multiplier: Float) {
        waveHeightMultiplier = multiplier.coerceIn(0.5f, 2.0f)
        chunkMaxHeight = UNINITIALIZED // Reset to recalculate
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
        if (isAudioFileMode && audioFileAmplitudes.isNotEmpty()) {
            invalidate()
        }
    }

    fun setChunkRoundedCorners(rounded: Boolean) {
        chunkPaint.strokeCap = if (rounded) Paint.Cap.ROUND else Paint.Cap.BUTT
        glowPaint.strokeCap = Paint.Cap.ROUND
        chunkRoundedCorners = rounded
    }

    fun recreate() {
        realtimeChunkHeights.clear()
        audioFileAmplitudes = emptyList()
        isAudioFileMode = false
        invalidate()
    }

    fun clearAudioFile() {
        extractionJob?.cancel()
        audioFileAmplitudes = emptyList()
        isAudioFileMode = false
        isLoading = false
        invalidate()
    }

    // For real-time recording
    fun update(amplitude: Int, color: Int = chunkColor) {
        if (height == 0) {
            Log.w(TAG, "View must be displayed before calling update")
            return
        }

        if (isAudioFileMode) {
            Log.w(TAG, "Cannot update in audio file mode. Call recreate() first.")
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

        if (realtimeChunkHeights.size >= maxChunks && maxChunks > 0) {
            realtimeChunkHeights.removeAt(0)
        }

        if (chunkMaxHeight == UNINITIALIZED) {
            chunkMaxHeight = (height - (topBottomPadding * 2)) * waveHeightMultiplier
        }

        val heightRange = chunkMaxHeight - chunkMinHeight
        if (heightRange != 0f) {
            val scaleFactor = maxReportableAmp / heightRange
            if (scaleFactor != 0f) {
                var calculatedHeight = (amplitude / scaleFactor) * waveHeightMultiplier

                if (realtimeChunkHeights.isNotEmpty()) {
                    val previousHeight = realtimeChunkHeights.last().height - chunkMinHeight
                    val timeDelta = System.currentTimeMillis() - lastUpdateTime
                    calculatedHeight = softTransition(
                        calculatedHeight,
                        previousHeight,
                        2.2f,
                        calculateScaleFactor(timeDelta)
                    )
                }

                val finalHeight = (calculatedHeight + chunkMinHeight).coerceIn(chunkMinHeight, chunkMaxHeight)
                realtimeChunkHeights.add(ChunkData(finalHeight, color, 255))
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (useGradient && h > 0) {
            createGradientShader(h)
        }

        chunkMaxHeight = UNINITIALIZED

        // Reload audio file with new dimensions if in audio file mode
        if (isAudioFileMode && audioFileAmplitudes.isNotEmpty() && w > 0) {
            // Recalculate for new width
            invalidate()
        }
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

        if (isLoading) {
            drawLoadingState(canvas)
            return
        }

        if (useGradient && gradientShader == null && height > 0) {
            createGradientShader(height)
        }

        if (isAudioFileMode) {
            drawAudioFileWaveform(canvas)
        } else {
            drawRealtimeWaveform(canvas)
        }
    }

    private fun drawLoadingState(canvas: Canvas) {
        val text = "Loading waveform..."
        canvas.drawText(text, width / 2f, height / 2f, loadingPaint)
    }

    private fun drawAudioFileWaveform(canvas: Canvas) {
        if (audioFileAmplitudes.isEmpty()) return

        if (chunkMaxHeight == UNINITIALIZED) {
            chunkMaxHeight = (height - (topBottomPadding * 2)) * waveHeightMultiplier
        }

        val chunkSpacing = chunkWidth + chunkSpace
        val availableWidth = width.toFloat()
        val totalChunks = audioFileAmplitudes.size

        // Calculate spacing to fit all samples
        val actualSpacing = if (totalChunks > 0) {
            min(chunkSpacing, availableWidth / totalChunks)
        } else {
            chunkSpacing
        }

        if (chunkAlignTo == AlignTo.BOTTOM) {
            drawAudioFileAlignBottom(canvas, actualSpacing)
        } else {
            drawAudioFileAlignCenter(canvas, actualSpacing)
        }
    }

    private fun drawAudioFileAlignCenter(canvas: Canvas, spacing: Float) {
        val centerY = height / 2f

        for (i in audioFileAmplitudes.indices) {
            val normalizedAmplitude = audioFileAmplitudes[i]
            val barHeight = (normalizedAmplitude * chunkMaxHeight).coerceAtLeast(chunkMinHeight)

            val chunkX = i * spacing + spacing / 2
            val halfHeight = barHeight / 2
            val startY = centerY - halfHeight
            val stopY = centerY + halfHeight

            // Calculate alpha based on position for fade effect
            val fadeAlpha = calculateFadeAlpha(i, audioFileAmplitudes.size)

            // Draw glow effect
            if (glowEffect) {
                glowPaint.apply {
                    if (useGradient) {
                        shader = gradientShader
                    } else {
                        shader = null
                        color = chunkColor
                    }
                    alpha = (fadeAlpha * 0.6f).toInt()
                }
                canvas.drawLine(chunkX, startY, chunkX, stopY, glowPaint)
            }

            // Draw main bar
            chunkPaint.apply {
                if (useGradient) {
                    shader = gradientShader
                } else {
                    shader = null
                    color = chunkColor
                }
                alpha = fadeAlpha
            }
            canvas.drawLine(chunkX, startY, chunkX, stopY, chunkPaint)
        }
    }

    private fun drawAudioFileAlignBottom(canvas: Canvas, spacing: Float) {
        val bottomY = height - topBottomPadding

        for (i in audioFileAmplitudes.indices) {
            val normalizedAmplitude = audioFileAmplitudes[i]
            val barHeight = (normalizedAmplitude * chunkMaxHeight).coerceAtLeast(chunkMinHeight)

            val chunkX = i * spacing + spacing / 2
            val topY = bottomY - barHeight

            val fadeAlpha = calculateFadeAlpha(i, audioFileAmplitudes.size)

            // Draw glow effect
            if (glowEffect) {
                glowPaint.apply {
                    if (useGradient) {
                        shader = gradientShader
                    } else {
                        shader = null
                        color = chunkColor
                    }
                    alpha = (fadeAlpha * 0.6f).toInt()
                }
                canvas.drawLine(chunkX, bottomY, chunkX, topY, glowPaint)
            }

            // Draw main bar
            chunkPaint.apply {
                if (useGradient) {
                    shader = gradientShader
                } else {
                    shader = null
                    color = chunkColor
                }
                alpha = fadeAlpha
            }
            canvas.drawLine(chunkX, bottomY, chunkX, topY, chunkPaint)
        }
    }

    private fun drawRealtimeWaveform(canvas: Canvas) {
        if (realtimeChunkHeights.isEmpty()) return

        val chunkSpacing = chunkWidth + chunkSpace

        if (chunkAlignTo == AlignTo.BOTTOM) {
            drawRealtimeAlignBottom(canvas, chunkSpacing)
        } else {
            drawRealtimeAlignCenter(canvas, chunkSpacing)
        }
    }

    private fun drawRealtimeAlignCenter(canvas: Canvas, chunkSpacing: Float) {
        val centerY = height / 2f

        for (i in realtimeChunkHeights.indices) {
            val chunkX = getChunkX(i, chunkSpacing)
            val halfHeight = realtimeChunkHeights[i].height / 2
            val startY = centerY - halfHeight
            val stopY = centerY + halfHeight

            val fadeAlpha = calculateFadeAlpha(i, realtimeChunkHeights.size)

            // Draw glow effect
            if (glowEffect) {
                glowPaint.apply {
                    if (useGradient) {
                        shader = gradientShader
                    } else {
                        shader = null
                        color = realtimeChunkHeights[i].color
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
                    color = realtimeChunkHeights[i].color
                }
                alpha = fadeAlpha
            }
            canvas.drawLine(chunkX, startY, chunkX, stopY, chunkPaint)
        }
    }

    private fun drawRealtimeAlignBottom(canvas: Canvas, chunkSpacing: Float) {
        val bottomY = height - topBottomPadding

        for (i in realtimeChunkHeights.indices) {
            val chunkX = getChunkX(i, chunkSpacing)
            val topY = bottomY - realtimeChunkHeights[i].height

            val fadeAlpha = calculateFadeAlpha(i, realtimeChunkHeights.size)

            // Draw glow effect
            if (glowEffect) {
                glowPaint.apply {
                    if (useGradient) {
                        shader = gradientShader
                    } else {
                        shader = null
                        color = realtimeChunkHeights[i].color
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
                    color = realtimeChunkHeights[i].color
                }
                alpha = fadeAlpha
            }
            canvas.drawLine(chunkX, bottomY, chunkX, topY, chunkPaint)
        }
    }

    private fun calculateFadeAlpha(index: Int, totalChunks: Int): Int {
        if (totalChunks <= 1) return 255

        val fadePercentage = index.toFloat() / (totalChunks - 1)
        val minAlpha = 100
        val maxAlpha = 255
        return (minAlpha + (maxAlpha - minAlpha) * fadePercentage).toInt()
    }

    private fun getChunkX(index: Int, chunkSpacing: Float): Float {
        return if (direction == Direction.RightToLeft) {
            width - (chunkSpacing * (realtimeChunkHeights.size - index))
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

    private fun sp(value: Int): Float {
        return value * context.resources.displayMetrics.scaledDensity
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        extractionJob?.cancel()
        coroutineScope.cancel()
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
