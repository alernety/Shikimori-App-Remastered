package com.gnoemes.shikimori.presentation.presenter.base

import android.util.Log
import com.gnoemes.shikimori.entity.app.domain.exceptions.BaseException
import com.gnoemes.shikimori.entity.app.domain.exceptions.NetworkException
import com.gnoemes.shikimori.entity.app.domain.exceptions.ServiceCodeException
import com.gnoemes.shikimori.presentation.view.base.activity.BaseNetworkView
import com.gnoemes.shikimori.utils.rx.GraphQLFallbackNotifier
import javax.inject.Inject

abstract class BaseNetworkPresenter<View : BaseNetworkView> : BaseNavigationPresenter<View>() {

    @Inject
    lateinit var graphQLFallbackNotifier: GraphQLFallbackNotifier

    override fun initData() {}

    override fun onFirstViewAttach() {
        super.onFirstViewAttach()
        subscribeToGraphQLFallback()
    }

    private fun subscribeToGraphQLFallback() {
        graphQLFallbackNotifier.observe()
                .subscribe { throwable ->
                    viewState.showSystemMessage(throwable.localizedMessage ?: "Something went wrong")
                }
                .addToDisposables()
    }

    //TODO process exceptions
    protected open fun processErrors(throwable: Throwable) {
//        val errorUtils = ErrorUtils()
//        errorUtils.processErrors(throwable, router, viewState)
        when ((throwable as? BaseException)?.tag) {
            NetworkException.TAG -> viewState.apply { showNetworkView(); showContent(false); showSystemMessage(throwable.localizedMessage) }
            ServiceCodeException.TAG -> viewState.apply { showNetworkView(); showContent(false); showSystemMessage("HTTP error ${(throwable as ServiceCodeException).serviceCode}") }
            else -> {
                Log.e("Error", "Error processing request", throwable)
                viewState.onHideLoading()
                viewState.showContent(true)
                viewState.showSystemMessage("Something went wrong")
            }
        }
    }
}