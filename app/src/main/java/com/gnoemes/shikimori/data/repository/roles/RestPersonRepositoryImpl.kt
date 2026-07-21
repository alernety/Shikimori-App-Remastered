package com.gnoemes.shikimori.data.repository.roles

import com.gnoemes.shikimori.data.network.RolesApi
import com.gnoemes.shikimori.data.repository.common.AnimeResponseConverter
import com.gnoemes.shikimori.data.repository.common.CharacterResponseConverter
import com.gnoemes.shikimori.data.repository.common.ImageResponseConverter
import com.gnoemes.shikimori.data.repository.common.MangaResponseConverter
import com.gnoemes.shikimori.data.repository.common.PersonResponseConverter
import com.gnoemes.shikimori.entity.roles.data.PersonDetailsResponse
import com.gnoemes.shikimori.entity.roles.domain.Person
import com.gnoemes.shikimori.entity.roles.domain.PersonDetails
import com.gnoemes.shikimori.entity.roles.domain.PersonType
import com.gnoemes.shikimori.entity.roles.domain.Work
import com.gnoemes.shikimori.utils.appendHostIfNeed
import com.gnoemes.shikimori.utils.nullIfEmpty
import io.reactivex.Single
import javax.inject.Inject

class RestPersonRepositoryImpl @Inject constructor(
        private val rolesApi: RolesApi,
        private val imageConverter: ImageResponseConverter,
        private val animeConverter: AnimeResponseConverter,
        private val mangaConverter: MangaResponseConverter,
        private val personConverter: PersonResponseConverter,
        private val characterConverter: CharacterResponseConverter
) : PersonRepository {

    override fun getDetails(id: Long): Single<PersonDetails> {
        return rolesApi.getPersonDetails(id)
                .map { response ->
                    PersonDetails(
                            id = response.id,
                            name = response.name.trim(),
                            nameRu = response.nameRu?.trim().nullIfEmpty(),
                            nameJp = response.nameJp?.trim().nullIfEmpty(),
                            image = imageConverter.convertResponse(response.image),
                            url = response.url.appendHostIfNeed(),
                            jobTitle = response.jobTitle?.trim().nullIfEmpty(),
                            birthDay = response.birthDay,
                            works = response.works?.map { workResponse ->
                                Work(
                                        anime = animeConverter.convertResponse(workResponse.anime),
                                        manga = mangaConverter.convertResponse(workResponse.manga),
                                        role = workResponse.role
                                )
                            } ?: emptyList(),
                            characters = response.roles?.flatMap { seyuRole ->
                                characterConverter.apply(seyuRole.characters)
                            } ?: emptyList(),
                            roles = response.rolesGrouped,
                            topicId = response.topicId,
                            type = resolveType(response),
                            favoriteType = resolveFavoriteType(response)
                    )
                }
    }

    override fun search(query: String, page: Int, limit: Int): Single<List<Person>> {
        return rolesApi.getPersonList(
                mapOf(
                        "search" to query,
                        "page" to page.toString(),
                        "limit" to limit.toString()
                )
        ).map { response ->
            personConverter.apply(response)
        }
    }

    private fun resolveType(response: PersonDetailsResponse): PersonType = when {
        response.isSeyu -> PersonType.SEYU
        response.isProducer -> PersonType.PRODUCER
        response.isMangaka -> PersonType.MANGAKA
        else -> PersonType.PERSON
    }

    private fun resolveFavoriteType(response: PersonDetailsResponse): PersonType = when {
        response.isFavoriteSeyu -> PersonType.SEYU
        response.isFavoriteProducer -> PersonType.PRODUCER
        response.isFavoriteMangaka -> PersonType.MANGAKA
        response.isFavoritePerson -> PersonType.PERSON
        else -> PersonType.NONE
    }
}
