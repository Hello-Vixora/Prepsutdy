package com.hellovixora.replyvault

import android.webkit.JavascriptInterface

/**
 * Minimal JavaScript bridge exposed to the page as `window.AndroidDownloader`.
 *
 * The web app never has to know it's running inside a native shell: normal
 * `<a href="...">`, `data:` and `blob:` links all keep working. This bridge
 * only exists so that `blob:` URLs (used by `URL.createObjectURL()` for
 * client-generated exports, e.g. a JSON/CSV export of study data) can be
 * converted to base64 in JS and handed to native code, which is the only
 * reliable way to save blob content from a WebView to disk.
 */
class WebAppInterface(private val onBase64Download: (base64: String, filename: String, mimeType: String) -> Unit) {

    @JavascriptInterface
    fun downloadBase64(base64Data: String, filename: String, mimeType: String) {
        onBase64Download(base64Data, filename, mimeType)
    }
}
