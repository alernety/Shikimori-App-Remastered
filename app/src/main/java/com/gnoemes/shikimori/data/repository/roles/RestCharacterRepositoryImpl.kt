package com.gnoemes.shikimori.data.repository.roles

import com.gnoemes.shikimori.data.network.RolesApi
import com.gnoemes.shikimori.data.repository.common.AnimeResponseConverter
import com.gnoemes.shikimori.data.repository.common.CharacterResponseConverter
import com.gnoemes.shikimori.data.repository.common.ImageResponseConverter
import com.gnoemes.shikimori.data.repository.common.MangaResponseConverter
import com.gnoemes.shikimori.data.repository.common.PersonResponseConverter
import com.gnoemes.shikimori.entity.roles.domain.CharacterDetails
import com.gnoemes.shikimori.utils.appendHostIfNeed
import com.gnoemes.shikimori.utils.nullIfEmpty
import io.reactivex.Single
import javax.inject.Inject

class RestCharacterRepositoryImpl @Inject constructor(
        private val rolesApi: RolesApi,
        private val imageConverter: ImageResponseConverter,
        private val animeConverter: AnimeResponseConverter,
        private val mangaConverter: MangaResponseConverter,
        private val personConverter: PersonResponseConverter,
        private val characterConverter: CharacterResponseConverter
) : CharacterRepository {

    override fun getDetails(id: Long): Single<CharacterDetails> {
        return rolesApi.getCharacterDetails(id)
                .map { response ->
                    CharacterDetails(
                            id = response.id,
                            name = response.name.trim(),
                            nameRu = response.nameRu?.trim().nullIfEmpty(),
                            image = imageConverter.convertResponse(response.image),
                            url = response.url.appendHostIfNeed(),
                            nameAlt = response.nameAlt?.trim().nullIfEmpty(),
                            nameJp = response.nameJp?.trim().nullIfEmpty(),
                            description = response.description,
                            descriptionSource = response.descriptionSource,
                            seyu = response.seyu?.let { personConverter.apply(it) } ?: emptyList(),
                            animes = animeConverter.apply(response.animes),
                            mangas = mangaConverter.apply(response.mangas)
                    )
                }
    }

    override fun search(query: String, page: Int, limit: Int): Single<List<CharacterDetails>> {
        return rolesApi.getCharacterList(
                mapOf(
                        "search" to query,
                        "page" to page.toString(),
                        "limit" to limit.toString()
                )
        ).map { response ->
            characterConverter.apply(response).map { character ->
                CharacterDetails(
                        id = character.id,
                        name = character.name,
                        nameRu = character.nameRu,
                        image = character.image,
                        url = character.url,
                        nameAlt = null,
                        nameJp = null,
                        description = null,
                        descriptionSource = null,
                        seyu = emptyList(),
                        animes = emptyList(),
                        mangas = emptyList()
                )
            }
        }
    }
}
