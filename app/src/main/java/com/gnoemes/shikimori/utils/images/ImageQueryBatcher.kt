package com.gnoemes.shikimori.utils.images

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.gnoemes.shikimori.data.graphql.AnimeListQuery
import com.gnoemes.shikimori.data.graphql.CharacterListQuery
import com.gnoemes.shikimori.data.graphql.MangaListQuery
import com.gnoemes.shikimori.data.graphql.PersonListQuery
import com.gnoemes.shikimori.data.graphql.rxQuery
import com.gnoemes.shikimori.utils.appendHostIfNeed
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Batches image URL resolution requests per entity type into a single GraphQL query.
 *
 * When multiple [resolve] calls arrive for the same entity type within a short window,
 * they are debounced and combined into a single query with comma-separated IDs.
 * Individual results are cached and delivered via callbacks.
 */
@Singleton
class ImageQueryBatcher @Inject constructor(
    private val apolloClient: ApolloClient
) {

    private val TAG = "ImageQueryBatcher"

    /**
     * Cache of resolved image URLs. Keyed by (entityType, entityId).
     * Stores null for entities that failed to resolve.
     */
    private val cache = HashMap<Pair<String, Long>, String?>()

    /**
     * Pending callbacks grouped by entity type and entity ID.
     *
     * Structure: entityType -> (entityId -> list of callbacks)
     */
    private val pending = mutableMapOf<String, MutableMap<Long, MutableList<(String?) -> Unit>>>()

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Flag to ensure only one batch is scheduled per UI frame.
     * Set to true when a batch is scheduled, reset to false inside the posted runnable.
     * This avoids the debounce pattern that could prevent batches from ever firing
     * when resolve() is called faster than the debounce delay.
     */
    private var batchScheduled: Boolean = false

    /**
     * CompositeDisposable to manage all active Rx subscriptions.
     * Prevents subscription leaks across config changes / view destruction.
     */
    private val disposables = CompositeDisposable()

    /**
     * Resolve an image URL for the given entity.
     *
     * If the URL is already cached, returns it immediately via [onComplete].
     * Otherwise, enqueues the request into a batch and fires a single
     * GraphQL query per entity type after a short debounce delay.
     *
     * @param entityType the type of entity (e.g. "anime", "manga", "character", "person")
     * @param entityId   the entity's unique identifier
     * @param onComplete callback invoked with the resolved URL (or null on failure)
     */
    fun resolve(entityType: String, entityId: Long, onComplete: (String?) -> Unit) {
        val key = Pair(entityType, entityId)
        if (cache.containsKey(key)) {
            val cached = cache[key]
            onComplete(cached)
            return
        }

        pending
            .getOrPut(entityType) { mutableMapOf() }
            .getOrPut(entityId) { mutableListOf() }
            .add(onComplete)

        scheduleBatch()
    }

    /**
     * Dispose all active GraphQL subscriptions and clear pending batches.
     * Call this when the associated view/scope is destroyed to prevent leaks.
     */
    fun dispose() {
        disposables.clear()
        batchScheduled = false
        // Deliver null to any remaining pending callbacks so they don't hang
        for ((_, idMap) in pending) {
            for ((_, callbacks) in idMap) {
                for (cb in callbacks) cb(null)
            }
        }
        pending.clear()
    }

    /**
     * Schedule a batch fire on the next main loop iteration if one isn't already scheduled.
     *
     * Uses a boolean flag instead of debounce to ensure the batch fires even when
     * [resolve] is called rapidly (e.g. during RecyclerView scrolling).
     */
    private fun scheduleBatch() {
        if (!batchScheduled) {
            batchScheduled = true
            handler.postDelayed({
                batchScheduled = false
                fireBatches()
            }, 16L) // one frame delay to collect more items from RecyclerView bindings
        }
    }

    /**
     * Fire all pending batches, one per entity type.
     * Takes a snapshot of [pending] and clears it before executing queries.
     */
    private fun fireBatches() {
        val snapshot = pending.toMap()
        pending.clear()

        for ((entityType, idMap) in snapshot) {
            when (entityType) {
                "anime" -> fireAnimeBatch(idMap)
                "manga" -> fireMangaBatch(idMap)
                "character" -> fireCharacterBatch(idMap)
                "person" -> firePersonBatch(idMap)
            }
        }
    }

    /**
     * Fire a single batch query for anime IDs using [AnimeListQuery].
     *
     * Unlike [AnimeByIdQuery], this query has no hardcoded `limit: 1`,
     * so it correctly returns results for comma-separated IDs.
     * Poster URL is extracted via [mainUrl] (list query does not expose [originalUrl]).
     */
    private fun fireAnimeBatch(idMap: Map<Long, MutableList<(String?) -> Unit>>) {
        val ids = idMap.keys.joinToString(",")
        val disposable = apolloClient.rxQuery(
            AnimeListQuery(
                ids = Optional.presentIfNotNull(ids),
                limit = Optional.presentIfNotNull(50)
            )
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { data ->
                    for ((entityId, callbacks) in idMap) {
                        val targetId = entityId.toString()
                        val url = data.animes
                            .firstOrNull { it.id == targetId }
                            ?.poster?.mainUrl?.appendHostIfNeed()
                        cache[Pair("anime", entityId)] = url
                        for (cb in callbacks) cb(url)
                    }
                },
                { throwable ->
                    Log.e(TAG, "Failed to resolve anime batch (ids=$ids)", throwable)
                    for ((entityId, callbacks) in idMap) {
                        cache[Pair("anime", entityId)] = null
                        for (cb in callbacks) cb(null)
                    }
                }
            )
        disposables.add(disposable)
    }

    /**
     * Fire a single batch query for manga IDs using [MangaListQuery].
     *
     * Unlike [MangaByIdQuery], this query has no hardcoded `limit: 1`,
     * so it correctly returns results for comma-separated IDs.
     * Poster URL is extracted via [mainUrl] (list query does not expose [originalUrl]).
     */
    private fun fireMangaBatch(idMap: Map<Long, MutableList<(String?) -> Unit>>) {
        val ids = idMap.keys.joinToString(",")
        val disposable = apolloClient.rxQuery(
            MangaListQuery(
                ids = Optional.presentIfNotNull(ids),
                limit = Optional.presentIfNotNull(50)
            )
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { data ->
                    for ((entityId, callbacks) in idMap) {
                        val targetId = entityId.toString()
                        val url = data.mangas
                            .firstOrNull { it.id == targetId }
                            ?.poster?.mainUrl?.appendHostIfNeed()
                        cache[Pair("manga", entityId)] = url
                        for (cb in callbacks) cb(url)
                    }
                },
                { throwable ->
                    Log.e(TAG, "Failed to resolve manga batch (ids=$ids)", throwable)
                    for ((entityId, callbacks) in idMap) {
                        cache[Pair("manga", entityId)] = null
                        for (cb in callbacks) cb(null)
                    }
                }
            )
        disposables.add(disposable)
    }

    /**
     * Fire a single batch query for character IDs.
     * [CharacterListQuery] accepts a comma-separated string via [Optional].
     */
    private fun fireCharacterBatch(idMap: Map<Long, MutableList<(String?) -> Unit>>) {
        val ids = idMap.keys.joinToString(",")
        val disposable = apolloClient.rxQuery(
            CharacterListQuery(
                ids = Optional.presentIfNotNull(ids),
                limit = Optional.presentIfNotNull(50)
            )
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { data ->
                    for ((entityId, callbacks) in idMap) {
                        val targetId = entityId.toString()
                        val url = data.characters
                            .firstOrNull { it.id == targetId }
                            ?.poster?.mainUrl?.appendHostIfNeed()
                        cache[Pair("character", entityId)] = url
                        for (cb in callbacks) cb(url)
                    }
                },
                { throwable ->
                    Log.e(TAG, "Failed to resolve character batch (ids=$ids)", throwable)
                    for ((entityId, callbacks) in idMap) {
                        cache[Pair("character", entityId)] = null
                        for (cb in callbacks) cb(null)
                    }
                }
            )
        disposables.add(disposable)
    }

    /**
     * Fire a single batch query for person IDs.
     * [PersonListQuery] accepts a list of string IDs via [Optional].
     */
    private fun firePersonBatch(idMap: Map<Long, MutableList<(String?) -> Unit>>) {
        val ids = idMap.keys.map { it.toString() }
        val disposable = apolloClient.rxQuery(
            PersonListQuery(
                ids = Optional.presentIfNotNull(ids),
                limit = Optional.presentIfNotNull(50)
            )
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { data ->
                    for ((entityId, callbacks) in idMap) {
                        val targetId = entityId.toString()
                        val url = data.people
                            .firstOrNull { it.id == targetId }
                            ?.poster?.mainUrl?.appendHostIfNeed()
                        cache[Pair("person", entityId)] = url
                        for (cb in callbacks) cb(url)
                    }
                },
                { throwable ->
                    Log.e(TAG, "Failed to resolve person batch (ids=$ids)", throwable)
                    for ((entityId, callbacks) in idMap) {
                        cache[Pair("person", entityId)] = null
                        for (cb in callbacks) cb(null)
                    }
                }
            )
        disposables.add(disposable)
    }
}
