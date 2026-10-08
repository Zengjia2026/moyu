package com.zengjia.moyu

import java.util.Locale

object TrafficFormatter {
    fun speed(bytesPerSecond: Long): String = "${bytes(bytesPerSecond)}/s"

    fun bytes(value: Long): String {
        val v = value.coerceAtLeast(0)
        if (v < 1024) return "$v B"
        val kb = v / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }
}
