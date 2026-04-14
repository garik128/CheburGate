package com.android.cheburgate.ui.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.HttpAuthHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.android.cheburgate.R
import com.android.cheburgate.core.ProxyService
import com.android.cheburgate.data.db.AppDatabase
import com.android.cheburgate.databinding.ActivityBrowserBinding
import com.android.cheburgate.ui.main.ServiceAdapter
import com.android.cheburgate.util.copyToClipboard
import com.android.cheburgate.util.showToast
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowserActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_SERVICE_NAME = "extra_service_name"
        private const val PREFS_SESSION = "browser_session"
    }

    private lateinit var binding: ActivityBrowserBinding
    private val viewModel: BrowserViewModel by viewModels()

    private lateinit var serviceName: String
    private lateinit var defaultUrl: String
    private var currentUrl: String = ""
    private lateinit var backCallback: OnBackPressedCallback

    // Для полноэкранного видео (YouTube и др.)
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // Для загрузки файлов
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        fileUploadCallback?.onReceiveValue(uris.toTypedArray())
        fileUploadCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        serviceName = intent.getStringExtra(EXTRA_SERVICE_NAME) ?: "default"
        defaultUrl = intent.getStringExtra(EXTRA_URL) ?: "about:blank"

        val prefs = getSharedPreferences(PREFS_SESSION, MODE_PRIVATE)
        val startUrl = prefs.getString(serviceName, defaultUrl) ?: defaultUrl

        setupNavBar()
        setupBackHandler()
        setupWebView()
        setupProxy(startUrl)
        loadSiteIcon()
        setupTabsButton()
    }

    private fun setupNavBar() {
        binding.btnClose.setOnClickListener { finish() }

        binding.tvCurrentUrl.setOnClickListener { toggleUrlBar() }
        binding.tvCurrentUrl.setOnLongClickListener {
            if (currentUrl.isNotEmpty()) {
                copyToClipboard(currentUrl)
                showToast(getString(R.string.copied))
            }
            true
        }

        binding.btnRefreshNav.setOnClickListener { binding.webView.reload() }

        // Иконка сайта — возврат на стартовую страницу сервиса
        binding.btnSiteIcon.setOnClickListener {
            binding.webView.loadUrl(defaultUrl)
        }

        binding.etUrl.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                navigateToUrl(binding.etUrl.text.toString())
                true
            } else false
        }
        binding.btnGo.setOnClickListener { navigateToUrl(binding.etUrl.text.toString()) }
    }

    private fun setupBackHandler() {
        // Жест/кнопка "назад" — всегда навигация внутри WebView.
        // Когда истории нет — просто ничего не делаем (не закрываем активити).
        // Закрытие — только через кнопку домой в тулбаре.
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    hideCustomView()
                    return
                }
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                }
                // Если истории нет — поглощаем событие, не выходим
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    private fun setupProxy(startUrl: String) {
        val port = ProxyService.currentPort
        if (port > 0) {
            ProxyController.getInstance().setProxyOverride(
                ProxyConfig.Builder()
                    .addProxyRule("http://127.0.0.1:$port")
                    .addBypassRule("localhost")
                    .build(),
                mainExecutor
            ) {
                binding.webView.loadUrl(startUrl)
            }
        } else {
            binding.webView.loadUrl(startUrl)
        }
    }

    private fun setupWebView() {
        val desktopMode = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("desktop_mode", false)

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // Разрешаем загрузку файлов
            allowContentAccess = true
            allowFileAccess = false
            userAgentString = if (desktopMode) {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Safari/537.36"
            } else {
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Mobile Safari/537.36"
            }
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportMultipleWindows(false)
            // Куки — включены по умолчанию в WebView, явно убеждаемся
            android.webkit.CookieManager.getInstance().setAcceptCookie(true)
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)
        }

        binding.webView.setBackgroundColor(android.graphics.Color.WHITE)

        binding.webView.webViewClient = object : WebViewClient() {

            // Обрабатываем 407 Proxy Auth Required от sing-box.
            // WebView вызывает этот колбэк и для серверного 401, и для прокси 407.
            // Для прокси host будет "127.0.0.1".
            override fun onReceivedHttpAuthRequest(
                view: WebView, handler: HttpAuthHandler, host: String, realm: String
            ) {
                val token = ProxyService.currentToken
                if (host == "127.0.0.1" && token.isNotEmpty()) {
                    handler.proceed(token, token)
                } else {
                    handler.cancel()
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                // Блокируем WebRTC чтобы сайт не мог получить реальный IP через STUN
                view.evaluateJavascript(
                    "try{" +
                    "Object.defineProperty(window,'RTCPeerConnection',{value:undefined,writable:false});" +
                    "Object.defineProperty(window,'webkitRTCPeerConnection',{value:undefined,writable:false});" +
                    "Object.defineProperty(window,'mozRTCPeerConnection',{value:undefined,writable:false});" +
                    "}catch(e){}",
                    null
                )
                currentUrl = url
                binding.tvCurrentUrl.text = Uri.parse(url).host ?: url
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                currentUrl = url
                binding.tvCurrentUrl.text = Uri.parse(url).host ?: url
                binding.progressBar.visibility = View.GONE
                android.webkit.CookieManager.getInstance().flush()
                if (url != "about:blank" && !url.startsWith("data:")) {
                    getSharedPreferences(PREFS_SESSION, MODE_PRIVATE)
                        .edit().putString(serviceName, url).apply()
                }
                val host = Uri.parse(url).host ?: return
                viewModel.recordVisit(url, view.title, host)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                binding.progressBar.visibility = View.GONE
                val desc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
                    error.description.toString() else "Ошибка соединения"
                val failedUrl = request.url.toString()
                    .replace("'", "\\'")
                    .replace("\\", "\\\\")
                val html = """
                    <html><body style="font-family:sans-serif;padding:32px;color:#555">
                    <h2>Не удалось загрузить страницу</h2>
                    <p>${request.url}</p>
                    <p style="color:#e00">$desc</p>
                    <p>Проверьте, что прокси запущен и сервер доступен.</p>
                    <button onclick="location.href='$failedUrl'" style="padding:10px 20px;font-size:16px">Повторить</button>
                    </body></html>
                """.trimIndent()
                view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                binding.flFullscreen.addView(view)
                binding.flFullscreen.visibility = View.VISIBLE
                binding.layoutMain.visibility = View.GONE
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    window.insetsController?.let {
                        it.hide(android.view.WindowInsets.Type.systemBars())
                        it.systemBarsBehavior =
                            android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                }
            }

            override fun onHideCustomView() {
                hideCustomView()
            }

            // Загрузка файлов
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                val mimeTypes = fileChooserParams.acceptTypes
                    .filter { it.isNotEmpty() }
                    .toTypedArray()
                    .ifEmpty { arrayOf("*/*") }
                fileChooserLauncher.launch(mimeTypes)
                return true
            }
        }
    }

    private fun loadSiteIcon() {
        val host = Uri.parse(defaultUrl).host ?: return
        val iconUrl = "https://icons.duckduckgo.com/ip3/$host.ico"
        CoroutineScope(Dispatchers.IO).launch {
            val loader = ImageLoader(this@BrowserActivity)
            val result = loader.execute(
                ImageRequest.Builder(this@BrowserActivity)
                    .data(iconUrl)
                    .allowHardware(false)
                    .build()
            )
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        binding.btnSiteIcon.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    private fun toggleUrlBar() {
        if (binding.layoutUrlBar.visibility == View.VISIBLE) {
            binding.layoutUrlBar.visibility = View.GONE
        } else {
            binding.layoutUrlBar.visibility = View.VISIBLE
            binding.etUrl.setText(currentUrl)
            binding.etUrl.selectAll()
            binding.etUrl.requestFocus()
        }
    }

    private fun navigateToUrl(input: String) {
        val url = if (input.startsWith("http://") || input.startsWith("https://")) input
                  else "https://$input"
        binding.webView.loadUrl(url)
        binding.layoutUrlBar.visibility = View.GONE
    }

    private fun setupTabsButton() {
        binding.btnTabsContainer.setOnClickListener { showBookmarksSheet() }
        lifecycleScope.launch {
            val count = AppDatabase.getInstance(this@BrowserActivity)
                .serviceDao().getVisibleFlow().first().size
            binding.tvTabCount.text = count.toString()
        }
    }

    private fun showBookmarksSheet() {
        val sheet = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_bookmarks, null)
        val rv = sheetView.findViewById<RecyclerView>(R.id.rvBookmarks)
        rv.layoutManager = GridLayoutManager(this, 4)

        lifecycleScope.launch {
            val services = AppDatabase.getInstance(this@BrowserActivity)
                .serviceDao().getVisibleFlow().first()
            withContext(Dispatchers.Main) {
                val adapter = ServiceAdapter(
                    onItemClick = { item ->
                        binding.webView.loadUrl(item.url)
                        sheet.dismiss()
                    },
                    onItemLongClick = {},
                    onAddClick = {},
                    showAddButton = false
                )
                rv.adapter = adapter
                adapter.submitList(services)
                sheet.setContentView(sheetView)
                sheet.show()
            }
        }
    }

    private fun hideCustomView() {
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        customView = null
        binding.flFullscreen.removeAllViews()
        binding.flFullscreen.visibility = View.GONE
        binding.layoutMain.visibility = View.VISIBLE
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.show(android.view.WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    override fun onDestroy() {
        if (customView != null) hideCustomView()
        ProxyController.getInstance().clearProxyOverride(mainExecutor) {}
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = null
        super.onDestroy()
    }
}
