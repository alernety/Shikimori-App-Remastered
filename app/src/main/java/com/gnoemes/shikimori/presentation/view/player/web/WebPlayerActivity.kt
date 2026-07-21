package com.gnoemes.shikimori.presentation.view.player.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gnoemes.shikimori.R
import com.gnoemes.shikimori.data.local.preference.PlayerSettingsSource
import com.gnoemes.shikimori.domain.series.SeriesSyncInteractor
import com.gnoemes.shikimori.entity.app.domain.AppExtras
import com.gnoemes.shikimori.entity.app.domain.SettingsExtras
import com.gnoemes.shikimori.presentation.view.base.activity.BaseThemedActivity
import com.gnoemes.shikimori.utils.Utils
import com.gnoemes.shikimori.utils.widgets.VideoWebChromeClient
import dagger.android.AndroidInjection
import com.gnoemes.shikimori.databinding.ActivityWebPlayerBinding
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import java.util.regex.Pattern
import kotlin.math.abs
import javax.inject.Inject

class WebPlayerActivity : BaseThemedActivity() {

    private lateinit var binding: ActivityWebPlayerBinding
    private lateinit var chromeClient: VideoWebChromeClient
    private lateinit var webView: WebView

    @Inject
    lateinit var settingsSource: PlayerSettingsSource

    @Inject
    lateinit var seriesSyncInteractor: SeriesSyncInteractor

    private val compositeDisposable = CompositeDisposable()

    private val rotationButtonHideJob = Runnable {
        binding.rotationView.visibility = View.GONE
    }

    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private var isDragging = false
    private var isLongPressed = false
    private var downX = 0f
    private var downY = 0f
    private var downRawX = 0f
    private var downRawY = 0f
    private var downTime = 0L
    private var startTranslationX = 0f
    private var startTranslationY = 0f

    private fun showRotationButton() {
        binding.rotationView.visibility = View.VISIBLE
        binding.rotationView.removeCallbacks(rotationButtonHideJob)
        binding.rotationView.postDelayed(rotationButtonHideJob, 3000L)
    }

    private fun setupRotationButton() {
        binding.rotationView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    isLongPressed = false
                    downX = event.x
                    downY = event.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downTime = System.currentTimeMillis()
                    startTranslationX = v.translationX
                    startTranslationY = v.translationY
                    showRotationButton()
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_MOVE -> {
                    val elapsed = System.currentTimeMillis() - downTime
                    if (!isLongPressed && elapsed > ViewConfiguration.getLongPressTimeout().toLong()) {
                        // Long-press detected!
                        isLongPressed = true
                        isDragging = true
                        binding.rotationView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        return@setOnTouchListener true
                    } else if (isDragging) {
                        val rangeX = binding.frame.width.toFloat()
                        val rangeY = binding.frame.height.toFloat()
                        v.translationX = (startTranslationX + event.rawX - downRawX).coerceIn(-rangeX, rangeX)
                        v.translationY = (startTranslationY + event.rawY - downRawY).coerceIn(-rangeY, rangeY)
                        return@setOnTouchListener true
                    } else {
                        // Waiting for long-press — consume move to prevent view from cancelling
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        isDragging = false
                        isLongPressed = false
                        settingsSource.rotationButtonX = v.translationX
                        settingsSource.rotationButtonY = v.translationY
                    } else {
                        // Short tap — toggle orientation
                        toggleOrientation()
                    }
                    showRotationButton()
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    isLongPressed = false
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private val animeId: Long by lazy { intent.getLongExtra(AppExtras.ARGUMENT_ANIME_ID, -1L) }
    private val episodeId: Long by lazy { intent.getLongExtra(AppExtras.ARGUMENT_EPISODE_ID, -1L) }

    private val isEpisodeVideo: Boolean
        get() = animeId != -1L && episodeId != -1L

    companion object {
        private val ANIME_365_REGEX = "smotret-anime\\.com".toRegex()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        binding = ActivityWebPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        showNoAdsMessage()

        if (isEpisodeVideo) {
            if (settingsSource.isOpenLandscape) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        webView = WebView(applicationContext)
        binding.frame.addView(webView)

        chromeClient = VideoWebChromeClient(webView, windowCallback)
        webView.apply {
            webChromeClient = chromeClient
            webViewClient = client
            setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

            settings.apply {
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                javaScriptCanOpenWindowsAutomatically = true
                javaScriptEnabled = true
                domStorageEnabled = true
                allowContentAccess = true
                allowUniversalAccessFromFileURLs = true
                userAgentString = "Mozilla/5.0 (Linux; Android 4.4; Nexus 5 Build/_BuildID_) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/30.0.0.0 Mobile Safari/537.36"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
        }

        if (intent != null) {
            val url = intent.getStringExtra(AppExtras.ARGUMENT_URL)
            if (!url.isNullOrBlank()) {
                val prefs = getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
                val token = prefs.getString(SettingsExtras.ANIME_365_TOKEN, null)

                val useIFrame = Utils.checkNeedIFrame(url)

                if (url.contains(ANIME_365_REGEX) && !token.isNullOrBlank()) webView.loadUrl("$url?access_token=$token")
                else if (useIFrame) {
                    val iframe = "<html><body style='margin:0;padding:0;'><iframe src='$url' width='100%' height='100%'  frameborder='0' allowfullscreen></iframe></body></html>"
                    webView.loadData(iframe, "text/html", "utf-8")
                } else webView.loadUrl(url)

            } else showError()
        } else onBackPressed()

        // Show button for both preview and episode
        showRotationButton()
        setupRotationButton()

        // Load saved position and clamp to visible area
        binding.rotationView.post {
            val v = binding.rotationView
            val parent = binding.frame
            if (parent.width == 0 || parent.height == 0) return@post

            v.translationX = settingsSource.rotationButtonX.coerceIn(
                -v.left.toFloat(),
                (parent.width - v.left - v.width).toFloat()
            )
            v.translationY = settingsSource.rotationButtonY.coerceIn(
                -v.top.toFloat(),
                (parent.height - v.top - v.height).toFloat()
            )
        }

    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_UP) {
            // Show rotation button on any tap anywhere on screen
            showRotationButton()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (hasFocus) hideSystemUi()
            else showSystemUI()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isAutoRotationEnabled) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        // Clamp button to visible screen area after new layout
        binding.rotationView.post {
            val v = binding.rotationView
            val parent = binding.frame
            if (parent.width == 0 || parent.height == 0) return@post

            v.translationX = v.translationX.coerceIn(
                -v.left.toFloat(),
                (parent.width - v.left - v.width).toFloat()
            )
            v.translationY = v.translationY.coerceIn(
                -v.top.toFloat(),
                (parent.height - v.top - v.height).toFloat()
            )
        }
    }

    private fun showNoAdsMessage() {
        val text = getString(R.string.player_no_ads)
        showMessage(text)
    }

    private fun showError() {
        val text = getString(R.string.player_error)
        showMessage(text)
    }

    private fun showMessage(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    override fun onBackPressed() {
        super.finish()
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        markEpisodeAsWatched()
        binding.frame?.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }

    private fun markEpisodeAsWatched() {
        if (animeId == -1L || episodeId == -1L) return
        compositeDisposable.add(
                seriesSyncInteractor.setEpisodeWatched(animeId, episodeId.toInt(), onlyLocal = false)
                        .subscribe({}, { it.printStackTrace() })
        )
    }

    private fun showSystemUI() {
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun hideSystemUi() {
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private val isAutoRotationEnabled: Boolean
        get() = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    private fun toggleOrientation() {
        val orientation = this.resources.configuration.orientation
        when (orientation) {
            Configuration.ORIENTATION_PORTRAIT -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Configuration.ORIENTATION_LANDSCAPE -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private val client = object : WebViewClient() {
        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                if (Pattern.compile("https?://vk\\.com/").matcher(url.orEmpty()).find()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    true
                } else false
            } else {
                super.shouldOverrideUrlLoading(view, url)
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (Pattern.compile("https?://vk\\.com/").matcher(request.url.toString()).find()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(request.url.toString()))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    true
                } else false
            } else false
        }
    }

    private val windowCallback = object : WindowCallback {
        override fun onFullscreenMode() = window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        override fun onNormalMode() = window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    interface WindowCallback {
        fun onFullscreenMode()

        fun onNormalMode()
    }

}