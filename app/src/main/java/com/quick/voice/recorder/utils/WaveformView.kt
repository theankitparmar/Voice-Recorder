package com.quick.voice.recorder.utils

import android.R.attr.bottom
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.quick.voice.recorder.R


// Extension function to convert dp to pixels
fun Context.dpToPx(dp: Int): Float =
    dp * resources.displayMetrics.density

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val LOG_TAG = "WaveformView"

    private var chunkAlignTo: AlignTo
    private var chunkColor: Int
    private val chunkHeights: ArrayList<WidthData>
    private var chunkMaxHeight: Float
    private var chunkMinHeight: Float
    private val chunkPaint: Paint
    private var chunkRoundedCorners: Boolean
    private var chunkSoftTransition: Boolean
    private var chunkSpace: Float
    private var chunkWidth: Float
    private val chunkWidths: ArrayList<Float> // Stores the x-coordinates of each chunk's right edge
    private var direction: Direction
    private var lastUpdateTime: Long = 0L

    private val maxReportableAmp: Float = 32768.0f // Max amplitude for 16-bit audio
    private var topBottomPadding: Float
    private val uninitializedValue = -1.0f // Using -1f as sentinel for uninitialized max height
    private var usageWidth: Float = 0.0f // Tracks the total width occupied by drawn chunks


    private var scrollOffset: Float = 0f // Tracks horizontal scroll position
    private var isScrollingMode: Boolean = false // Enable/disable scrolling
    private val centerLineColor: Int = ContextCompat.getColor(context, R.color.error) // Red line color

    init {
        // Default values
        chunkAlignTo = AlignTo.CENTER
        direction = Direction.LeftToRight
        chunkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE // Use stroke for line drawing
            strokeCap = Paint.Cap.BUTT // Default to butt ends
        }
        chunkHeights = ArrayList()
        chunkWidths = ArrayList()
        topBottomPadding = context.dpToPx(6)
        chunkColor = ContextCompat.getColor(context, R.color.primary) // Use a color from your theme
        chunkWidth = context.dpToPx(2)
        chunkSpace = context.dpToPx(1)
        chunkMaxHeight = uninitializedValue // Will be calculated based on view height
        chunkMinHeight = context.dpToPx(3)
        chunkRoundedCorners = false
        chunkSoftTransition = true // Default to soft transition

        // Initialize from attributes if provided
        attrs?.let { initAttrs(it) } ?: initDefaults()

        setWillNotDraw(false) // Allow onDraw to be called
    }

    private val centerLinePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = centerLineColor
        strokeWidth = context.dpToPx(2)
        style = Paint.Style.STROKE
    }

    // Public method to enable scrolling mode
    fun setScrollingMode(enabled: Boolean) {
        isScrollingMode = enabled
        if (enabled) {
            // In scrolling mode, we want to keep adding chunks and scroll left
            scrollOffset = 0f
        }
        invalidate()
    }

    private fun initDefaults() {
        chunkPaint.strokeWidth = this.chunkWidth
        chunkPaint.color = this.chunkColor
    }

    private fun initAttrs(attrs: AttributeSet) {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.WaveformView, 0, 0)
        try {
            chunkSpace = a.getDimension(R.styleable.WaveformView_chunkSpace2, chunkSpace)
            chunkMaxHeight = a.getDimension(R.styleable.WaveformView_chunkMaxHeight2, chunkMaxHeight)
            chunkMinHeight = a.getDimension(R.styleable.WaveformView_chunkMinHeight2, chunkMinHeight)
            setChunkRoundedCorners(a.getBoolean(R.styleable.WaveformView_chunkRoundedCorners2, chunkRoundedCorners))
            setChunkWidth(a.getDimension(R.styleable.WaveformView_chunkWidth2, chunkWidth))
            setChunkColor(a.getColor(R.styleable.WaveformView_chunkColor2, chunkColor))

            chunkAlignTo = when (a.getInt(R.styleable.WaveformView_chunkAlignTo2, AlignTo.CENTER.value)) {
                AlignTo.BOTTOM.value -> AlignTo.BOTTOM
                else -> AlignTo.CENTER
            }
            direction = when (a.getInt(R.styleable.WaveformView_direction, Direction.LeftToRight.value)) {
                Direction.RightToLeft.value -> Direction.RightToLeft
                else -> Direction.LeftToRight
            }
            chunkSoftTransition = a.getBoolean(R.styleable.WaveformView_chunkSoftTransition2, chunkSoftTransition)

            chunkPaint.isAntiAlias = true
        } finally {
            a.recycle()
        }
    }

    // --- Public Getters/Setters for Customization ---

    fun getChunkAlignTo(): AlignTo? {
        return this.chunkAlignTo
    }

    fun setChunkAlignTo(alignTo: AlignTo?) {
        this.chunkAlignTo = alignTo!!
    }

    fun getDirection(): Direction? {
        return this.direction
    }

    fun setDirection(direction: Direction?) {
        this.direction = direction!!
    }

    fun getChunkSoftTransition(): Boolean {
        return this.chunkSoftTransition
    }

    fun setChunkSoftTransition(chunkSoftTransition: Boolean) {
        this.chunkSoftTransition = chunkSoftTransition
    }

    fun getChunkColor(): Int {
        return this.chunkColor
    }

    fun setChunkColor(chunkColor: Int) {
        this.chunkColor = chunkColor
    }

    fun getChunkWidth(): Float {
        return this.chunkWidth
    }

    fun setChunkWidth(chunkWidth: Float) {
        this.chunkPaint.setStrokeWidth(chunkWidth)
        this.chunkWidth = chunkWidth
    }

    fun getChunkSpace(): Float {
        return this.chunkSpace
    }

    fun setChunkSpace(chunkSpace: Float) {
        this.chunkSpace = chunkSpace
    }

    fun getChunkMaxHeight(): Float {
        return this.chunkMaxHeight
    }

    fun setChunkMaxHeight(chunkMaxHeight: Float) {
        this.chunkMaxHeight = chunkMaxHeight
    }

    fun getChunkMinHeight(): Float {
        return this.chunkMinHeight
    }

    fun setChunkMinHeight(chunkMinHeight: Float) {
        this.chunkMinHeight = chunkMinHeight
    }

    fun getChunkRoundedCorners(): Boolean {
        return this.chunkRoundedCorners
    }

    fun setChunkRoundedCorners(chunkRoundedCorners: Boolean) {
        if (chunkRoundedCorners) {
            this.chunkPaint.setStrokeCap(Paint.Cap.ROUND)
        } else {
            this.chunkPaint.setStrokeCap(Paint.Cap.BUTT)
        }
        this.chunkRoundedCorners = chunkRoundedCorners
    }

    var alignTo: AlignTo
        get() = chunkAlignTo
        set(value) { chunkAlignTo = value; invalidate() }

    var waveformDirection: Direction
        get() = direction
        set(value) { direction = value; invalidate() }

    var softTransition: Boolean
        get() = chunkSoftTransition
        set(value) { chunkSoftTransition = value; invalidate() }

    var waveformColor: Int
        get() = chunkColor
        set(value) {
            chunkColor = value
            chunkPaint.color = value
            invalidate()
        }

    var waveformWidth: Float
        get() = chunkWidth
        set(value) {
            chunkPaint.strokeWidth = value
            chunkWidth = value
            invalidate()
        }

    var waveformSpace: Float
        get() = chunkSpace
        set(value) { chunkSpace = value; invalidate() }

    var waveformMaxHeight: Float
        get() = chunkMaxHeight
        set(value) { chunkMaxHeight = value; invalidate() }

    var waveformMinHeight: Float
        get() = chunkMinHeight
        set(value) { chunkMinHeight = value; invalidate() }

    var roundedCorners: Boolean
        get() = chunkRoundedCorners
        set(value) {
            chunkRoundedCorners = value
            chunkPaint.strokeCap = if (value) Paint.Cap.ROUND else Paint.Cap.BUTT
            invalidate()
        }

    // --- Core Logic ---

    // Modified recreate to reset scroll
    fun recreate() {
        usageWidth = 0.0f
        scrollOffset = 0f
        chunkWidths.clear()
        chunkHeights.clear()
        invalidate()
    }

    /**
     * Updates the waveform with a new amplitude.
     * @param amplitude The raw amplitude value from the audio source (e.g., MediaRecorder.getMaxAmplitude()).
     * @param color The color for this specific chunk. Defaults to the view's `chunkColor` if not provided.
     */
    fun update(amplitude: Int, color: Int = chunkColor) {
        if (height == 0) {
            Log.w(LOG_TAG, "WaveformView is not yet laid out. Call update when the view is displayed.")
            // Post an update for when the view is ready
            post { update(amplitude, color) }
            return
        }

        try {
            handleNewAmplitude(amplitude, color)
            invalidate() // Request a redraw
            lastUpdateTime = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error updating waveform: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        // Recalculate max height if it was uninitialized
        if (chunkMaxHeight == uninitializedValue || chunkMaxHeight > h - (topBottomPadding * 2)) {
            chunkMaxHeight = h - (topBottomPadding * 2)
        }
        if (chunkMaxHeight < 0) chunkMaxHeight = 0f // Ensure it's not negative
    }

    private fun handleNewAmplitude(amplitude: Int, color: Int) {
        if (amplitude == 0 && chunkHeights.isEmpty()) {
            // Don't add a chunk if amplitude is zero and nothing is drawn yet.
            // This prevents a single flat line before actual sound.
            return
        }

        val chunkTotalWidth = chunkWidth + chunkSpace
        val maxVisibleChunks = (width / chunkTotalWidth).toInt()

        // Remove oldest chunk if we exceed max visible chunks
        if (chunkHeights.size >= maxVisibleChunks) {
            chunkHeights.removeAt(0)
            chunkWidths.removeAt(0)
            // Adjust remaining chunk positions if we removed from the start
            for (i in 0 until chunkWidths.size) {
                chunkWidths[i] -= chunkTotalWidth
            }
            usageWidth -= chunkTotalWidth // Update total usage width
        }

        // Add new chunk position
        usageWidth += chunkTotalWidth
        chunkWidths.add(usageWidth)


        // Calculate chunk height based on amplitude
        val actualMaxHeight = chunkMaxHeight - chunkMinHeight
        val scaledAmplitude = if (maxReportableAmp > 0) amplitude / maxReportableAmp else 0f
        var calculatedChunkHeight = chunkMinHeight + (actualMaxHeight * scaledAmplitude)

        // Clamp height within min and max
        calculatedChunkHeight = calculatedChunkHeight.coerceIn(chunkMinHeight, chunkMaxHeight)

        // Apply soft transition
        if (chunkSoftTransition && chunkHeights.isNotEmpty()) {
            val previousHeight = chunkHeights.last().width // .width is actually height
            val scaleFactor = calculateScaleFactor(System.currentTimeMillis() - lastUpdateTime)
            calculatedChunkHeight = softTransition(
                newValue = calculatedChunkHeight - chunkMinHeight, // Transition the varying part
                previousValue = previousHeight - chunkMinHeight,
                speed = 2.2f, // Smoothness factor
                scaleFactor = scaleFactor
            ) + chunkMinHeight // Add minHeight back
        }

        chunkHeights.add(WidthData(calculatedChunkHeight, color))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isScrollingMode) {
            drawScrollingWaveform(canvas)
            drawCenterLine(canvas)
        } else {
            drawWaveformChunks(canvas)
        }
    }

    private fun drawScrollingWaveform(canvas: Canvas) {
        val centerY = height / 2f
        val bottomY = height - topBottomPadding
        val centerX = width / 2f // Red line position
        val chunkTotalWidth = chunkWidth + chunkSpace

        // Calculate how much to scroll: keep the latest chunk at center
        if (chunkWidths.isNotEmpty()) {
            val latestChunkX = chunkWidths.last()
            scrollOffset = latestChunkX - centerX
        }

        for (i in 0 until chunkHeights.size) {
            val chunkX = getScrollingChunkX(i, scrollOffset)

            // Only draw chunks that are visible on screen
            if (chunkX < -chunkWidth || chunkX > width + chunkWidth) continue

            val chunkHeight = chunkHeights[i].width
            chunkPaint.color = chunkHeights[i].color

            when (chunkAlignTo) {
                AlignTo.CENTER -> {
                    val halfHeight = chunkHeight / 2f
                    val startY = centerY - halfHeight
                    val stopY = centerY + halfHeight
                    canvas.drawLine(chunkX, startY, chunkX, stopY, chunkPaint)
                }
                AlignTo.BOTTOM -> {
                    canvas.drawLine(chunkX, bottomY, chunkX, bottomY - chunkHeight, chunkPaint)
                }
            }
        }
    }

    private fun getScrollingChunkX(index: Int, offset: Float): Float {
        val currentChunkRightEdge = chunkWidths[index]
        val currentChunkCenter = currentChunkRightEdge - (chunkSpace / 2) - (chunkWidth / 2)
        return currentChunkCenter - offset
    }

    private fun drawCenterLine(canvas: Canvas) {
        val centerX = width / 2f
        canvas.drawLine(centerX, topBottomPadding, centerX, height - topBottomPadding, centerLinePaint)
    }

    private fun drawWaveformChunks(canvas: Canvas) {
        val centerY = height / 2f
        val bottomY = height - topBottomPadding

        for (i in 0 until chunkHeights.size) {
            val chunkX = getChunkX(i)
            val chunkHeight = chunkHeights[i].width // 'width' field is used for height here
            chunkPaint.color = chunkHeights[i].color

            when (chunkAlignTo) {
                AlignTo.CENTER -> {
                    val halfHeight = chunkHeight / 2f
                    val startY = centerY - halfHeight
                    val stopY = centerY + halfHeight
                    canvas.drawLine(chunkX, startY, chunkX, stopY, chunkPaint)
                }
                AlignTo.BOTTOM -> {
                    canvas.drawLine(chunkX, bottomY, chunkX, bottomY - chunkHeight, chunkPaint)
                }
            }
        }
    }

    private fun getChunkX(index: Int): Float {
        val totalChunkWidth = chunkWidth + chunkSpace
        val currentChunkRightEdge = chunkWidths[index] // This is the right edge of the current chunk in LTR
        val currentChunkCenter = currentChunkRightEdge - (chunkSpace / 2) - (chunkWidth / 2) // Center of the chunk itself

        return when (direction) {
            Direction.LeftToRight -> currentChunkCenter
            Direction.RightToLeft -> width - currentChunkCenter
        }
    }

    private fun calculateScaleFactor(timeDelta: Long): Float {
        return (timeDelta / 75.0f).coerceIn(0.0f, 1.0f)
    }

    private fun softTransition(newValue: Float, previousValue: Float, speed: Float, scaleFactor: Float): Float {
        return previousValue + ((newValue - previousValue) / speed) * scaleFactor
    }

    // Data class for storing chunk height and color
    data class WidthData(val width: Float, val color: Int) // 'width' here means height in the context of vertical lines

    enum class AlignTo(val value: Int) {
        CENTER(0),
        BOTTOM(1);
    }

    enum class Direction(val value: Int) {
        LeftToRight(0),
        RightToLeft(1);
    }
}