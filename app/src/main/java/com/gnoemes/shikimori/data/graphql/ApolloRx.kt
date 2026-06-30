package com.gnoemes.shikimori.data.graphql

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Mutation
import com.apollographql.apollo.api.Query
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Bridge from Apollo Kotlin's coroutine-based suspend API to RxJava2.
 *
 * Enables existing RxJava2/MVP/Dagger presenters to call Apollo 5 operations
 * without migrating the entire app to coroutines.
 *
 * @see rxQuery for GraphQL queries returning [Single]
 * @see rxMutation for GraphQL mutations returning [Completable]
 */

/**
 * Execute a GraphQL query via Apollo and return the result as an RxJava2 [Single].
 *
 * The coroutine is launched on [Dispatchers.IO] and the returned [Disposable]
 * cancels the underlying coroutine [Job] when disposed.
 *
 * @param D the expected query response data type (bound by [Query.Data])
 * @param query the Apollo [Query] to execute
 * @return [Single] emitting the query result data
 * @throws Exception if the query fails or data is null
 */
fun <D : Query.Data> ApolloClient.rxQuery(query: Query<D>): Single<D> {
    return Single.create { emitter ->
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val response: ApolloResponse<D> = this@rxQuery.query(query).execute()
                val data = response.data
                if (data != null) {
                    emitter.onSuccess(data)
                } else {
                    // Apollo returned a response with null data (e.g. entity not found).
                    // dataOrThrow() would throw NoDataException, which ErrorProcessing
                    // doesn't handle, causing a confusing TitleException crash dialog.
                    // Convert to NoSuchElementException so ErrorProcessing maps it to
                    // ContentException — a graceful empty/not-found state.
                    emitter.onError(NoSuchElementException("No data was found"))
                }
            } catch (e: Exception) {
                emitter.onError(e)
            }
        }

        emitter.setDisposable(
            object : Disposable {
                override fun dispose() {
                    job.cancel()
                }

                override fun isDisposed(): Boolean = job.isCancelled
            }
        )
    }
}

/**
 * Execute a GraphQL mutation via Apollo and return completion as an RxJava2 [Completable].
 *
 * The coroutine is launched on [Dispatchers.IO] and the returned [Disposable]
 * cancels the underlying coroutine [Job] when disposed.
 *
 * @param mutation the Apollo [Mutation] to execute
 * @return [Completable] signalling completion or error of the mutation
 */
fun ApolloClient.rxMutation(mutation: Mutation<*>): Completable {
    return Completable.create { emitter ->
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                this@rxMutation.mutation(mutation).execute()
                emitter.onComplete()
            } catch (e: Exception) {
                emitter.onError(e)
            }
        }

        emitter.setDisposable(
            object : Disposable {
                override fun dispose() {
                    job.cancel()
                }

                override fun isDisposed(): Boolean = job.isCancelled
            }
        )
    }
}
