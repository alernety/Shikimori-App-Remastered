package com.gnoemes.shikimori.presentation.view.search.filter.seasons

import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moxy.presenter.InjectPresenter
import moxy.presenter.ProvidePresenter
import com.gnoemes.shikimori.R
import com.gnoemes.shikimori.databinding.FragmentFilterSeasonsBinding
import com.gnoemes.shikimori.databinding.LayoutFilterNestedToolbarBinding
import com.gnoemes.shikimori.databinding.LayoutFilterSeasonsCustomBinding
import com.gnoemes.shikimori.entity.common.domain.FilterItem
import com.gnoemes.shikimori.entity.common.domain.Type
import com.gnoemes.shikimori.entity.search.domain.FilterType
import com.gnoemes.shikimori.entity.search.presentation.FilterViewModel
import com.gnoemes.shikimori.presentation.presenter.search.FilterSeasonsPresenter
import com.gnoemes.shikimori.presentation.view.base.fragment.BaseBottomSheetInjectionDialogFragment
import com.gnoemes.shikimori.presentation.view.search.filter.FilterCallback
import com.gnoemes.shikimori.presentation.view.search.filter.adapter.FilterChipAdapter
import com.gnoemes.shikimori.presentation.view.search.filter.seasons.adapter.FilterSeasonsAdapter
import com.gnoemes.shikimori.utils.*
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager

class FilterSeasonsFragment : BaseBottomSheetInjectionDialogFragment<FilterSeasonsPresenter, FilterSeasonsView>(), FilterSeasonsView {

    @InjectPresenter
    lateinit var filterPresenter: FilterSeasonsPresenter

    @ProvidePresenter
    fun providePresenter(): FilterSeasonsPresenter = presenterProvider.get().apply {
        type = arguments?.getSerializable(TYPE_KEY) as? Type ?: Type.ANIME
        //copy of filters
        appliedFilters = HashMap(arguments?.getSerializable(FILTERS_KEY) as HashMap<String, MutableList<FilterItem>>)
    }

    companion object {
        private const val FILTERS_KEY = "FILTERS_KEY"
        private const val TYPE_KEY = "TYPE_KEY"
        fun newInstance(type: Type, filters: HashMap<String, MutableList<FilterItem>>?) = FilterSeasonsFragment()
                .withArgs {
                    putSerializable(TYPE_KEY, type)
                    putSerializable(FILTERS_KEY, filters)
                }
    }

    private val simpleAdapter by lazy { FilterChipAdapter(FilterType.SEASON, presenter::onFilterInverted, presenter::onFilterSelected) }
    private val customAdapter by lazy { FilterSeasonsAdapter(presenter::onNewCustomFilter, presenter::onRemoveCustomFilter) }

    private var _binding: FragmentFilterSeasonsBinding? = null
    private val binding get() = _binding!!

    override fun onAttach(context: Context) {
        super.onAttach(context)
        peekHeight = Point().let { activity?.windowManager?.defaultDisplay?.getSize(it);it }.x - context.dimenAttr(android.R.attr.actionBarSize)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentFilterSeasonsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding.nestedToolbar.toolbar) {
            setTitle(R.string.filter_seasons)
            addBackButton(R.drawable.ic_close) { onBackPressed() }
        }

        with(binding.recyclerView) {
            adapter = simpleAdapter
            layoutManager = FlexboxLayoutManager(context)
            itemAnimator = null
        }

        with(binding.seasonsCustom.customInputRecyclerView) {
            adapter = customAdapter
            layoutManager = FlexboxLayoutManager(context).apply { flexWrap = FlexWrap.WRAP; flexDirection = FlexDirection.ROW }
            itemAnimator = null
        }

        binding.nestedToolbar.clearBtn.onClick { presenter.onResetClicked() }
        binding.nestedToolbar.acceptBtn.onClick { presenter.onAcceptClicked() }
        binding.seasonsCustom.helpBtn.onClick {
            MaterialAlertDialogBuilder(context!!).apply {
                setTitle(R.string.filter_custom)
                setMessage(Html.fromHtml(getString(R.string.filter_custom_seasons_hint)))
                setPositiveButton(R.string.common_understand, null)
            }.show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    ///////////////////////////////////////////////////////////////////////////
    // GETTERS
    ///////////////////////////////////////////////////////////////////////////

    override val presenter: FilterSeasonsPresenter
        get() = filterPresenter

    override fun getDialogLayout(): Int = R.layout.fragment_filter_seasons

    ///////////////////////////////////////////////////////////////////////////
    // MVP
    ///////////////////////////////////////////////////////////////////////////

    override fun showSimpleData(items: List<FilterViewModel>) {
        simpleAdapter.bind(items)
    }

    override fun showCustomData(items: List<Any>) {
        customAdapter.bindItems(items)
    }

    override fun setResetEnabled(show: Boolean) = binding.nestedToolbar.clearBtn.visibleIf { show }

    override fun onFiltersAccepted(appliedFilters: HashMap<String, MutableList<FilterItem>>) {
        (targetFragment as? FilterCallback)?.onFiltersSelected(tag, appliedFilters)
        onBackPressed()
    }

    override fun onBackPressed() {
        dismiss()
    }

    override fun showData(items: List<Any>) = Unit
}