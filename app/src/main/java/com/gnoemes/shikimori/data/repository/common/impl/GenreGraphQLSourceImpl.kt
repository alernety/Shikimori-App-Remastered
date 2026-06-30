package com.gnoemes.shikimori.data.repository.common.impl

import com.apollographql.apollo.ApolloClient
import com.gnoemes.shikimori.data.graphql.AnimeGenresQuery
import com.gnoemes.shikimori.data.graphql.type.GenreKindEnum
import com.gnoemes.shikimori.data.graphql.MangaGenresQuery
import com.gnoemes.shikimori.data.graphql.rxQuery
import com.gnoemes.shikimori.data.repository.common.GenreGraphQLSource
import com.gnoemes.shikimori.entity.common.data.GenreResponse
import io.reactivex.Single
import javax.inject.Inject

/**
 * Implementation of [GenreGraphQLSource] that uses the Shikimori GraphQL API
 * via [ApolloClient] and maps the Apollo-generated Genre types to [GenreResponse].
 */
class GenreGraphQLSourceImpl @Inject constructor(
        private val apolloClient: ApolloClient
) : GenreGraphQLSource {

    override fun getAnimeGenres(): Single<List<GenreResponse>> {
        return apolloClient.rxQuery(AnimeGenresQuery())
                .map { response -> response.genres.map { it.toGenreResponse() } }
    }

    override fun getMangaGenres(): Single<List<GenreResponse>> {
        return apolloClient.rxQuery(MangaGenresQuery())
                .map { response -> response.genres.map { it.toGenreResponse() } }
    }

    /**
     * Map an Apollo-generated [AnimeGenresQuery.Genre] to the existing [GenreResponse] model.
     *
     * In Apollo 5 the `id` field is typed as `ID?` (a String wrapper), or `String?`,
     * so we convert via [Any.toString] for safety.
     */
    private fun AnimeGenresQuery.Genre.toGenreResponse(): GenreResponse = GenreResponse(
            id = id?.toString()?.toLongOrNull() ?: 0L,
            name = name ?: "",
            nameRu = russian,
            type = kind?.toGenreKindString()
    )

    /**
     * Map an Apollo-generated [MangaGenresQuery.Genre] to the existing [GenreResponse] model.
     */
    private fun MangaGenresQuery.Genre.toGenreResponse(): GenreResponse = GenreResponse(
            id = id?.toString()?.toLongOrNull() ?: 0L,
            name = name ?: "",
            nameRu = russian,
            type = kind?.toGenreKindString()
    )

    /**
     * Convert the [GenreKindEnum] into the lowercase string expected by [GenreResponse.type].
     *
     * e.g. [GenreKindEnum.GENRE] → "genre", [GenreKindEnum.THEME] → "theme"
     */
    private fun GenreKindEnum.toGenreKindString(): String = name.lowercase()
}
