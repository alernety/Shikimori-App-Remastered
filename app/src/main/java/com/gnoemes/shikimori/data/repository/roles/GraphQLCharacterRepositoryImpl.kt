package com.gnoemes.shikimori.data.repository.roles

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.gnoemes.shikimori.data.graphql.CharacterListQuery
import com.gnoemes.shikimori.data.graphql.rxQuery
import com.gnoemes.shikimori.entity.common.domain.Image
import com.gnoemes.shikimori.entity.roles.domain.CharacterDetails
import com.gnoemes.shikimori.utils.appendHostIfNeed
import com.gnoemes.shikimori.utils.nullIfEmpty
import io.reactivex.Single
import javax.inject.Inject

class GraphQLCharacterRepositoryImpl @Inject constructor(
        private val apolloClient: ApolloClient
) : CharacterRepository {

    override fun getDetails(id: Long): Single<CharacterDetails> {
        return apolloClient.rxQuery(
                CharacterListQuery(ids = Optional.present(id.toString()))
        ).map { response ->
            val character = response.characters.firstOrNull()
                    ?: throw IllegalArgumentException("Character not found: $id")
            character.toDomain()
        }
    }

    override fun search(query: String, page: Int, limit: Int): Single<List<CharacterDetails>> {
        return apolloClient.rxQuery(
                CharacterListQuery(
                        search = Optional.present(query),
                        page = Optional.present(page),
                        limit = Optional.present(limit)
                )
        ).map { response ->
            response.characters.map { it.toDomain() }
        }
    }

    private fun CharacterListQuery.Character.toDomain(): CharacterDetails = CharacterDetails(
            id = id.toLong(),
            name = name,
            nameRu = russian.nullIfEmpty(),
            image = poster.toDomainImage(),
            url = url.appendHostIfNeed(),
            nameAlt = null,
            nameJp = japanese.nullIfEmpty(),
            description = description,
            descriptionSource = descriptionSource,
            seyu = emptyList(),
            animes = emptyList(),
            mangas = emptyList()
    )

    private fun CharacterListQuery.Poster?.toDomainImage(): Image = if (this == null) {
        Image(null, null, null, null)
    } else {
        Image(
                original = mainUrl.appendHostIfNeed(),
                preview = previewUrl.appendHostIfNeed(),
                x96 = preview2xUrl.appendHostIfNeed(),
                x48 = null
        )
    }
}
