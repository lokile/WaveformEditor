package com.bandlab.waveformeditor.customView

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.VisibleForTesting
import com.bandlab.waveformeditor.utils.dpToPixel
import com.bandlab.waveformeditor.utils.handleLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


const val NOT_SELECTED = -1
internal const val SELECTED_START_POINTER = 0
internal const val SELECTED_END_POINTER = 1

private fun log(call: () -> String) {
    handleLogging {
        "[WaveformView] ${call()}"
    }
}


open class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), CoroutineScope {

    protected data class WaveItem(
        val x: Float,
        val topY: Float,
        val bottomY: Float
    )

    internal val POINTER_WIDTH by lazy { 2.5f.dpToPixel(getContext()) }
    internal val TOUCH_EFFECT_AREA by lazy { 20f.dpToPixel(getContext()) }
    internal val MARKER_SIZE by lazy { 15f.dpToPixel(getContext()) }
    internal val HORIZONTAL_PADDING by lazy { 0f.dpToPixel(getContext()) }
    internal val VERTICAL_PADDING by lazy { 9f.dpToPixel(getContext()) }
    internal val NO_DATA_MESSAGE = "NO DATA"

    private val _selectedIndex by lazy {
        MutableStateFlow(NOT_SELECTED to NOT_SELECTED)
    }
    val selectedIndex: StateFlow<Pair<Int, Int>> by lazy { _selectedIndex }

    private val activeWavePaint: Paint by lazy {
        Paint().apply {
            color = 0xFF0086E6.toInt()
            style = Paint.Style.FILL
        }
    }
    private val inactiveWavePaint: Paint by lazy {
        Paint().apply {
            color = 0xFF3F454D.toInt()
            style = Paint.Style.FILL
        }
    }
    private val inactivePointerPaint: Paint by lazy {
        Paint().apply {
            color = 0xFF7E8999.toInt()
            style = Paint.Style.FILL_AND_STROKE
        }
    }
    private val activePointerPaint: Paint by lazy {
        Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL_AND_STROKE
        }
    }
    private val backgroundPaint: Paint by lazy {
        Paint().apply {
            color = 0xFF141618.toInt()
            style = Paint.Style.FILL_AND_STROKE
        }
    }
    private val textPaint: Paint by lazy {
        Paint().apply {
            color = 0xFF7E8999.toInt()
            textSize = 20f.dpToPixel(context)
            textAlign = Paint.Align.CENTER
        }
    }

    private var startPointerPosition = NOT_SELECTED.toFloat()
    private var endPointerPosition = NOT_SELECTED.toFloat()
    private var selectedPointer = NOT_SELECTED

    @VisibleForTesting
    internal var originalWaveform = listOf<Pair<Float, Float>>()

    @VisibleForTesting
    protected var displayingPoints = listOf<WaveItem>()
    private var waveItemSize = -1f

    fun setWaveform(
        list: List<Pair<Float, Float>>,
        selectedStart: Int = NOT_SELECTED,
        selectedEnd: Int = NOT_SELECTED
    ) {
        log { "setWaveform" }
        originalWaveform = list
        displayingPoints = listOf()
        _selectedIndex.value = selectedStart to selectedEnd
        initView()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        log { "onSizeChanged: $w - $h - $oldw -$oldh" }
        initView()
    }

    @VisibleForTesting
    protected fun calculateMid(
        p1: WaveItem?,
        p2: WaveItem?,
        separateIndex: Float
    ): WaveItem? {
        val lastP1 = p1 ?: return p2
        val firstP2 = p2 ?: return p1
        val top =
            lastP1.topY + (firstP2.topY - lastP1.topY) * (separateIndex - lastP1.x) / (firstP2.x - lastP1.x)
        val bottom =
            lastP1.bottomY + (firstP2.bottomY - lastP1.bottomY) * (separateIndex - lastP1.x) / (firstP2.x - lastP1.x)
        return WaveItem(
            x = separateIndex,
            topY = top,
            bottomY = bottom
        )
    }

    @VisibleForTesting
    protected open fun createSectionPath(
        leftBarrier: WaveItem?,
        rightBarrier: WaveItem?,
        list: List<WaveItem>
    ): Path {
        return Path().apply {
            (leftBarrier ?: list.firstOrNull())?.also {
                moveTo(it.x, it.topY)
            }
            list.forEach {
                lineTo(it.x, it.topY)
            }

            rightBarrier?.also {
                lineTo(it.x, it.topY)
                lineTo(it.x, it.bottomY)
            }
            list.reversed().forEach {
                lineTo(it.x, it.bottomY)
            }
            leftBarrier?.also {
                lineTo(it.x, it.bottomY)
            }
            close()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(
            HORIZONTAL_PADDING,
            VERTICAL_PADDING,
            width - HORIZONTAL_PADDING,
            height - VERTICAL_PADDING,
            backgroundPaint
        )

        if (displayingPoints.isEmpty()) {
            canvas.drawText(NO_DATA_MESSAGE, width / 2f, height / 2f, textPaint)
            return
        }

        val leftSection = mutableListOf<WaveItem>()
        val centerSection = mutableListOf<WaveItem>()
        val rightSection = mutableListOf<WaveItem>()
        displayingPoints.forEach {
            if (it.x < startPointerPosition) {
                leftSection.add(it)
            } else if (it.x <= endPointerPosition + POINTER_WIDTH) {
                centerSection.add(it)
            } else {
                rightSection.add(it)
            }
        }

        val middlePointLeftCenter = calculateMid(
            leftSection.lastOrNull(),
            centerSection.firstOrNull() ?: rightSection.firstOrNull(),
            startPointerPosition
        )

        val middlePointCenterRight = calculateMid(
            centerSection.lastOrNull() ?: leftSection.lastOrNull(),
            rightSection.firstOrNull(),
            endPointerPosition + POINTER_WIDTH
        )



        if (leftSection.isNotEmpty()) {
            canvas.drawPath(
                createSectionPath(null, middlePointLeftCenter, leftSection),
                inactiveWavePaint
            )
        }
        canvas.drawPath(
            createSectionPath(middlePointLeftCenter, middlePointCenterRight, centerSection),
            activeWavePaint
        )
        if (rightSection.isNotEmpty()) {
            canvas.drawPath(
                createSectionPath(middlePointCenterRight, null, rightSection),
                inactiveWavePaint
            )
        }

        drawLeftPointer(canvas)
        drawRightPointer(canvas)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (displayingPoints.isEmpty()) {
            return false
        }

        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                when {
                    event.x > (startPointerPosition - TOUCH_EFFECT_AREA) && event.x < +startPointerPosition + POINTER_WIDTH + TOUCH_EFFECT_AREA -> {
                        selectedPointer = SELECTED_START_POINTER
                        log { "onTouchEvent: SELECTED_START_POINTER" }
                        invalidate()
                        return true
                    }

                    event.x > (endPointerPosition - TOUCH_EFFECT_AREA) && event.x < +endPointerPosition + POINTER_WIDTH + TOUCH_EFFECT_AREA -> {
                        selectedPointer = SELECTED_END_POINTER
                        log { "onTouchEvent: SELECTED_END_POINTER" }
                        invalidate()
                        return true
                    }
                }
                log { "onTouchEvent: NOT_SELECTED, ignore" }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                var updated = false
                when (selectedPointer) {
                    SELECTED_START_POINTER -> {
                        if (event.x + POINTER_WIDTH < endPointerPosition - TOUCH_EFFECT_AREA / 2) {
                            max(event.x, 0f).also {
                                if (it != startPointerPosition) {
                                    updated = true
                                    startPointerPosition = it
                                }
                            }
                        }
                    }

                    SELECTED_END_POINTER -> {
                        if (event.x > startPointerPosition + POINTER_WIDTH + TOUCH_EFFECT_AREA / 2) {
                            (min(event.x, width.toFloat()) - POINTER_WIDTH).also {
                                if (it != endPointerPosition) {
                                    updated = true
                                    endPointerPosition = it
                                }
                            }
                        }
                    }
                }
                if (updated) {
                    invalidate()
                    updateSelectedItems()
                }
            }

            MotionEvent.ACTION_UP -> {
                selectedPointer = NOT_SELECTED
                log { "onTouchEvent: ACTION_UP" }
                invalidate()
            }
        }
        return true
    }

    @VisibleForTesting
    internal fun calculatePosition(unit: Pair<Float, Float>): Pair<Float, Float> {
        return (height / 2) + abs(height / 2 * unit.first) to (height / 2) * (1 - abs(unit.second))
    }

    private fun initDisplayPoints() {
        val width = width.toFloat() - HORIZONTAL_PADDING * 2
        val height = height.toFloat() - VERTICAL_PADDING * 2
        waveItemSize = width / (originalWaveform.size - 1)
        log { "initWaveform: w=$width , h=$height , item_size=$waveItemSize" }

        displayingPoints = originalWaveform
            .map {
                calculatePosition(it)
            }
            .mapIndexed { index, pair ->
                WaveItem(
                    x = waveItemSize * (index) + HORIZONTAL_PADDING,
                    topY = pair.second + VERTICAL_PADDING,
                    bottomY = pair.first + VERTICAL_PADDING
                )
            }
    }

    private fun initPointerPosition() {
        val (selectedStart, selectedEnd) = _selectedIndex.value
        this.startPointerPosition = if (selectedStart != NOT_SELECTED && selectedStart > 0) {
            HORIZONTAL_PADDING + waveItemSize * (selectedStart)
        } else {
            0f
        }
        this.endPointerPosition =
            if (selectedEnd != NOT_SELECTED && selectedEnd < displayingPoints.size - 1) {
                HORIZONTAL_PADDING + waveItemSize * (selectedEnd) - POINTER_WIDTH
            } else {
                width.toFloat() - POINTER_WIDTH
            }
        log { "initPointerPosition: start=${startPointerPosition}, end=${endPointerPosition}" }
    }

    private fun drawLeftPointer(canvas: Canvas) {
        val painToUse = if (selectedPointer == SELECTED_START_POINTER) {
            activePointerPaint
        } else {
            inactivePointerPaint
        }
        canvas.drawRoundRect(
            startPointerPosition,
            0f, startPointerPosition + POINTER_WIDTH, height.toFloat(),
            30f, 30f,
            painToUse
        )
        canvas.drawRect(
            startPointerPosition + POINTER_WIDTH / 2,
            0f,
            startPointerPosition + MARKER_SIZE / 2,
            MARKER_SIZE,
            painToUse
        )
        canvas.drawArc(
            startPointerPosition - 2,
            0f,
            startPointerPosition + MARKER_SIZE,
            MARKER_SIZE,
            -90f, 180f,
            false,
            painToUse
        )
    }

    private fun drawRightPointer(canvas: Canvas) {
        if (endPointerPosition == NOT_SELECTED.toFloat()) {
            this.endPointerPosition = width.toFloat() - POINTER_WIDTH
        }
        val painToUse = if (selectedPointer == SELECTED_END_POINTER) {
            activePointerPaint
        } else {
            inactivePointerPaint
        }
        canvas.drawRoundRect(
            endPointerPosition,
            0f, endPointerPosition + POINTER_WIDTH, height.toFloat(),
            30f, 30f,
            painToUse
        )
        canvas.drawRect(
            endPointerPosition + POINTER_WIDTH - MARKER_SIZE / 2,
            height.toFloat() - MARKER_SIZE,
            endPointerPosition + POINTER_WIDTH / 2,
            height.toFloat(),
            painToUse
        )
        canvas.drawArc(
            endPointerPosition + POINTER_WIDTH - MARKER_SIZE,
            height.toFloat() - MARKER_SIZE,
            endPointerPosition + POINTER_WIDTH + 2,
            height.toFloat(),
            90f, 180f,
            false,
            painToUse
        )
    }

    private fun initView() {
        launch {
            if (width <= 0 || height <= 0) {
                log { "Invalid width or height, ignore | w=$width, h=$height" }
                return@launch
            }
            if (originalWaveform.size < 2) {
                log { "No wave data" }
                return@launch
            }
            initDisplayPoints()
            initPointerPosition()
            postInvalidate()
        }
    }

    private fun updateSelectedItems() {
        launch {
            val start = (startPointerPosition - HORIZONTAL_PADDING) / waveItemSize
            val end = (endPointerPosition - HORIZONTAL_PADDING + POINTER_WIDTH) / waveItemSize
            _selectedIndex.emit(
                max(0, start.toInt()) to min(
                    end.toInt() + 1,
                    displayingPoints.size - 1
                )
            )
        }
    }

    override val coroutineContext: CoroutineContext = Dispatchers.Default + SupervisorJob()
}