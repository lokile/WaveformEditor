package com.bandlab.waveformeditor

import android.app.Application
import android.util.Log
import androidx.lifecycle.asFlow
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bandlab.waveformeditor.views.main.MainViewModel
import com.bandlab.waveformeditor.customView.NOT_SELECTED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith


/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
@RunWith(AndroidJUnit4::class)
class MainViewModelTest {
    private fun log(message: String) {
        Log.d("MainViewModelTest", message)
    }

    private fun getViewModel() = MainViewModel(appContext.applicationContext as Application)


    @Test
    fun testCancelImportWaveform() = runBlocking {
        val viewModel = getViewModel()
        withTimeoutOrNull(1000) {
            viewModel.waveFormState.asFlow()
                .onStart { viewModel.importWaveForm(null) }
                .take(1)
                .singleOrNull()
        }.let {
            //it to nothing and do not have data
            assertNull(it)
        }
    }

    @Test
    fun testImportAndReimportWaveform() = runBlocking {
        val data1 = getInputData()
        val viewModel = getViewModel()
        viewModel.waveFormState.asFlow()
            .onStart { viewModel.importWaveForm(getImportUri(data1)) }
            .debounce(100)
            .take(1)
            .collect { state ->
                log("waveform: ${state.waveform.size} - ${data1.size}")
                assertEquals(state.waveform.size, data1.size)
                assertFalse(state.waveform.zip(data1).any {
                    it.first != it.second
                })
            }

        //change the selected slice
        viewModel.updateSelectedItems(1, data1.size - 3)
        assertEquals(viewModel.waveFormState.value?.selectedStart, 1)
        assertEquals(viewModel.waveFormState.value?.selectedEnd, data1.size - 3)

        //reimport data
        val data2 = getInputData()
        assertFalse(
            data1.zip(data2).all { it.first == it.second }
        )

        viewModel.waveFormState.asFlow()
            .onStart { viewModel.importWaveForm(getImportUri(data2)) }
            .debounce(100)
            .take(1)
            .collect { state ->
                log("waveform: ${state.waveform.size} - ${data2.size}")
                //compare with the new data
                assertEquals(state.waveform.size, data2.size)
                assertFalse(state.waveform.zip(data2).any {
                    it.first != it.second
                })

                //compare with the old data
                assertTrue(state.waveform.zip(data1).any {
                    it.first != it.second
                })
            }

        //the selected slice is reset
        assertEquals(viewModel.waveFormState.value?.selectedStart, NOT_SELECTED)
        assertEquals(viewModel.waveFormState.value?.selectedEnd, NOT_SELECTED)
    }

    @Test
    fun testSelectedItemChanged() = runBlocking {
        val viewModel = getViewModel()
        val data = getInputData()
        viewModel.waveFormState.asFlow()
            .onStart { viewModel.importWaveForm(getImportUri(data)) }
            .debounce(100)
            .take(1)
            .collect()

        //the selected slide is equal to the whole wave length, disable the export button
        assertFalse(
            viewModel.allowToExport.asFlow()
                .take(1)
                .single()
        )

        //user move the start pointer --> allow to export

        assertTrue(
            viewModel.allowToExport.asFlow()
                .onStart { viewModel.updateSelectedItems(1, data.size - 3) }
                .debounce(100)
                .take(1)
                .single()
        )

        //the selected slide is equal to the whole wave length again, disable the export button

        assertFalse(
            viewModel.allowToExport.asFlow()
                .onStart { viewModel.updateSelectedItems(0, data.size - 1) }
                .debounce(100)
                .take(1)
                .single()
        )

        //user move the end pointer --> allow to export
        assertTrue(
            viewModel.allowToExport.asFlow()
                .onStart { viewModel.updateSelectedItems(0, data.size - 2) }
                .debounce(100)
                .take(1)
                .single()
        )
    }

    @Test
    fun testExportWaveform() = runBlocking {
        val data1 = getInputData()
        val viewModel = getViewModel()
        viewModel.waveFormState.asFlow()
            .onStart { viewModel.importWaveForm(getImportUri(data1)) }
            .debounce(100)
            .take(1)
            .collect()

        val start = 3
        val end = data1.size - 3
        viewModel.updateSelectedItems(start, end)
        viewModel.exportWaveform(getExportUri())
        delay(500)

        //load the exported data and compare
        val exportedData = getExportFile()
            .inputStream().use { String(it.readBytes()) }
            .split("\n")
            .map { it.split(" ") }
            .map { it.map { it.toFloatOrNull() }.filterNotNull() }
            .filter { it.size == 2 }
            .map { it[0] to it[1] }

        val selectedSlice = data1.subList(start, end + 1)
        assertEquals(exportedData.size, selectedSlice.size)
        assertTrue(
            exportedData.zip(selectedSlice).all { it.first == it.second }
        )
    }
}