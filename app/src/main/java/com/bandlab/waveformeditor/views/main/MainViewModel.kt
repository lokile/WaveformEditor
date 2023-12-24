package com.bandlab.waveformeditor.views.main

import android.app.Application
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.bandlab.waveformeditor.bases.BaseViewModel
import com.bandlab.waveformeditor.customView.NOT_SELECTED
import com.bandlab.waveformeditor.utils.handleLogging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import javax.inject.Inject

class WaveFormState(
    val waveform: List<Pair<Float, Float>>,
    var selectedStart: Int,
    var selectedEnd: Int
)

private fun log(message: () -> String) {
    handleLogging {
        "[MainViewModel] ${message()}"
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(val application: Application) : BaseViewModel() {
    val waveFormState = MutableLiveData<WaveFormState>()
    val allowToExport = MutableLiveData(false)

    fun updateSelectedItems(start: Int, end: Int) {
        waveFormState.value
            ?.apply {
                if (start != NOT_SELECTED) {
                    selectedStart = start
                }
                if (end != NOT_SELECTED) {
                    selectedEnd = end
                }
                log { "Update selected: $selectedStart - $selectedEnd | ${waveform.size}" }
                allowToExport.postValue(
                    waveform.isNotEmpty() &&
                            (selectedStart <= selectedEnd) &&
                            ((selectedStart != NOT_SELECTED && selectedStart > 0) || (selectedEnd != NOT_SELECTED && selectedEnd < waveform.size - 1))
                )
            }
    }

    fun exportWaveform(uri: Uri?) {
        uri ?: kotlin.run {
            log { "Cancel export file" }
            return
        }
        val state = waveFormState.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            application.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val start = if (state.selectedStart == NOT_SELECTED) {
                    0
                } else {
                    state.selectedStart
                }
                val end = if (state.selectedEnd == NOT_SELECTED) {
                    state.waveform.size - 1
                } else {
                    state.selectedEnd
                }
                log { "Export: $start - $end | size=${end - start + 1}" }
                state.waveform.subList(start, end + 1).forEach {
                    outputStream.write("${it.first} ${it.second}\n".toByteArray())
                }
            }
        }
    }

    fun importWaveForm(uri: Uri?) {
        uri ?: kotlin.run {
            log { "Cancel import file" }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            application.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().use { buffer ->
                    flow<String> {
                        while (buffer.readLine()?.also { emit(it) } != null) {
                        }
                    }
                        .map { it.trim().split(" ") }
                        .map { it.map { it.toFloatOrNull() }.filterNotNull() }
                        .filter { it.size == 2 }
                        .map { it[0] to it[1] }
                        .flowOn(Dispatchers.IO)
                        .catch { }
                        .toList()
                        .also {
                            waveFormState.postValue(
                                WaveFormState(
                                    waveform = it,
                                    NOT_SELECTED,
                                    NOT_SELECTED
                                )
                            )
                        }
                }
            }
        }
    }
}