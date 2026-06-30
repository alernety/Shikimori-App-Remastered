package com.gnoemes.shikimori.data.repository.ranobe

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.gnoemes.shikimori.data.graphql.MangaByIdQuery
import com.gnoemes.shikimori.data.graphql.MangaListQuery
import com.gnoemes.shikimori.data.graphql.rxQuery
import com.gnoemes.shikimori.data.graphql.type.MangaKindEnum
import com.gnoemes.shikimori.data.graphql.type.MangaStatusEnum
import com.gnoemes.shikimori.data.graphql.type.UserRateStatusEnum
import com.gnoemes.shikimori.data.local.db.MangaRateSyncDbSource
import com.gnoemes.shikimori.data.network.RanobeApi
import com.gnoemes.shikimori.data.repository.common.FranchiseResponseConverter
import com.gnoemes.shikimori.data.repository.common.MangaResponseConverter
import com.gnoemes.shikimori.entity.common.domain.Franchise
import com.gnoemes.shikimori.entity.common.domain.Genre
import com.gnoemes.shikimori.entity.common.domain.Image
import com.gnoemes.shikimori.entity.common.domain.Link
import com.gnoemes.shikimori.entity.common.domain.Roles
import com.gnoemes.shikimori.entity.common.domain.Status
import com.gnoemes.shikimori.entity.manga.domain.Manga
import com.gnoemes.shikimori.entity.manga.domain.MangaDetails
import com.gnoemes.shikimori.entity.manga.domain.MangaType
import com.gnoemes.shikimori.entity.rates.domain.RateStatus
import com.gnoemes.shikimori.entity.rates.domain.UserRate
import com.gnoemes.shikimori.entity.roles.domain.Character
import com.gnoemes.shikimori.entity.roles.domain.Person
import com.gnoemes.shikimori.entity.user.domain.Statistic
import com.gnoemes.shikimori.utils.appendHostIfNeed
import com.gnoemes.shikimori.utils.nullIfEmpty
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import org.joda.time.DateTime
import javax.inject.Inject

class GraphQLRanobeRepositoryImpl @Inject constructor(
    private val apolloClient: ApolloClient,
    private val api: RanobeApi,
    private val syncDbSource: MangaRateSyncDbSource,
    private val franchiseConverter: FranchiseResponseConverter,
    private val mangaConverter: MangaResponseConverter
) : RanobeRepository {

    // ── GraphQL-backed methods ──────────────────────────────────────

    override fun getList(filters: Map<String, String>): Single<List<Manga>> =
        apolloClient.rxQuery(toListQuery(filters))
            .map { response -> response.mangas.map { it.toDomainManga() } }

    override fun getDetails(id: Long): Single<MangaDetails> =
        fetchRanobeById(id)
            .flatMap { syncRate(it).toSingleDefault(it) }

    override fun getRoles(id: Long): Single<Roles> =
        fetchRanobeByIdRaw(id)
            .map { manga ->
                Roles(
                    characters = manga.characterRoles?.map { it.toDomainCharacter() } ?: emptyList(),
                    persons = manga.personRoles?.map { it.toDomainPersonWithRoles() } ?: emptyList()
                )
            }

    override fun getLinks(id: Long): Single<List<Link>> =
        fetchRanobeByIdRaw(id)
            .map { manga ->
                manga.externalLinks?.map { it.toDomainLink() } ?: emptyList()
            }

    // ── Retrofit fallback (not in GraphQL schema) ──────────────────

    override fun getSimilar(id: Long): Single<List<Manga>> =
        api.getSimilar(id).map(mangaConverter)

    override fun getFranchise(id: Long): Single<Franchise> =
        api.getFranchise(id).map(franchiseConverter)

    // ── Private data fetching ──────────────────────────────────────

    private fun toListQuery(filters: Map<String, String>): MangaListQuery = MangaListQuery(
        page = Optional.presentIfNotNull(filters["page"]?.toIntOrNull()),
        limit = Optional.presentIfNotNull(filters["limit"]?.toIntOrNull()),
        order = Optional.presentIfNotNull(filters["order"]?.let { com.gnoemes.shikimori.data.graphql.type.OrderEnum.safeValueOf(it) }),
        kind = Optional.presentIfNotNull(filters["kind"]),
        status = Optional.presentIfNotNull(filters["status"]),
        season = Optional.presentIfNotNull(filters["season"]),
        score = Optional.presentIfNotNull(filters["score"]?.toIntOrNull()),
        genre = Optional.presentIfNotNull(filters["genre"]),
        publisher = Optional.presentIfNotNull(filters["publisher"]),
        franchise = Optional.presentIfNotNull(filters["franchise"]),
        censored = Optional.presentIfNotNull(filters["censored"]?.toBooleanStrictOrNull()),
        mylist = Optional.presentIfNotNull(filters["mylist"]),
        ids = Optional.presentIfNotNull(filters["ids"]),
        excludeIds = Optional.presentIfNotNull(filters["excludeIds"]),
        search = Optional.presentIfNotNull(filters["search"])
    )

    private fun fetchRanobeById(id: Long): Single<MangaDetails> =
        fetchRanobeByIdRaw(id).map { it.toDomainDetails(id) }

    private fun fetchRanobeByIdRaw(id: Long): Single<MangaByIdQuery.Manga> =
        apolloClient.rxQuery(MangaByIdQuery(ids = id.toString()))
            .map { response ->
                response.mangas.firstOrNull()
                    ?: throw NoSuchElementException("Ranobe $id not found")
            }

    private fun syncRate(details: MangaDetails): Completable =
        Maybe.fromCallable { details.userRate }
            .filter { it.id != null && it.targetId != null && it.chapters != null }
            .flatMapCompletable { syncDbSource.saveRate(it) }

    // ── Domain conversion: MangaListQuery.Manga → domain types ────

    private fun MangaListQuery.Manga.toDomainManga(): Manga = Manga(
        id = id.toLongOrNull() ?: 0L,
        name = name.trim(),
        nameRu = russian?.trim().nullIfEmpty(),
        image = poster.toDomainListImage(),
        url = url.appendHostIfNeed(),
        type = kind.toDomainMangaType(),
        score = score,
        status = status.toDomainStatus(),
        volumes = volumes,
        chapters = chapters,
        dateAired = airedOn.toDomainDate(),
        dateReleased = releasedOn.toDomainDate()
    )

    private fun MangaListQuery.Poster?.toDomainListImage(): Image = when (this) {
        null -> Image(null, null, null, null)
        else -> Image(
            original = mainUrl.appendHostIfNeed(),
            preview = previewUrl.appendHostIfNeed(),
            x96 = main2xUrl.appendHostIfNeed(),
            x48 = preview2xUrl.appendHostIfNeed()
        )
    }

    private fun MangaListQuery.AiredOn?.toDomainDate(): DateTime? {
        if (this == null) return null
        return date?.let { tryParseDateTime(it) } ?: run {
            val y = year ?: return@run null
            val m = month ?: 1
            val d = day ?: 1
            try { DateTime(y, m, d, 0, 0) } catch (_: Exception) { null }
        }
    }

    private fun MangaListQuery.ReleasedOn?.toDomainDate(): DateTime? {
        if (this == null) return null
        return date?.let { tryParseDateTime(it) } ?: run {
            val y = year ?: return@run null
            val m = month ?: 1
            val d = day ?: 1
            try { DateTime(y, m, d, 0, 0) } catch (_: Exception) { null }
        }
    }

    // ── Domain conversion: MangaByIdQuery.Manga → domain types ────

    private fun MangaByIdQuery.Manga.toDomainDetails(mangaId: Long): MangaDetails = MangaDetails(
        id = mangaId,
        name = name,
        nameRu = russian.nullIfEmpty(),
        image = poster.toDomainDetailsImage(),
        url = url.appendHostIfNeed(),
        type = kind.toDomainMangaType(),
        status = status.toDomainStatus(),
        ageRating = null,
        volumes = volumes,
        chapters = chapters,
        dateAired = airedOn.toDomainDetailsDate(),
        dateReleased = releasedOn.toDomainDetailsDate(),
        score = score ?: 0.0,
        description = description,
        descriptionHtml = descriptionHtml ?: "",
        franchise = franchise,
        favoured = false,
        topicId = topic?.id?.toLongOrNull(),
        genres = genres?.map { it.toDomainGenre() } ?: emptyList(),
        userRate = userRate?.toDomainUserRate(mangaId),
        rateScoresStats = scoresStats?.map { Statistic(it.score.toString(), it.count) } ?: emptyList(),
        rateStatusesStats = statusesStats?.map { Statistic(it.status.rawValue, it.count) } ?: emptyList()
    )

    // ── Sub-type converters (MangaByIdQuery) ───────────────────────

    private fun MangaByIdQuery.Poster?.toDomainDetailsImage(): Image = when (this) {
        null -> Image(null, null, null, null)
        else -> Image(
            original = originalUrl.appendHostIfNeed(),
            preview = previewUrl.appendHostIfNeed(),
            x96 = mainUrl.appendHostIfNeed(),
            x48 = null
        )
    }

    private fun MangaByIdQuery.AiredOn?.toDomainDetailsDate(): DateTime? {
        if (this == null) return null
        return date?.let { tryParseDateTime(it) } ?: run {
            val y = year ?: return@run null
            val m = month ?: 1
            val d = day ?: 1
            try { DateTime(y, m, d, 0, 0) } catch (_: Exception) { null }
        }
    }

    private fun MangaByIdQuery.ReleasedOn?.toDomainDetailsDate(): DateTime? {
        if (this == null) return null
        return date?.let { tryParseDateTime(it) } ?: run {
            val y = year ?: return@run null
            val m = month ?: 1
            val d = day ?: 1
            try { DateTime(y, m, d, 0, 0) } catch (_: Exception) { null }
        }
    }

    private fun MangaKindEnum?.toDomainMangaType(): MangaType = when (this) {
        MangaKindEnum.manga -> MangaType.MANGA
        MangaKindEnum.manhwa -> MangaType.MANHWA
        MangaKindEnum.manhua -> MangaType.MANHUA
        MangaKindEnum.novel -> MangaType.NOVEL
        MangaKindEnum.one_shot -> MangaType.ONE_SHOT
        MangaKindEnum.doujin -> MangaType.DOUJIN
        else -> MangaType.UNKNOWN
    }

    private fun MangaStatusEnum?.toDomainStatus(): Status = when (this) {
        MangaStatusEnum.anons -> Status.ANONS
        MangaStatusEnum.ongoing -> Status.ONGOING
        MangaStatusEnum.released -> Status.RELEASED
        else -> Status.NONE
    }

    private fun MangaByIdQuery.Genre.toDomainGenre(): Genre {
        val normalizedName = name
            .replace(Regex("[-\\s]"), "_")
            .lowercase()
        return Genre.values().firstOrNull { it.equalsName(normalizedName) } ?: Genre.SCI_FI
    }

    private fun MangaByIdQuery.UserRate?.toDomainUserRate(targetId: Long): UserRate? {
        if (this == null) return null
        return UserRate(
            id = id.toLongOrNull(),
            userId = null,
            targetId = targetId,
            targetType = null,
            score = score.toDouble(),
            status = status.toDomainRateStatus(),
            rewatches = rewatches,
            episodes = episodes,
            volumes = volumes,
            chapters = chapters,
            text = text,
            textHtml = null,
            dateCreated = createdAt?.toString()?.let { tryParseDateTime(it) },
            dateUpdated = updatedAt?.toString()?.let { tryParseDateTime(it) }
        )
    }

    private fun UserRateStatusEnum?.toDomainRateStatus(): RateStatus? = when (this) {
        UserRateStatusEnum.watching -> RateStatus.WATCHING
        UserRateStatusEnum.planned -> RateStatus.PLANNED
        UserRateStatusEnum.rewatching -> RateStatus.REWATCHING
        UserRateStatusEnum.completed -> RateStatus.COMPLETED
        UserRateStatusEnum.on_hold -> RateStatus.ON_HOLD
        UserRateStatusEnum.dropped -> RateStatus.DROPPED
        else -> null
    }

    // ── Inline field converters (externalLinks, characterRoles, personRoles) ──

    private fun MangaByIdQuery.ExternalLink.toDomainLink(): Link = Link(
        id = id?.toString()?.toLongOrNull() ?: 0L,
        name = kind.rawValue,
        url = url.appendHostIfNeed()
    )

    private fun MangaByIdQuery.CharacterRole.toDomainCharacter(): Character {
        val ch = character
        return Character(
            id = ch.id.toLongOrNull() ?: 0L,
            name = ch.name,
            nameRu = ch.russian.nullIfEmpty(),
            image = ch.poster?.let { p ->
                Image(
                    original = p.originalUrl.appendHostIfNeed(),
                    preview = p.mainUrl.appendHostIfNeed(),
                    x96 = p.main2xUrl.appendHostIfNeed(),
                    x48 = p.previewUrl.appendHostIfNeed()
                )
            } ?: Image(null, null, null, null),
            url = ch.url.appendHostIfNeed()
        )
    }

    private fun MangaByIdQuery.PersonRole.toDomainPersonWithRoles(): Pair<Person, List<String>> {
        val p = person
        val domainPerson = Person(
            id = p.id.toLongOrNull() ?: 0L,
            name = p.name,
            nameRu = p.russian.nullIfEmpty(),
            image = p.poster?.let { po ->
                Image(
                    original = po.originalUrl.appendHostIfNeed(),
                    preview = po.mainUrl.appendHostIfNeed(),
                    x96 = po.main2xUrl.appendHostIfNeed(),
                    x48 = po.previewUrl.appendHostIfNeed()
                )
            } ?: Image(null, null, null, null),
            url = p.url.appendHostIfNeed()
        )
        val roles = (rolesEn + rolesRu).distinct()
        return Pair(domainPerson, roles)
    }

    // ── Parsing utilities ──────────────────────────────────────────

    private fun tryParseDateTime(value: Any?): DateTime? {
        return when (value) {
            is String -> try {
                org.joda.time.format.ISODateTimeFormat.dateTimeParser().parseDateTime(value)
            } catch (_: Exception) {
                null
            }
            else -> null
        }
    }
}
