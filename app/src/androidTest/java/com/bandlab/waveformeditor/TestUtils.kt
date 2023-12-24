package com.bandlab.waveformeditor;

import android.content.Context
import android.net.Uri
import android.view.MotionEvent
import androidx.core.net.toUri
import java.io.File
import kotlin.random.Random

val appContext: Context
    get() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        .targetContext


fun getInputData() = (1..9)
    .map { Random.nextInt(100) }
    .map { it.toFloat() / 100f }
    .map { -it to it }

fun getExportFile() = File(appContext.filesDir, "test_export.txt")
fun getExportUri() = getExportFile().apply {
    if (exists()) {
        delete()
    }
}.toUri()

/* Create file from list data and verify its content */
fun getImportUri(list: List<Pair<Float, Float>>): Uri {
    val internalFile = File(appContext.filesDir, "test_import.txt").apply {
        if (exists()) {
            delete()
        }
    }

    val fileContent = list
        .map { "${it.first} ${it.second}" }
        .joinToString("\n")

    internalFile.outputStream().use {
        it.write(fileContent.toByteArray())
    }

    val verify = internalFile.inputStream().use { it.readBytes().let { String(it) } }
    assert(fileContent == verify)
    return internalFile.toUri()
}

fun createTouchEvent(x: Float, y: Float, action: Int):MotionEvent {
    return MotionEvent.obtain(
        System.currentTimeMillis(), System.currentTimeMillis() + 100,
        action,
        x, y, 0
    )
}