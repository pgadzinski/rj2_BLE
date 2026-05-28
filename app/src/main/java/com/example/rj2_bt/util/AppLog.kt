package com.example.rj2_bt.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppLog(private val maxChars: Int = 14_000) {

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun append(line: String) {
        val stamped = "${timeFmt.format(Date())}  $line\n"
        var combined = _text.value + stamped
        if (combined.length > maxChars) {
            combined = combined.takeLast(maxChars)
            val nl = combined.indexOf('\n')
            if (nl >= 0) combined = combined.substring(nl + 1)
        }
        _text.value = combined
    }

    @Synchronized
    fun clear() {
        _text.value = ""
    }

    fun snapshot(): String = _text.value
}
