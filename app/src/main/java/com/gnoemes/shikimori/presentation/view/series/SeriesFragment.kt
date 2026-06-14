package com.gnoemes.shikimori.presentation.view.series

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.Fade
import androidx.transition.TransitionManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.files.folderChooser
import moxy.presenter.InjectPresenter
import moxy.presenter.ProvidePresenter
import com.gnoemes.shikimori.R
import com.gnoemes.shikimori.databinding.FragmentSeriesBinding
import com.gnoemes.shikimori.entity.app.domain.SettingsExtras
import com.gnoemes.shikimori.entity.common.domain.Image
import com.gnoemes.shikimori.entity.series.domain.PlayerType
import com.gnoemes.shikimori.entity.series.domain.TranslationType
import com.gnoemes.shikimori.entity.series.domain.Video
import com.gnoemes.shikimori.entity.series.presentation.*
import com.gnoemes.shikimori.presentation.presenter.series.SeriesPresenter
import com.gnoemes.shikimori.presentation.presenter.series.download.SeriesDownloadDialog
import com.gnoemes.shikimori.presentation.view.base.fragment.BaseFragment
import com.gnoemes.shikimori.presentation.view.base.fragment.RouterProvider
import com.gnoemes.shikimori.presentation.view.common.fragment.DescriptionDialogFragment
import com.gnoemes.shikimori.presentation.view.common.fragment.ListDialogFragment
import com.gnoemes.shikimori.presentation.view.series.episodes.EpisodesFragment
import com.gnoemes.shikimori.presentation.view.series.translations.adapter.TranslationsAdapter
import com.gnoemes.shikimori.utils.*
import com.gnoemes.shikimori.utils.images.ImageLoader
import com.gnoemes.shikimori.utils.widgets.VerticalSpaceItemDecorator
import com.kotlinpermissions.KotlinPermissions
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class SeriesFragment : BaseFragment<SeriesPresenter, SeriesView>(),
        SeriesView,
        PlayerSelectDialog.Callback,
        ListDialogFragment.DialogCallback,
        EpisodesFragment.EpisodesCallback,
        SeriesDownloadDialog.SeriesDownloadCallback,
        HasAndroidInjector {

    private var _binding: FragmentSeriesBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var imageLoader: ImageLoader

    @InjectPresenter
    lateinit var seriesPresenter: SeriesPresenter

    @Inject
    lateinit var childFragmentInjector: DispatchingAndroidInjector<Any>

    override fun androidInjector(): AndroidInjector<Any> = childFragmentInjector

    @ProvidePresenter
    fun providePresenter(): SeriesPresenter = presenterProvider.get().apply {
        localRouter = (parentFragment as RouterProvider).localRouter
        navigationData = arguments?.getParcelable(SERIES_NAVIGATION_DATA)!!
    }

    companion object {
        fun newInstance(data: SeriesNavigationData) = SeriesFragment().withArgs { putParcelable(SERIES_NAVIGATION_DATA, data) }
        private const val SERIES_NAVIGATION_DATA = "SERIES_NAVIGATION_DATA"
    }

    private val defaultCorners by lazy { context!!.dimen(R.dimen.margin_big) }

    private val adapter by lazy { TranslationsAdapter(getPresenter()::onHostingClicked, getPresenter()::onMenuClicked) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentSeriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding.toolbar) {
            inflateMenu(R.menu.menu_series)
            setOnMenuItemClickListener { getPresenter().onSearchClicked(); true }
            addBackButton { getPresenter().onBackPressed() }
        }

        binding.searchToolbar.addBackButton { getPresenter().onSearchClose() }

        with(binding.recyclerView) {
            adapter = this@SeriesFragment.adapter
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(VerticalSpaceItemDecorator(context.dimen(R.dimen.margin_normal).toInt(), false))
            setHasFixedSize(true)
            addOnScrollListener(shadowScrollListener)
        }

        with(binding.searchView) {
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    hideSoftInput()
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    getPresenter().onQueryChanged(newText)
                    return true
                }
            })
            findViewById<SearchView.SearchAutoComplete>(R.id.search_src_text)?.apply {
                setPadding(0, 0, context.dp(8), 0)
                setHintTextColor(context.colorStateList(context.attr(R.attr.colorOnPrimarySecondary).resourceId))
            }
            findViewById<LinearLayout>(R.id.search_edit_frame)?.apply {
                layoutParams = (layoutParams as? LinearLayout.LayoutParams)?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        marginStart = 0
                    }; leftMargin = 0
                }
            }
            findViewById<ImageView>(R.id.search_close_btn)?.apply {
                setPadding(context!!.dp(12), 0, context!!.dp(12), 0)
                tint(context.colorAttr(R.attr.colorOnPrimary))
            }
        }

        binding.emptyContentView.setText(R.string.episodes_not_found)
        binding.networkErrorView.callback = { getPresenter().onRefresh() }
        binding.networkErrorView.showButton()

        binding.authorsLayout.gone()
        binding.translationBtn.gone()
        binding.progress.visible()

        binding.translationBtn.onClick { showTypes(true) }
        binding.voiceBtn.onClick { onTypeSelected(TranslationType.VOICE_RU) }
        binding.subtitlesBtn.onClick { onTypeSelected(TranslationType.SUB_RU) }
        binding.originalBtn.onClick { onTypeSelected(TranslationType.RAW) }

        binding.episodeChip.onClick { getPresenter().showEpisodes() }
        binding.sourceChangeBtn.onClick { showSources(true) }

        binding.mainSource.onClick { onSourceSelected(false) }
        binding.altSource.onClick { onSourceSelected(true) }

        binding.actionBtn.onClick { onSourceSelected(true) }
        binding.fab.onClick { getPresenter().onDiscussionClicked() }
        binding.nextEpisodeBtn.onClick { getPresenter().onNextEpisode() }
    }

    private fun onTypeSelected(newType: TranslationType) {
        getPresenter().onTypeChanged(newType)
        showTypes(false)
    }

    private fun onSourceSelected(isAlternative: Boolean) {
        getPresenter().onSourceChanged(isAlternative)
        showSources(false)
    }

    private fun showSources(show: Boolean) {
        TransitionManager.beginDelayedTransition(binding.motionLayout, AutoTransition())
        if (show && !binding.translationBtn.isVisible()) showTypes(false)
        binding.sourceChangeBtn.visibleIf { !show }
        if (binding.episodeChip.isEnabled) binding.episodeChip.visibleIf { !show }
        if (binding.nextEpisodeBtn.isEnabled) binding.nextEpisodeBtn.visibleIf { !show }
        binding.mainSource.visibleIf { show }
        binding.altSource.visibleIf { show }
    }

    private fun showTypes(show: Boolean) {
        TransitionManager.beginDelayedTransition(binding.motionLayout, AutoTransition())
        if (show && !binding.sourceChangeBtn.isVisible()) showSources(false)
        binding.translationBtn.visibleIf { !show }
        binding.voiceBtn.visibleIf { show }
        binding.subtitlesBtn.visibleIf { show }
        binding.originalBtn.visibleIf { show }
    }

    private val shadowScrollListener = object : RecyclerView.OnScrollListener() {
        private val shadowDefault by lazy { context!!.dimen(R.dimen.default_shadow) }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            if (recyclerView.layoutManager != null) {

                if (recyclerView.canScrollVertically(-1)) ViewCompat.setElevation(binding.appbar, shadowDefault)
                else ViewCompat.setElevation(binding.appbar, 0f)
            }
        }
    }

    override fun onDestroyView() {
        binding.recyclerView.removeOnScrollListener(shadowScrollListener)
        super.onDestroyView()
        _binding = null
    }

    override fun onPlayerSelected(playerType: PlayerType) {
        getPresenter().onPlayerSelected(playerType)
    }

    override fun dialogItemCallback(tag: String?, action: String) {
        if (tag == "QualityDialog") {
            getPresenter().onQualityChoosed(action)
        }
    }

    override fun onDownload(url: String, video: Video) = getPresenter().onTrackForDownloadSelected(url, video)
    override fun onShare(url: String) = getPresenter().onShare(url)

    override fun onRateCreated(id: Long) = getPresenter().onRateCreated(id)
    override fun onEpisodeSelected(episodeId: Long, episode: Int, isAlternative: Boolean) =
            getPresenter().onEpisodeSelected(episodeId, episode, isAlternative)

    ///////////////////////////////////////////////////////////////////////////
    // GETTERS
    ///////////////////////////////////////////////////////////////////////////

    override fun getPresenter(): SeriesPresenter = seriesPresenter

    override fun getFragmentLayout(): Int = R.layout.fragment_series

    ///////////////////////////////////////////////////////////////////////////
    // MVP
    ///////////////////////////////////////////////////////////////////////////

    override fun showData(newItems: List<TranslationViewModel>) {
        adapter.bindItems(newItems)
    }

    override fun setTitle(title: String) {
        binding.toolbar.title = title
    }

    override fun showEmptyAuthorsView(show: Boolean, isAlternative: Boolean) {
        binding.authorsLayout.visibleIf { show }
        binding.titleView.setText(R.string.series_empty_authors_title)
        binding.descriptionView.setText(R.string.series_empty_authors_description)
        if (isAlternative) binding.actionBtn.gone()
        else binding.actionBtn.visible()
    }

    override fun showEmptySearchView() {
        val emptyItem = SeriesPlaceholderItem(R.string.translation_search_empty_title, R.string.translation_search_empty_desc)
        adapter.bindItems(mutableListOf(emptyItem))
    }

    override fun setEpisodeName(index: Int) {
        val text = "# ".colorSpan(context!!.colorAttr(R.attr.colorSecondaryTransparent)).append("$index")
        binding.episodeChip.text = text
    }

    override fun showNextEpisode(show: Boolean) {
        binding.nextEpisodeBtn.isEnabled = show
        binding.nextEpisodeBtn.visibleIf { show }
    }

    override fun hideEpisodeName() {
        binding.episodeChip.isEnabled = false
        binding.episodeChip.gone()
    }

    override fun showEpisodeLoading(show: Boolean) {
        TransitionManager.beginDelayedTransition(binding.motionLayout, Fade())
        if (binding.episodeChip.isEnabled) binding.episodeChip.visibleIf { !show }
        if (binding.nextEpisodeBtn.isEnabled) binding.nextEpisodeBtn.visibleIf { !show }
        binding.sourceChangeBtn.visibleIf { !show }
    }

    override fun changeSource(isAlternative: Boolean) {
        binding.mainSource.setTextColor(context!!.colorAttr(if (isAlternative) R.attr.colorOnSurface else R.attr.colorSecondary))
        binding.altSource.setTextColor(context!!.colorAttr(if (isAlternative) R.attr.colorSecondary else R.attr.colorOnSurface))
    }

    override fun setTranslationType(type: TranslationType) {
        val icon = when (type) {
            TranslationType.VOICE_RU -> R.drawable.ic_voice
            TranslationType.SUB_RU -> R.drawable.ic_subs
            TranslationType.RAW -> R.drawable.ic_original
            else -> 0
        }

        if (icon != 0) {
            binding.translationBtn.setIconResource(icon)
        }

        if (!binding.translationBtn.isVisible()) binding.translationBtn.visible()
    }

    override fun showSearchView() {
        TransitionManager.beginDelayedTransition(binding.appBarLayout, Fade())
        binding.searchToolbar.visible()
        binding.toolbar.gone()
        binding.motionLayout.gone()
        binding.backdrop.radius = 0f
    }

    override fun onSearchClosed() {
        TransitionManager.beginDelayedTransition(binding.appBarLayout, Fade())
        binding.motionLayout.visible()
        binding.toolbar.visible()
        binding.searchToolbar.gone()
        binding.backdrop.radius = defaultCorners
    }

    override fun showPlayerDialog() {
        val dialog = PlayerSelectDialog.newInstance()
        dialog.show(childFragmentManager, "PlayerSelect")
    }

    override fun showDownloadDialog(title: String, items: List<SeriesDownloadItem>) {
        val dialog = SeriesDownloadDialog.newInstance(title, items)
        dialog.show(childFragmentManager, "DownloadDialog")
    }

    override fun showAuthorDialog(author: String) {
        DescriptionDialogFragment.newInstance(titleRes = R.string.translation_authors, text = author)
                .show(childFragmentManager, "AuthorsDialog")
    }

    override fun showEpisodesDialog(data: EpisodesNavigationData) {
        val dialog = EpisodesFragment.newInstance(data)
        dialog.show(childFragmentManager, "EpisodeDialog")
    }

    override fun showTracksNotFoundError() {
        Toast.makeText(requireContext(), R.string.series_tracks_empty, Toast.LENGTH_SHORT).show()
    }

    override fun checkPermissions() {
        KotlinPermissions.with(activity!!)
                .permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                .onAccepted { getPresenter().onStoragePermissionsAccepted() }
                .ask()
    }

    override fun showFolderChooserDialog() {
        MaterialDialog(context!!).show {
            folderChooser(
                    allowFolderCreation = true,
                    emptyTextRes = R.string.download_folder_empty,
                    folderCreationLabel = R.string.download_new_folder)
            { _, file ->
                putSetting(SettingsExtras.DOWNLOAD_FOLDER, file.absolutePath)
            }
            positiveButton { getPresenter().onStoragePermissionsAccepted() }
        }
    }

    override fun showQualityChooser(items: List<Pair<String, String>>) {
        val dialog = ListDialogFragment.newInstance()
        dialog.apply {
            setItems(items)
        }.show(childFragmentManager, "QualityDialog")
    }

    override fun showEmptyView() {
        binding.authorsLayout.visible()
        binding.actionBtn.gone()
        binding.titleView.setText(R.string.series_empty_episode_title)
        binding.descriptionView.setText(R.string.series_empty_episode_description)
        binding.sourceChangeBtn.gone()
    }

    override fun showContent(show: Boolean) = binding.recyclerView.visibleIf { show }
    override fun hideEmptyView() = binding.emptyContentView.gone()
    override fun showNetworkView() = binding.networkErrorView.visible()
    override fun hideNetworkView() = binding.networkErrorView.gone()
    override fun setBackground(image: Image) = imageLoader.setBlurredImage(binding.backgroundImage, image.original, sampling = 2)
    override fun scrollToPosition(position: Int) = binding.recyclerView.scrollToPosition(position)
    override fun showFab(show: Boolean) = if (show) binding.fab.show() else binding.fab.hide()
    override fun onShowLoading() = binding.progress.visible()
    override fun onHideLoading() = binding.progress.gone()
    override fun onShowLightLoading() = binding.progress.visible()
    override fun onHideLightLoading() = binding.progress.gone()
}