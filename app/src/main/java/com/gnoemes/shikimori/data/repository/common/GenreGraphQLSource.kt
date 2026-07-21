package com.gnoemes.shikimori.data.repository.common

import com.gnoemes.shikimori.entity.common.data.GenreResponse
import io.reactivex.Single

/**
 * GraphQL-based data source for fetching genres.
 *
 * Provides methods to retrieve genres from the Shikimori GraphQL API,
 * mapping the results to the existing [GenreResponse] model so that the
 * existing converter chain ([GenreResponseConverter]) continues to work.
 */
interface GenreGraphQLSource {

    /**
     * Fetch all anime genres via the `genres(entryType: Anime)` GraphQL query.
     */
    fun getAnimeGenres(): Single<List<GenreResponse>>

    /**
     * Fetch all manga genres via the `genres(entryType: Manga)` GraphQL query.
     */
    fun getMangaGenres(): Single<List<GenreResponse>>
}
