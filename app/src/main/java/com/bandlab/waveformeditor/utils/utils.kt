package com.bandlab.waveformeditor.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.bandlab.waveformeditor.BuildConfig

fun handleException(t: Throwable) {
    ensureStaging {
        t.printStackTrace()
    }
}

fun handleLogging(log: () -> String) {
    ensureStaging {
        Log.d("WaveformLog", log())
    }
}

inline fun <T> ensureStaging(call: () -> T?): T? {
    return if (!BuildConfig.IS_PRODUCTION) {
        call()
    } else {
        null
    }
}

inline fun <T> ensureProduction(call: () -> T?): T? {
    return if (BuildConfig.IS_PRODUCTION) {
        call()
    } else {
        null
    }
}

fun Float.dpToPixel(context: Context): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        context.resources.displayMetrics
    )
}

fun Context.hasReadPermission(): Boolean {
    return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) || ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
}

fun Context.hasWritePermission(): Boolean {
    return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) || ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
}