package com.gnoemes.shikimori.presentation.view.base.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gnoemes.shikimori.R
import com.gnoemes.shikimori.databinding.LayoutDefaultListBinding
import com.gnoemes.shikimori.entity.app.domain.Constants
import com.gnoemes.shikimori.presentation.presenter.base.BasePaginationPresenter
import com.gnoemes.shikimori.presentation.view.base.adapter.BasePaginationAdapter
import com.gnoemes.shikimori.utils.hideRefresh
import com.gnoemes.shikimori.utils.showRefresh
import com.gnoemes.shikimori.utils.visibleIf

abstract class BasePaginationFragment<Items : Any, Presenter : BasePaginationPresenter<Items, ViewV>, ViewV : BasePaginationView> : BaseFragment<Presenter, ViewV>(), BasePaginationView {

    private var _listBinding: LayoutDefaultListBinding? = null
    protected val listBinding get() = _listBinding!!

    abstract val adapter: BasePaginationAdapter

    protected open val nextPageListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            val manager = (recyclerView.layoutManager as LinearLayoutManager)
            val visibleItemPosition = manager.findLastCompletelyVisibleItemPosition() + Constants.DEFAULT_LIMIT / 2
            val itemCount = manager.itemCount

            if (!adapter.isProgress() && visibleItemPosition >= itemCount) {
                getPresenter().loadNextPage()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _listBinding = LayoutDefaultListBinding.bind(view.findViewById(R.id.included_layout_default_list))
        super.onViewCreated(view, savedInstanceState)

        listBinding.refreshLayout.setOnRefreshListener { getPresenter().onRefresh() }
    }

    override fun onDestroyView() {
        listBinding.recyclerView.removeOnScrollListener(nextPageListener)
        _listBinding = null
        super.onDestroyView()
    }

    override fun showData(data: List<Any>) {
        adapter.bindItems(data)
    }

    override fun showContent(show: Boolean) = listBinding.recyclerView.visibleIf { show }
    override fun onShowLoading() = listBinding.refreshLayout.showRefresh()
    override fun onHideLoading() = listBinding.refreshLayout.hideRefresh()
    override fun showPageLoading() = postViewAction { adapter.showProgress(true) }
    override fun hidePageLoading() = postViewAction { adapter.showProgress(false) }
    override fun onAllData() = Unit
}
