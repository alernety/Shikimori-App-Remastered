package com.gnoemes.shikimori.data.repository.roles

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.gnoemes.shikimori.data.graphql.PersonListQuery
import com.gnoemes.shikimori.data.graphql.rxQuery
import com.gnoemes.shikimori.entity.common.domain.Image
import com.gnoemes.shikimori.entity.roles.domain.Person
import com.gnoemes.shikimori.entity.roles.domain.PersonDetails
import com.gnoemes.shikimori.entity.roles.domain.PersonType
import com.gnoemes.shikimori.utils.appendHostIfNeed
import com.gnoemes.shikimori.utils.nullIfEmpty
import io.reactivex.Single
import org.joda.time.DateTime
import javax.inject.Inject

class GraphQLPersonRepositoryImpl @Inject constructor(
        private val apolloClient: ApolloClient
) : PersonRepository {

    override fun getDetails(id: Long): Single<PersonDetails> {
        return apolloClient.rxQuery(
                PersonListQuery(ids = Optional.present(listOf(id.toString())))
        ).map { response ->
            val person = response.people.firstOrNull()
                    ?: throw IllegalArgumentException("Person not found: $id")
            person.toDomain()
        }
    }

    override fun search(query: String, page: Int, limit: Int): Single<List<Person>> {
        return apolloClient.rxQuery(
                PersonListQuery(
                        search = Optional.present(query),
                        page = Optional.present(page),
                        limit = Optional.present(limit)
                )
        ).map { response ->
            response.people.map { it.toDomainPerson() }
        }
    }

    private fun PersonListQuery.Person.toDomain(): PersonDetails = PersonDetails(
            id = id.toLong(),
            name = name,
            nameRu = russian.nullIfEmpty(),
            nameJp = japanese.nullIfEmpty(),
            image = poster.toDomainImage(),
            url = url.appendHostIfNeed(),
            jobTitle = null,
            birthDay = birthOn.toBirthDay(),
            works = emptyList(),
            characters = emptyList(),
            roles = emptyList(),
            topicId = 0L,
            type = resolvePersonType(),
            favoriteType = PersonType.NONE
    )

    private fun PersonListQuery.Person.toDomainPerson(): Person = Person(
            id = id.toLong(),
            name = name.trim(),
            nameRu = russian?.trim().nullIfEmpty(),
            image = poster.toDomainImage(),
            url = url.appendHostIfNeed()
    )

    private fun PersonListQuery.Poster?.toDomainImage(): Image = if (this == null) {
        Image(null, null, null, null)
    } else {
        Image(
                original = mainUrl.appendHostIfNeed(),
                preview = previewUrl.appendHostIfNeed(),
                x96 = preview2xUrl.appendHostIfNeed(),
                x48 = null
        )
    }

    private fun PersonListQuery.BirthOn?.toBirthDay(): DateTime? {
        if (this == null) return null
        val y = year ?: return null
        val m = month ?: return null
        val d = day ?: return null
        return DateTime(y, m, d, 0, 0)
    }

    private fun PersonListQuery.Person.resolvePersonType(): PersonType = when {
        isProducer -> PersonType.PRODUCER
        isMangaka -> PersonType.MANGAKA
        isSeyu -> PersonType.SEYU
        else -> PersonType.NONE
    }
}
