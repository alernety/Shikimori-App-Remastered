package com.gnoemes.shikimori.utils.rx

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GraphQLFallbackNotifier @Inject constructor() {

    private val fallbackSource = PublishSubject.create<Throwable>()

    fun observe(): Observable<Throwable> = fallbackSource

    fun notify(throwable: Throwable) {
        fallbackSource.onNext(throwable)
    }
}
