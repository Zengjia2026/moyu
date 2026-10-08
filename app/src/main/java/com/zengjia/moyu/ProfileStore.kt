package com.zengjia.moyu

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

class ProfileStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "profiles").apply { mkdirs() }
    val activeFile: File get() = File(dir, "active.yaml")

    fun hasProfile(): Boolean = activeFile.isFile && activeFile.length() > 0

    fun displayName(): String = prefs.getString("display_name", null)
        ?: if (hasProfile()) "active.yaml" else "未导入配置"

    fun import(uri: Uri): String {
        val name = queryName(uri) ?: "profile.yaml"
        require(name.endsWith(".yaml", true) || name.endsWith(".yml", true)) {
            "请选择 .yaml 或 .yml 配置文件"
        }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取配置文件" }
            activeFile.outputStream().use { output -> input.copyTo(output) }
        }
        require(activeFile.length() > 0) { "配置文件为空" }
        prefs.edit().putString("display_name", name).apply()
        return name
    }

    private fun queryName(uri: Uri): String? {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return uri.lastPathSegment
    }
}
