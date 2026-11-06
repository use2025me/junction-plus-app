package com.junctionplus.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.content.res.Configuration
import android.os.Build
import android.webkit.JavascriptInterface
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var offline: View

    // Full-screen video (when the viewer taps the player's full-screen button)
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private var keepSplash = true
    private lateinit var loading: View
    private var loadingAnim: ObjectAnimator? = null

    // Fire TV / Android TV: on-screen pointer driven by the remote
    private var tvRemote: TvRemote? = null

    // Phones: video fills the screen when turned sideways
    private var videoMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { keepSplash }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        webView = findViewById(R.id.webview)
        progress = findViewById(R.id.progress)
        offline = findViewById(R.id.offline)
        loading = findViewById(R.id.loading)
        keepSplash = false // the branded loading screen takes over from here
        startLoadingAnimation()

        // Keep content clear of the status bar, navigation bar and camera cut-out.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            if (videoMode) v.setPadding(0, 0, 0, 0)
            else v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupWebView()

        // Let the video use the whole screen, including around the camera cut-out.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        if (isTvDevice(this)) {
            val cursor = CursorView(this)
            // Sits above the website but below the "No connection" screen.
            root.addView(
                cursor, 1,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            tvRemote = TvRemote(webView, cursor).also { remote ->
                webView.post { remote.show(true) }
            }
        }

        findViewById<Button>(R.id.retry).setOnClickListener {
            offline.visibility = View.GONE
            webView.visibility = View.VISIBLE
            if (webView.url.isNullOrEmpty()) webView.loadUrl(Config.HOME_URL) else webView.reload()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    fullscreenView != null -> exitFullscreen()
                    videoMode -> exitVideoMode()
                    webView.canGoBack() -> webView.goBack()
                    else -> finish()
                }
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            hideLoading()
        } else {
            webView.loadUrl(startUrl(intent))
            UpdateChecker(this).check()
        }

        // Never keep the loading screen up longer than 12 seconds, even on a slow connection.
        root.postDelayed({ hideLoading() }, 12000)
    }

    private fun startLoadingAnimation() {
        val d = resources.displayMetrics.density
        findViewById<View>(R.id.loading_content).apply {
            alpha = 0f; scaleX = 0.94f; scaleY = 0.94f
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        loadingAnim = ObjectAnimator.ofFloat(
            findViewById(R.id.loading_bar), View.TRANSLATION_X, -64 * d, 160 * d
        ).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun hideLoading() {
        if (loading.visibility != View.VISIBLE) return
        loading.animate().alpha(0f).setDuration(400).withEndAction {
            loading.visibility = View.GONE
            loadingAnim?.cancel()
            loadingAnim = null
        }.start()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true) // keeps subscribers signed in
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(false)
            userAgentString = "$userAgentString JunctionPlusApp/1.0"
        }
        webView.addJavascriptInterface(JsBridge(), VideoMode.BRIDGE)
        webView.setBackgroundColor(getColor(R.color.black))

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    if (isInternal(uri)) return false
                    openExternally(uri)
                    return true
                }
                // mailto:, tel:, intent: and app links
                openExternally(uri)
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                exitVideoMode() // a new page never starts in video mode
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress.visibility = View.GONE
                keepSplash = false
                hideLoading()
                view.evaluateJavascript(VideoMode.PLAY_HOOK, null)
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    keepSplash = false
                    hideLoading()
                    webView.visibility = View.INVISIBLE
                    offline.visibility = View.VISIBLE
                    findViewById<Button>(R.id.retry).requestFocus() // so a TV remote can press it
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                enterFullscreen(view, callback)
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }
        }
    }

    private fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        fullscreenView = view
        fullscreenCallback = callback
        (window.decorView as FrameLayout).addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        view.setBackgroundColor(getColor(R.color.black))
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun exitFullscreen() {
        val view = fullscreenView ?: return
        (window.decorView as FrameLayout).removeView(view)
        fullscreenView = null
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        ViewCompat.requestApplyInsets(root)
    }

    /** Called from the website when a video starts playing. */
    inner class JsBridge {
        @JavascriptInterface
        fun onVideoPlay() {
            runOnUiThread { updateVideoMode() }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateVideoMode()
    }

    /** Sideways with a video playing: video fills the screen. Upright: back to normal. */
    private fun updateVideoMode() {
        if (tvRemote != null || fullscreenView != null) return
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape) enterVideoMode() else exitVideoMode()
    }

    private fun enterVideoMode() {
        webView.evaluateJavascript(VideoMode.ENTER) { result ->
            if (result?.contains("on") == true && !videoMode) {
                videoMode = true
                progress.visibility = View.GONE
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                ViewCompat.requestApplyInsets(root)
            }
        }
    }

    private fun exitVideoMode() {
        if (!videoMode) return
        videoMode = false
        webView.evaluateJavascript(VideoMode.EXIT, null)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ViewCompat.requestApplyInsets(root)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val remote = tvRemote
        if (remote != null && offline.visibility != View.VISIBLE &&
            remote.handle(event, fullscreenView)
        ) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isInternal(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return Config.INTERNAL_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    private fun openExternally(uri: Uri) {
        try {
            val intent = if (uri.scheme == "intent") {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No app can open this link; ignore.
        } catch (e: Exception) {
            // Malformed link; ignore.
        }
    }

    private fun startUrl(intent: Intent?): String {
        val data = intent?.data
        return if (data != null && isInternal(data)) data.toString() else Config.HOME_URL
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { if (isInternal(it)) webView.loadUrl(it.toString()) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
