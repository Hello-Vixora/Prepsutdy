package com.hellovixora.replyvault

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.hellovixora.replyvault.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Callback used by the WebChromeClient file chooser (`<input type="file">`). */
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    /** Pending download, stored while we wait on a runtime storage permission (API 24-28 only). */
    private var pendingDownload: PendingDownload? = null

    private data class PendingDownload(val bytes: ByteArray, val filename: String, val mimeType: String)

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val result = if (uri != null) arrayOf(uri) else null
        fileChooserCallback?.onReceiveValue(result)
        fileChooserCallback = null
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingDownload
        pendingDownload = null
        if (granted && pending != null) {
            persistDownload(pending.bytes, pending.filename, pending.mimeType)
        } else if (!granted) {
            Toast.makeText(this, R.string.permission_denied_storage, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyEdgeToEdgeInsets()
        configureWebView()
        setupBackNavigation()

        binding.retryButton.setOnClickListener {
            showContent()
            binding.webView.reload()
        }

        if (savedInstanceState == null) {
            binding.webView.loadUrl(LOCAL_ENTRY_POINT)
        }
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // The WebView draws its own background edge-to-edge; we only pad
            // the error state (which uses native views) so its text/button
            // never sit under a status/navigation bar.
            binding.errorLayout.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun configureWebView() {
        val webView = binding.webView
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        settings.textZoom = 100

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.settings.safeBrowsingEnabled = false
        }

        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.addJavascriptInterface(
            WebAppInterface { base64, filename, mimeType ->
                runOnUiThread { handleBase64Download(base64, filename, mimeType) }
            },
            "AndroidDownloader"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                val scheme = url.scheme
                return if (scheme == "http" || scheme == "https") {
                    // Fully offline app: local navigation stays in-app, anything
                    // pointing off-device is handed to the system so it can be
                    // opened by a browser if one is available. It will simply
                    // never occur during normal use of the bundled content.
                    if (url.host.isNullOrEmpty()) {
                        false
                    } else {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, url))
                        } catch (_: Exception) {
                            // No app can handle it; ignore.
                        }
                        true
                    }
                } else {
                    false
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                showContent()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    showError()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                return try {
                    val mimeTypes = fileChooserParams.acceptTypes
                        ?.filter { it.isNotBlank() }
                        ?.joinToString(",")
                    filePickerLauncher.launch(if (mimeTypes.isNullOrBlank()) "*/*" else mimeTypes)
                    true
                } catch (_: Exception) {
                    fileChooserCallback = null
                    false
                }
            }
        }

        webView.setDownloadListener { url, _, contentDisposition, mimeTypeArg, _ ->
            handleDownload(url, contentDisposition, mimeTypeArg)
        }
    }

    private fun handleDownload(url: String, contentDisposition: String?, mimeTypeArg: String?) {
        when {
            url.startsWith("data:") -> {
                val filename = URLUtil.guessFileName(url, contentDisposition, mimeTypeArg)
                val commaIndex = url.indexOf(',')
                if (commaIndex == -1) return
                val header = url.substring(5, commaIndex) // e.g. "text/csv;base64"
                val mimeType = header.substringBefore(";").ifBlank { mimeTypeArg ?: "application/octet-stream" }
                val payload = url.substring(commaIndex + 1)
                handleBase64Download(payload, filename, mimeType)
            }

            url.startsWith("blob:") -> {
                // Blob content can only be read from inside the page's JS context.
                // Fetch it there, convert to base64, and hand it back through the
                // AndroidDownloader bridge.
                val filename = URLUtil.guessFileName(url, contentDisposition, mimeTypeArg)
                val js = """
                    (function() {
                        fetch("$url")
                            .then(function(res) { return res.blob(); })
                            .then(function(blob) {
                                var reader = new FileReader();
                                reader.onloadend = function() {
                                    var base64 = reader.result.split(',')[1] || '';
                                    window.AndroidDownloader.downloadBase64(base64, "$filename", blob.type || "");
                                };
                                reader.readAsDataURL(blob);
                            })
                            .catch(function(e) { console.error('ReplyVault download failed', e); });
                    })();
                """.trimIndent()
                binding.webView.evaluateJavascript(js, null)
            }

            url.startsWith("http://") || url.startsWith("https://") -> {
                downloadViaManager(url, contentDisposition, mimeTypeArg)
            }
        }
    }

    private fun handleBase64Download(base64: String, filename: String, mimeType: String) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            persistDownload(bytes, filename, mimeType.ifBlank { guessMimeType(filename) })
        } catch (e: Exception) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun persistDownload(bytes: ByteArray, filename: String, mimeType: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = PendingDownload(bytes, filename, mimeType)
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }

        when (val result = DownloadHelper.saveBytes(this, bytes, filename, mimeType)) {
            is DownloadHelper.Result.Success ->
                Toast.makeText(this, getString(R.string.download_complete, result.displayName), Toast.LENGTH_SHORT).show()
            is DownloadHelper.Result.Failure ->
                Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadViaManager(url: String, contentDisposition: String?, mimeTypeArg: String?) {
        try {
            val filename = URLUtil.guessFileName(url, contentDisposition, mimeTypeArg)
            val request = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mimeTypeArg)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, getString(R.string.download_started, filename), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun guessMimeType(filename: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(filename)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this) {
            val webView = binding.webView
            if (binding.errorLayout.visibility == View.VISIBLE) {
                // Nothing meaningful to go back to from an error state.
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            } else if (webView.canGoBack()) {
                webView.goBack()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    private fun showContent() {
        binding.webView.visibility = View.VISIBLE
        binding.errorLayout.visibility = View.GONE
    }

    private fun showError() {
        binding.webView.visibility = View.INVISIBLE
        binding.errorLayout.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        binding.webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val LOCAL_ENTRY_POINT = "file:///android_asset/study-app.html"
    }
}
