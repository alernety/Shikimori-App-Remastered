package com.gnoemes.shikimori.presentation.view.screenshots

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import androidx.core.view.ViewCompat
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.viewpager.widget.ViewPager
import com.gnoemes.shikimori.R
import com.gnoemes.shikimori.databinding.ActivityScreenshotsBinding
import com.gnoemes.shikimori.entity.anime.domain.ScreenshotsNavigationData
import com.gnoemes.shikimori.presentation.view.base.activity.MvpActivity
import com.gnoemes.shikimori.presentation.view.screenshots.adapter.ScreenshotPagerAdapter
import com.gnoemes.shikimori.utils.*


class ScreenshotsActivity : MvpActivity() {

    companion object {
        private const val CURRENT_PAGE = "CURRENT_PAGE"
        private const val UI_VISIBLE = "UI_VISIBLE"
        private const val SCREENSHOTS_DATA_KEY = "SCREENSHOTS_DATA_KEY"
        fun newIntent(context: Context?, data: ScreenshotsNavigationData): Intent {
            val intent = Intent(context, ScreenshotsActivity::class.java)
            intent.putExtra(SCREENSHOTS_DATA_KEY, data)
            return intent
        }
    }

    private var _binding: ActivityScreenshotsBinding? = null
    private val binding get() = _binding!!

    private var adapter: ScreenshotPagerAdapter? = null
    private var itemCount = 0
    private val formatString by lazy { getString(R.string.common_count_format) }
    private var uiVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.ShikimoriAppTheme_Screenshots)
        theme.applyStyle(getCurrentAscentTheme, true)
        super.onCreate(savedInstanceState)
        _binding = ActivityScreenshotsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.run {
            addBackButton(R.drawable.ic_close) { finish() }
            inflateMenu(R.menu.menu_screenshots)
            onMenuClick {
                when (it?.itemId) {
                    R.id.item_share -> share(getCurrentScreenshot())
                    R.id.item_download -> download(getCurrentScreenshot())
                }
                true
            }
        }
        with(binding.viewpager) {
            offscreenPageLimit = 5
            addOnPageChangeListener(pageChangeCallback)
        }

        if (intent != null) {
            val data: ScreenshotsNavigationData? = intent.getParcelableExtra(SCREENSHOTS_DATA_KEY, ScreenshotsNavigationData::class.java)
            if (data != null) {
                adapter = ScreenshotPagerAdapter(data.items, this::toggleUI, this::onSwipe, this::onDismiss)
                itemCount = data.items.size
                val pos = savedInstanceState?.getInt(CURRENT_PAGE, data.selected) ?: data.selected
                binding.viewpager.adapter = this@ScreenshotsActivity.adapter
                binding.viewpager.setCurrentItem(pos, false)
                binding.toolbar.title = String.format(formatString, pos + 1, data.items.size)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            v.setPadding(0, insets.systemWindowInsetTop, insets.systemWindowInsetRight, 0)
            insets
        }

        uiVisible = savedInstanceState?.getBoolean(UI_VISIBLE, true) ?: true
        if (uiVisible) showUi()
        else hideUi()
    }

    private fun getCurrentScreenshot(): String? {
        val adapter = binding.viewpager.adapter as? ScreenshotPagerAdapter
        val items = adapter?.items
        return items?.getOrNull(binding.viewpager.currentItem)?.original
    }

    private fun onSwipe() {
        hideUi(false)
    }

    private fun onDismiss() {
        finish()
        overridePendingTransition(0, 0)
    }

    private fun share(url: String?) {
        val intent = Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }, getString(R.string.common_share))
        startActivity(intent)
    }

    private fun download(url: String?) {
        val file = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val name = url?.let { it.substring(it.lastIndexOf("/") + 1) }
        val path = Uri.withAppendedPath(Uri.fromFile(file), name)

        val manager = downloadManager()
        val request = DownloadManager.Request(url?.toUri())
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .apply {
                    allowScanningByMediaScanner()
                    setDestinationUri(path)
                }

        manager?.enqueue(request)
    }

    private fun toggleUI() {
        if (uiVisible) hideUi()
        else showUi()
        uiVisible = !uiVisible
    }

    private fun showUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        TransitionManager.beginDelayedTransition(binding.coordinator, Fade().apply { duration = 110 })
        binding.appBarLayout.visible()
    }


    private fun hideUi(animate : Boolean = true) {
        window.decorView.systemUiVisibility = (
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN).let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) it or View.SYSTEM_UI_FLAG_IMMERSIVE
            else it
        }
        if (animate) TransitionManager.beginDelayedTransition(binding.coordinator, Fade().apply { duration = 110 })
        binding.appBarLayout.gone()
    }

    override fun onDestroy() {
        super.onDestroy()

        binding.viewpager?.removeOnPageChangeListener(pageChangeCallback)
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putInt(CURRENT_PAGE, binding.viewpager?.currentItem ?: 0)
        outState.putBoolean(UI_VISIBLE, uiVisible)
    }

    private val pageChangeCallback = object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            binding.toolbar?.title = String.format(formatString, position + 1, itemCount)
        }
    }
}