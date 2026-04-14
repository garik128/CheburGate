package com.android.cheburgate.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast

fun Context.copyToClipboard(text: String, label: String = "text") {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun Context.pasteFromClipboard(): String? {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
}

fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun String.toHostOrNull(): String? = try {
    Uri.parse(this).host
} catch (_: Exception) {
    null
}
