package com.bandlab.waveformeditor.bases

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.bandlab.waveformeditor.utils.handleException
import com.bandlab.waveformeditor.utils.handleLogging

abstract class AppBaseActivity : AppCompatActivity() {
    internal var viewBindingProvider: ((LayoutInflater) -> ViewBinding)? = null
    abstract fun setupView(savedInstanceState: Bundle?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = viewBindingProvider?.invoke(layoutInflater)?.root
        if (view != null) {
            setContentView(view)
        } else{
            handleLogging { "Warning: The activity has no view" }
        }
        kotlin.runCatching {
            setupView(savedInstanceState)
        }.onFailure {
            handleLogging { "Error: failed to initialize Activity: ${it.message}" }
            handleException(it)
        }
    }
}