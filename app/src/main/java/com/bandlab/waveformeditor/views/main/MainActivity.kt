package com.bandlab.waveformeditor.views.main

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.bandlab.waveformeditor.bases.AppBaseActivity
import com.bandlab.waveformeditor.databinding.ActivityMainBinding
import com.bandlab.waveformeditor.utils.handleLogging
import com.bandlab.waveformeditor.utils.viewBindingUtils.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private fun log(message: () -> String) {
    handleLogging {
        "[MainActivity] ${message()}"
    }
}

@AndroidEntryPoint
class MainActivity : AppBaseActivity() {
    val binding by viewBinding { ActivityMainBinding.inflate(this) }
    val viewModel by viewModels<MainViewModel>()

    private val importRequest =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            viewModel.importWaveForm(uri)
        }
    private val exportRequest =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
            viewModel.exportWaveform(uri)
        }

    override fun setupView(savedInstanceState: Bundle?) {

        viewModel.waveFormState.observe(this) {
            log { "Received new waveform, size=${it.waveform.size}, start=${it.selectedStart} , end=${it.selectedEnd}" }
            binding.waveformView.setWaveform(it.waveform, it.selectedStart, it.selectedEnd)
        }
        viewModel.allowToExport.observe(this) {
            binding.exportButton.isEnabled = it
        }

        binding.waveformView.selectedIndex
            .onEach { viewModel.updateSelectedItems(it.first, it.second) }
            .flowOn(Dispatchers.Default)
            .launchIn(lifecycleScope)

        binding.importButton.setOnClickListener {
            importRequest.launch("*/*")
        }
        binding.exportButton.setOnClickListener {
            exportRequest.launch("new_waveform.txt")
        }
    }
}