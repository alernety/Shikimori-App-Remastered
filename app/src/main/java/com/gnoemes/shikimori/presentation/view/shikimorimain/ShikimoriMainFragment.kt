package com.gnoemes.shikimori.presentation.view.shikimorimain

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import moxy.presenter.InjectPresenter
import moxy.presenter.ProvidePresenter
import com.gnoemes.shikimori.R
import com.gnoemes.shikimori.databinding.FragmentShikimoriMainBinding
import com.gnoemes.shikimori.entity.forum.domain.ForumType
import com.gnoemes.shikimori.presentation.presenter.shikimorimain.ShikimoriMainPresenter
import com.gnoemes.shikimori.presentation.view.base.fragment.BaseFragment
import com.gnoemes.shikimori.presentation.view.base.fragment.RouterProvider
import com.gnoemes.shikimori.presentation.view.forum.ForumFragment
import com.gnoemes.shikimori.presentation.view.topic.list.TopicListFragment
import com.gnoemes.shikimori.utils.gone
import com.gnoemes.shikimori.utils.ifNotNull
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import ru.terrakok.cicerone.Navigator
import ru.terrakok.cicerone.Router
import javax.inject.Inject

class ShikimoriMainFragment : BaseFragment<ShikimoriMainPresenter, ShikimoriMainView>(), ShikimoriMainView, RouterProvider, HasAndroidInjector {

    private var _binding: FragmentShikimoriMainBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var childFragmentInjector: DispatchingAndroidInjector<Any>

    override fun androidInjector(): AndroidInjector<Any> = childFragmentInjector

    @InjectPresenter
    lateinit var mainPresenter: ShikimoriMainPresenter

    @ProvidePresenter
    fun providePresenter(): ShikimoriMainPresenter {
        mainPresenter = presenterProvider.get()

        parentFragment.ifNotNull {
            mainPresenter.localRouter = (it as RouterProvider).localRouter
        }

        return mainPresenter
    }

    companion object {
        fun newInstance() = ShikimoriMainFragment()
    }

    private val adapter by lazy { PagerAdapter(childFragmentManager) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentShikimoriMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbarBinding.toolbar.gone()

        binding.pagesContainerView.adapter = adapter
        binding.pagesContainerView.offscreenPageLimit = 3
        binding.tabLayout.setupWithViewPager(binding.pagesContainerView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    ///////////////////////////////////////////////////////////////////////////
    // GETTERS
    ///////////////////////////////////////////////////////////////////////////

    override fun getPresenter(): ShikimoriMainPresenter = mainPresenter

    override fun getFragmentLayout(): Int = R.layout.fragment_shikimori_main

    override val localRouter: Router
        get() = (parentFragment as RouterProvider).localRouter

    override val localNavigator: Navigator
        get() = (parentFragment as RouterProvider).localNavigator

    ///////////////////////////////////////////////////////////////////////////
    // MVP
    ///////////////////////////////////////////////////////////////////////////

    inner class PagerAdapter(
            fm: FragmentManager
    ) : FragmentStatePagerAdapter(fm) {

        override fun getItem(position: Int): Fragment {
            return when (position) {
                0 -> TopicListFragment.newInstance(ForumType.NEWS)
                1 -> TopicListFragment.newInstance(ForumType.MY_CLUBS)
                else -> ForumFragment.newInstance()
            }
        }

        override fun getCount(): Int = 3

        override fun getPageTitle(position: Int): CharSequence? {
            return when (position) {
                0 -> context?.getString(R.string.topic_news)
                1 -> context?.getString(R.string.topic_my_feed)
                else -> context?.getString(R.string.topic_forum)
            }
        }
    }
}