package com.bandlab.waveformeditor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bandlab.waveformeditor.customView.WaveformView
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@RunWith(AndroidJUnit4::class)
class WaveformViewTest {
    val viewSize = 100
    fun initCustomView(waveformView: WaveformView) {
        waveformView.measure(
            View.MeasureSpec.makeMeasureSpec(viewSize, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(viewSize, View.MeasureSpec.EXACTLY)
        )
        waveformView.layout(0, 0, viewSize, viewSize)
    }

    @Test
    fun testNoData() {
        val waveformView = WaveformView(appContext)
        initCustomView(waveformView)
        val mockCanvas = mock(Canvas::class.java)
        waveformView.draw(mockCanvas)

        val left = ArgumentCaptor.forClass(Float::class.java)
        val top = ArgumentCaptor.forClass(Float::class.java)
        val right = ArgumentCaptor.forClass(Float::class.java)
        val bottom = ArgumentCaptor.forClass(Float::class.java)
        val paint = ArgumentCaptor.forClass(Paint::class.java)

        //check it draw the background
        verify(mockCanvas).drawRect(
            left.capture(),
            top.capture(),
            right.capture(),
            bottom.capture(),
            paint.capture()
        )
        assertEquals(waveformView.HORIZONTAL_PADDING, left.value)
        assertEquals(waveformView.VERTICAL_PADDING, top.value)
        assertEquals(viewSize - waveformView.HORIZONTAL_PADDING, right.value)
        assertEquals(viewSize - waveformView.VERTICAL_PADDING, bottom.value)

        //check it draws the text
        val text = ArgumentCaptor.forClass(String::class.java)
        val width = ArgumentCaptor.forClass(Float::class.java)
        val height = ArgumentCaptor.forClass(Float::class.java)

        verify(mockCanvas).drawText(
            text.capture(),
            width.capture(),
            height.capture(),
            paint.capture()
        )
        assertEquals(text.value, waveformView.NO_DATA_MESSAGE)
        assertEquals(width.value, viewSize / 2f)
        assertEquals(height.value, viewSize / 2f)
    }

    @Test
    fun testSelectFullSlice() = runBlocking<Unit> {
        val data = getInputData()
        val waveformView = object : WaveformView(appContext) {
            override fun createSectionPath(
                leftBarrier: WaveItem?,
                rightBarrier: WaveItem?,
                list: List<WaveItem>
            ): Path {
                assertEquals(list.size, displayingPoints.size)
                assertTrue(list.zip(displayingPoints).all {
                    it.first == it.second
                })
                return super.createSectionPath(leftBarrier, rightBarrier, list)
            }
        }

        initCustomView(waveformView)
        val mockCanvas = mock(Canvas::class.java)

        waveformView.setWaveform(data)
        delay(100)
        waveformView.draw(mockCanvas)

        //it draw only 1 section
        verify(mockCanvas, times(1))
            .drawPath(any(), any())
    }


    /*
    * Move the end pointer to the center of the slice, check it get correct points to draw
    * */
    @Test
    fun testSelectFirstHalf() = runBlocking<Unit> {
        val data = getInputData()
        val waveformView = object : WaveformView(appContext) {
            var sectionIndex = 1

            override fun createSectionPath(
                leftBarrier: WaveItem?,
                rightBarrier: WaveItem?,
                list: List<WaveItem>
            ): Path {

                val expected =
                    if (sectionIndex == 1) {//check whether it is drawing the left section
                        sectionIndex += 1
                        val leftMid =
                            calculateMid(displayingPoints.first(), displayingPoints[1], 0f)

                        assertEquals(leftMid, leftBarrier)
                        assertNotNull(rightBarrier)
                        assertEquals(rightBarrier!!.x, width / 2f)
                        displayingPoints.dropLast(displayingPoints.size / 2).also {
                            val rightMid =
                                calculateMid(it.last(), displayingPoints[it.size], width / 2f)
                            assertEquals(rightMid, rightBarrier)
                        }


                    } else if (sectionIndex == 2) { //check whether it is drawing the right section
                        assertNull(rightBarrier)
                        displayingPoints.drop(displayingPoints.size - displayingPoints.size / 2)
                            .also {
                                val leftMid = calculateMid(
                                    displayingPoints[displayingPoints.size / 2],
                                    it.first(),
                                    width / 2f
                                )
                                assertEquals(leftMid, leftBarrier)

                            }
                    } else {
                        emptyList()
                    }
                assertEquals(expected.size, list.size)
                assertTrue(
                    expected.zip(list).all { it.first == it.second }
                )
                return super.createSectionPath(leftBarrier, rightBarrier, list)
            }
        }

        initCustomView(waveformView)
        val mockCanvas = mock(Canvas::class.java)
        waveformView.setWaveform(data)
        delay(100)
        waveformView.onTouchEvent(
            createTouchEvent(
                viewSize.toFloat(),
                viewSize / 2f, MotionEvent.ACTION_DOWN
            )
        )
        waveformView.onTouchEvent(
            createTouchEvent(
                viewSize / 2f, viewSize / 2f, MotionEvent.ACTION_MOVE,
            )
        )
        waveformView.onTouchEvent(
            createTouchEvent(
                viewSize / 2f, viewSize / 2f, MotionEvent.ACTION_UP,
            )
        )
        waveformView.draw(mockCanvas)

        //it draw only 2 section
        verify(mockCanvas, times(2))
            .drawPath(any(), any())
    }
}