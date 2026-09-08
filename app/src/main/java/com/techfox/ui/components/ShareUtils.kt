package com.techfox.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.techfox.R

/**
 * Copies text to system clipboard and displays a short confirmation Toast.
 */
fun copyToClipboard(
    context: Context,
    text: String,
    label: String = "Translation",
    toastResId: Int = R.string.copied_to_clipboard
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, toastResId, Toast.LENGTH_SHORT).show()
}

/**
 * Launches Android Share sheet for sharing translation or insight text.
 */
fun shareText(
    context: Context,
    text: String,
    chooserTitle: String? = null
) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, chooserTitle)
    context.startActivity(shareIntent)
}
