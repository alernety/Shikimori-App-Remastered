package com.gnoemes.shikimori.data.repository.rates

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.gnoemes.shikimori.data.graphql.UserRatesQuery
import com.gnoemes.shikimori.data.graphql.rxQuery
import com.gnoemes.shikimori.data.graphql.type.AnimeKindEnum
import com.gnoemes.shikimori.data.graphql.type.AnimeStatusEnum
import com.gnoemes.shikimori.data.graphql.type.MangaKindEnum
import com.gnoemes.shikimori.data.graphql.type.MangaStatusEnum
import com.gnoemes.shikimori.data.graphql.type.UserRateOrderInputType
import com.gnoemes.shikimori.data.graphql.type.UserRateStatusEnum
import com.gnoemes.shikimori.data.graphql.type.UserRateTargetTypeEnum
import com.gnoemes.shikimori.data.local.db.AnimeRateSyncDbSource
import com.gnoemes.shikimori.data.local.db.ChapterDbSource
import com.gnoemes.shikimori.data.local.db.EpisodeDbSource
import com.gnoemes.shikimori.data.local.db.MangaRateSyncDbSource
import com.gnoemes.shikimori.data.network.UserApi
import com.gnoemes.shikimori.data.repository.common.RateResponseConverter
import com.gnoemes.shikimori.entity.anime.domain.Anime
import com.gnoemes.shikimori.entity.anime.domain.AnimeType
import com.gnoemes.shikimori.entity.common.domain.Image
import com.gnoemes.shikimori.entity.common.domain.Status
import com.gnoemes.shikimori.entity.common.domain.Type
import com.gnoemes.shikimori.entity.manga.domain.Manga
import com.gnoemes.shikimori.entity.manga.domain.MangaType
import com.gnoemes.shikimori.entity.rates.domain.Rate
import com.gnoemes.shikimori.entity.rates.domain.RateStatus
import com.gnoemes.shikimori.entity.rates.domain.UserRate
import com.gnoemes.shikimori.utils.appendHostIfNeed
import com.gnoemes.shikimori.utils.firstUpperCase
import com.gnoemes.shikimori.utils.nullIfEmpty
import com.gnoemes.shikimori.utils.rx.GraphQLFallbackNotifier
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.reactivex.Completable
import io.reactivex.Single
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import javax.inject.Inject

/**
 * [RatesRepository] implementation that uses Apollo GraphQL for read operations
 * where possible, falling back to the Retrofit REST API when GraphQL queries are
 * not available or cannot satisfy the request parameters.
 *
 * Read endpoints migrated to GraphQL:
 * - [getUserRates] uses [UserRatesQuery] when no [targetId] and a single status
 * - [getAnimeRates] / [getMangaRates] use [UserRatesQuery] with partial Anime/Manga domain
 * - [getRate] remains on REST (no GraphQL equivalent)
 *
 * Mutations (create/update/delete/sync/increment) stay on REST via [UserApi].
 */
class GraphQLRatesRepositoryImpl @Inject constructor(
    private val apolloClient: ApolloClient,
    private val api: UserApi,
    private val converter: RateResponseConverter,
    private val episodeDbSource: EpisodeDbSource,
    private val chapterDbSource: ChapterDbSource,
    private val animeSyncSource: AnimeRateSyncDbSource,
    private val mangaSyncSource: MangaRateSyncDbSource,
    private val graphQLFallbackNotifier: GraphQLFallbackNotifier
) : RatesRepository {

    // ──────────────────────────────────────────────
    //  Read: getAnimeRates / getMangaRates
    //  GraphQL primary path, REST fallback
    //  Note: GraphQL uses proper page-based pagination (not offset).
    //  The Shikimori v1 REST API's `page` parameter acts as an offset (skip N items),
    //  so the REST fallback recalculates the offset from page number.
    // ──────────────────────────────────────────────

    override fun getAnimeRates(id: Long, page: Int, limit: Int, rateStatus: RateStatus, order: UserRateOrderInputType?): Single<List<Rate>> {
        val query = UserRatesQuery(
            page = Optional.present(page),
            limit = Optional.present(limit),
            userId = Optional.present(id.toString()),
            targetType = Optional.present(UserRateTargetTypeEnum.Anime),
            status = Optional.present(rateStatus.status.toGraphQLRateStatus()),
            order = if (order != null) Optional.present(order) else Optional.Absent,
            includeAnime = true,
            includeManga = false
        )
        return apolloClient.rxQuery(query)
            .map { response -> response.userRates.map { it.toDomainRate() } }
            .onErrorResumeNext { error ->
                // Graceful fallback to REST if GraphQL fails
                FirebaseCrashlytics.getInstance().recordException(error)
                graphQLFallbackNotifier.notify(error)
                val offset = (page - 1) * limit
                api.getUserAnimeRates(id, offset, limit, rateStatus.status)
                    .map(converter)
                    .onErrorResumeNext { if (it is NoSuchElementException) Single.just(emptyList()) else Single.error(it) }
            }
    }

    override fun getMangaRates(id: Long, page: Int, limit: Int, rateStatus: RateStatus, order: UserRateOrderInputType?): Single<List<Rate>> {
        val query = UserRatesQuery(
            page = Optional.present(page),
            limit = Optional.present(limit),
            userId = Optional.present(id.toString()),
            targetType = Optional.present(UserRateTargetTypeEnum.Manga),
            status = Optional.present(rateStatus.status.toGraphQLRateStatus()),
            order = if (order != null) Optional.present(order) else Optional.Absent,
            includeAnime = false,
            includeManga = true
        )
        return apolloClient.rxQuery(query)
            .map { response -> response.userRates.map { it.toDomainRate() } }
            .onErrorResumeNext { error ->
                // Graceful fallback to REST if GraphQL fails
                FirebaseCrashlytics.getInstance().recordException(error)
                graphQLFallbackNotifier.notify(error)
                val offset = (page - 1) * limit
                api.getUserMangaRates(id, offset, limit, rateStatus.status)
                    .map(converter)
                    .onErrorResumeNext { if (it is NoSuchElementException) Single.just(emptyList()) else Single.error(it) }
            }
    }

    // ──────────────────────────────────────────────
    //  Read: getUserRates
    //  GraphQL primary path, REST fallback
    // ──────────────────────────────────────────────

    override fun getUserRates(
        id: Long,
        targetId: Long?,
        target: Type?,
        statuses: String?,
        page: Int,
        limit: Int,
        order: UserRateOrderInputType?
    ): Single<List<UserRate>> {
        // GraphQL UserRatesQuery doesn't support targetId filtering
        if (targetId != null) {
            return fallbackGetUserRates(id, targetId, target, statuses, page, limit, order)
        }

        // GraphQL UserRatesQuery only accepts a single status value
        if (statuses != null && statuses.contains(",")) {
            return fallbackGetUserRates(id, null, target, statuses, page, limit, order)
        }

        return getUserRatesGraphQL(id, target, statuses, page, limit, order)
    }

    // ──────────────────────────────────────────────
    //  Read: getRate (no GraphQL equivalent)
    // ──────────────────────────────────────────────

    override fun getRate(id: Long): Single<UserRate> =
        api.getRate(id)
            .map { converter.convertUserRateResponse(null, it) }
            .flatMap { syncRate(it).toSingleDefault(it) }

    // ──────────────────────────────────────────────
    //  Mutations — all REST
    // ──────────────────────────────────────────────

    override fun syncRate(id: Long): Completable =
        getRate(id)
            .flatMapCompletable { syncRate(it) }

    override fun syncRate(rate: UserRate): Completable =
        when (rate.targetType) {
            Type.ANIME -> syncAnimeRate(rate)
            Type.MANGA, Type.RANOBE -> syncMangaRate(rate)
            else -> Completable.error(IllegalArgumentException())
        }

    override fun updateRate(rate: UserRate): Completable =
        api.updateRate(rate.id!!, converter.convertCreateOrUpdateRequest(rate))
            .map { converter.convertUserRateResponse(null, it) }
            .flatMapCompletable { syncRate(it) }

    override fun deleteRate(id: Long): Completable =
        getRate(id)
            .flatMapCompletable { deleteRate(it) }
            .andThen(api.deleteRate(id))

    override fun increment(rateId: Long): Completable = api.increment(rateId)

    override fun increment(rate: UserRate): Completable =
        when (rate.targetType) {
            Type.ANIME -> incrementAnimeRate(rate)
            Type.MANGA, Type.RANOBE -> incrementMangaRate(rate)
            else -> Completable.error(IllegalArgumentException())
        }

    override fun createRate(id: Long, type: Type, rate: UserRate, userId: Long): Completable =
        when (type) {
            Type.ANIME -> Completable.fromSingle(createAnimeRate(id, rate, userId))
            Type.MANGA, Type.RANOBE -> Completable.fromSingle(createMangaRate(id, rate, userId))
            else -> Completable.error(IllegalStateException())
        }

    override fun createRateWithResult(id: Long, type: Type, rate: UserRate, userId: Long): Single<UserRate> =
        when (type) {
            Type.ANIME -> createAnimeRate(id, rate, userId)
            Type.MANGA, Type.RANOBE -> createMangaRate(id, rate, userId)
            else -> Single.error(IllegalStateException())
        }

    // ──────────────────────────────────────────────
    //  Private: GraphQL implementation
    // ──────────────────────────────────────────────

    private fun getUserRatesGraphQL(
        userId: Long,
        target: Type?,
        statuses: String?,
        page: Int,
        limit: Int,
        order: UserRateOrderInputType?
    ): Single<List<UserRate>> {
        val query = UserRatesQuery(
            page = Optional.present(page),
            limit = Optional.present(limit),
            userId = Optional.present(userId.toString()),
            targetType = if (target != null) {
                Optional.present(target.toGraphQLTargetType())
            } else {
                Optional.Absent
            },
            status = if (statuses != null) {
                Optional.present(statuses.toGraphQLRateStatus())
            } else {
                Optional.Absent
            },
            order = if (order != null) Optional.present(order) else Optional.Absent,
            includeAnime = target == null || target == Type.ANIME,
            includeManga = target == null || target == Type.MANGA || target == Type.RANOBE
        )

        return apolloClient.rxQuery(query)
            .map { response ->
                response.userRates.map { gqlRate -> gqlRate.toDomainUserRate(userId) }
            }
            .onErrorResumeNext { error ->
                // Graceful fallback: if GraphQL fails, try REST
                FirebaseCrashlytics.getInstance().recordException(error)
                graphQLFallbackNotifier.notify(error)
                fallbackGetUserRates(userId, null, target, statuses, page, limit, order)
            }
    }

    // ──────────────────────────────────────────────
    //  Private: REST fallback
    // ──────────────────────────────────────────────

    private fun fallbackGetUserRates(
        id: Long,
        targetId: Long?,
        target: Type?,
        statuses: String?,
        page: Int,
        limit: Int,
        order: UserRateOrderInputType?
    ): Single<List<UserRate>> =
        api.getUserRates(id, targetId, target?.name?.lowercase()?.firstUpperCase(), statuses, page, limit)
            .map { list -> list.mapNotNull { converter.convertUserRateResponse(targetId, it) } }

    // ──────────────────────────────────────────────
    //  Private: REST mutation helpers
    // ──────────────────────────────────────────────

    private fun createAnimeRate(id: Long, rate: UserRate, userId: Long): Single<UserRate> =
        episodeDbSource.getWatchedEpisodesCount(id)
            .map { converter.convertCreateOrUpdateRequest(id, Type.ANIME, rate.copy(episodes = it), userId) }
            .flatMap { api.createRate(it) }
            .map { converter.convertUserRateResponse(id, it) }
            .flatMap { syncRate(it).toSingleDefault(it) }

    private fun createMangaRate(id: Long, rate: UserRate, userId: Long): Single<UserRate> =
        chapterDbSource.getReadedChapterCount(id)
            .map { converter.convertCreateOrUpdateRequest(id, Type.MANGA, rate.copy(chapters = it), userId) }
            .flatMap { api.createRate(it) }
            .map { converter.convertUserRateResponse(id, it) }
            .flatMap { syncRate(it).toSingleDefault(it) }

    private fun syncAnimeRate(it: UserRate): Completable =
        Single.just(it)
            .filter { it.targetId != null && it.episodes != null }
            .flatMapCompletable { animeSyncSource.saveRate(it) }

    private fun syncMangaRate(it: UserRate): Completable =
        Single.just(it)
            .filter { it.targetId != null && it.chapters != null }
            .flatMapCompletable { mangaSyncSource.saveRate(it) }

    private fun deleteRate(rate: UserRate): Completable =
        when (rate.targetType) {
            Type.ANIME -> deleteAnimeRate(rate)
            Type.MANGA, Type.RANOBE -> deleteMangaRate(rate)
            else -> Completable.complete()
        }

    private fun deleteAnimeRate(rate: UserRate): Completable =
        Single.just(rate)
            .filter { it.targetId != null }
            .flatMapCompletable {
                episodeDbSource.clearEpisodes(rate.targetId!!)
                    .andThen(animeSyncSource.clearRate(rate.targetId))
            }

    private fun deleteMangaRate(rate: UserRate): Completable =
        Single.just(rate)
            .filter { it.targetId != null }
            .flatMapCompletable {
                chapterDbSource.clearChapters(rate.targetId!!)
                    .andThen(mangaSyncSource.clearRate(rate.targetId))
            }

    private fun incrementMangaRate(rate: UserRate): Completable =
        Single.just(rate)
            .filter { it.targetId != null }
            .flatMapSingle { chapterDbSource.getReadedChapterCount(it.targetId!!) }
            .flatMapCompletable { count ->
                mangaSyncSource.getRate(rate.id!!)
                    .flatMapCompletable { mangaSyncSource.saveRate(it.copy(chapters = count + 1)) }
            }
            .andThen(increment(rate.id!!))

    private fun incrementAnimeRate(rate: UserRate): Completable =
        Single.just(rate)
            .filter { it.targetId != null }
            .flatMapSingle { episodeDbSource.getWatchedEpisodesCount(it.targetId!!) }
            .flatMapCompletable { count ->
                animeSyncSource.getRate(rate.id!!)
                    .onErrorResumeNext { error ->
                        FirebaseCrashlytics.getInstance().recordException(error)
                        graphQLFallbackNotifier.notify(error)
                        getRate(rate.id)
                    }
                    .flatMapCompletable { animeSyncSource.saveRate(it.copy(episodes = count)) }
            }
            .andThen(increment(rate.id!!))
}

// ──────────────────────────────────────────────────
//  Mapping helpers: GraphQL generated types → domain
// ──────────────────────────────────────────────────

private fun Type.toGraphQLTargetType(): UserRateTargetTypeEnum = when (this) {
    Type.ANIME -> UserRateTargetTypeEnum.Anime
    Type.MANGA, Type.RANOBE -> UserRateTargetTypeEnum.Manga
    else -> UserRateTargetTypeEnum.UNKNOWN__
}

private fun String.toGraphQLRateStatus(): UserRateStatusEnum = when (this) {
    "watching" -> UserRateStatusEnum.watching
    "planned" -> UserRateStatusEnum.planned
    "rewatching" -> UserRateStatusEnum.rewatching
    "completed" -> UserRateStatusEnum.completed
    "on_hold" -> UserRateStatusEnum.on_hold
    "dropped" -> UserRateStatusEnum.dropped
    else -> UserRateStatusEnum.UNKNOWN__
}

private fun UserRateStatusEnum.toDomainRateStatus(): RateStatus = when (this) {
    UserRateStatusEnum.watching -> RateStatus.WATCHING
    UserRateStatusEnum.planned -> RateStatus.PLANNED
    UserRateStatusEnum.rewatching -> RateStatus.REWATCHING
    UserRateStatusEnum.completed -> RateStatus.COMPLETED
    UserRateStatusEnum.on_hold -> RateStatus.ON_HOLD
    UserRateStatusEnum.dropped -> RateStatus.DROPPED
    UserRateStatusEnum.UNKNOWN__ -> RateStatus.PLANNED
}

private fun UserRatesQuery.UserRate.toDomainUserRate(userId: Long): UserRate {
    val targetType = when {
        anime != null -> Type.ANIME
        manga != null -> Type.MANGA
        else -> null
    }

    val targetId = anime?.id?.toLongOrNull() ?: manga?.id?.toLongOrNull()

    return UserRate(
        id = id.toLongOrNull(),
        userId = userId,
        targetId = targetId,
        targetType = targetType,
        score = score.toDouble(),
        status = status.toDomainRateStatus(),
        rewatches = rewatches,
        episodes = episodes,
        volumes = volumes,
        chapters = chapters,
        text = text,
        textHtml = null, // GraphQL response does not include textHtml
        dateCreated = createdAt.parseGraphQLDateTime(),
        dateUpdated = updatedAt.parseGraphQLDateTime()
    )
}

/**
 * Parse a GraphQL DateTime (ISO 8601 string, or [Any]) to Joda [DateTime].
 * Returns null for unparseable or null values.
 */
private fun Any?.parseGraphQLDateTime(): DateTime? {
    if (this == null) return null
    return when (this) {
        is String -> try {
            ISODateTimeFormat.dateTimeParser().parseDateTime(this)
        } catch (_: Exception) {
            null
        }
        else -> null
    }
}

// ──────────────────────────────────────────────────
//  GraphQL UserRatesQuery → domain Rate mapping
// ──────────────────────────────────────────────────

private fun UserRatesQuery.UserRate.toDomainRate(): Rate {
    return Rate(
        id = id.toLongOrNull() ?: 0L,
        score = score,
        status = status.toDomainRateStatus(),
        text = text,
        textHtml = null,
        episodes = episodes,
        chapters = chapters,
        volumes = volumes,
        rewatches = rewatches,
        createdDateTime = createdAt.parseGraphQLDateTime(),
        updatedDateTime = updatedAt.parseGraphQLDateTime(),
        anime = anime?.toDomainAnime(),
        manga = manga?.toDomainManga()
    )
}

private fun UserRatesQuery.Anime.toDomainAnime(): Anime {
    return Anime(
        id = id.toLongOrNull() ?: 0L,
        name = name,
        nameRu = russian?.trim().nullIfEmpty(),
        image = poster.toDomainImage(),
        url = url.appendHostIfNeed(),
        type = kind.toAnimeType(),
        score = score,
        status = status.toDomainStatus(),
        episodes = episodes,
        episodesAired = episodesAired,
        dateAired = airedOn.toDomainDate(),
        dateReleased = releasedOn.toDomainDate()
    )
}

private fun UserRatesQuery.Manga.toDomainManga(): Manga {
    return Manga(
        id = id.toLongOrNull() ?: 0L,
        name = name,
        nameRu = russian?.trim().nullIfEmpty(),
        image = poster.toDomainImage(),
        url = url.appendHostIfNeed(),
        type = kind.toDomainMangaType(),
        score = score,
        status = status.toDomainStatus(),
        volumes = volumes,
        chapters = chapters,
        dateAired = airedOn.toDomainDate(),
        dateReleased = releasedOn.toDomainDate()
    )
}

private fun UserRatesQuery.Poster?.toDomainImage(): Image {
    if (this == null) return Image(null, null, null, null)
    return Image(
        original = mainUrl.appendHostIfNeed(),
        preview = previewUrl.appendHostIfNeed(),
        x96 = main2xUrl.appendHostIfNeed(),
        x48 = null
    )
}

private fun UserRatesQuery.Poster1?.toDomainImage(): Image {
    if (this == null) return Image(null, null, null, null)
    return Image(
        original = mainUrl.appendHostIfNeed(),
        preview = previewUrl.appendHostIfNeed(),
        x96 = main2xUrl.appendHostIfNeed(),
        x48 = null
    )
}

// ──────────────────────────────────────────────────
//  GraphQL enum → domain enum extensions
// ──────────────────────────────────────────────────

private fun AnimeKindEnum?.toAnimeType(): AnimeType = when (this) {
    AnimeKindEnum.tv -> AnimeType.TV
    AnimeKindEnum.movie -> AnimeType.MOVIE
    AnimeKindEnum.ova -> AnimeType.OVA
    AnimeKindEnum.ona -> AnimeType.ONA
    AnimeKindEnum.special -> AnimeType.SPECIAL
    AnimeKindEnum.music -> AnimeType.MUSIC
    AnimeKindEnum.tv_13 -> AnimeType.TV_13
    AnimeKindEnum.tv_24 -> AnimeType.TV_24
    AnimeKindEnum.tv_48 -> AnimeType.TV_48
    null -> AnimeType.NONE
    else -> AnimeType.NONE
}

private fun AnimeStatusEnum?.toDomainStatus(): Status = when (this) {
    AnimeStatusEnum.anons -> Status.ANONS
    AnimeStatusEnum.ongoing -> Status.ONGOING
    AnimeStatusEnum.released -> Status.RELEASED
    null -> Status.NONE
    else -> Status.NONE
}

private fun MangaKindEnum?.toDomainMangaType(): MangaType = when (this) {
    MangaKindEnum.manga -> MangaType.MANGA
    MangaKindEnum.manhwa -> MangaType.MANHWA
    MangaKindEnum.manhua -> MangaType.MANHUA
    MangaKindEnum.novel -> MangaType.NOVEL
    MangaKindEnum.one_shot -> MangaType.ONE_SHOT
    MangaKindEnum.doujin -> MangaType.DOUJIN
    null -> MangaType.UNKNOWN
    else -> MangaType.UNKNOWN
}

private fun MangaStatusEnum?.toDomainStatus(): Status = when (this) {
    MangaStatusEnum.anons -> Status.ANONS
    MangaStatusEnum.ongoing -> Status.ONGOING
    MangaStatusEnum.released -> Status.RELEASED
    null -> Status.NONE
    else -> Status.NONE
}

// ──────────────────────────────────────────────────
//  GraphQL IncompleteDate → Joda DateTime
// ──────────────────────────────────────────────────

private fun UserRatesQuery.AiredOn?.toDomainDate(): DateTime? {
    if (this == null) return null
    return date?.toString()?.parseGraphQLDateTime() ?: run {
        val y = year ?: return@run null
        val m = month ?: 1
        val d = day ?: 1
        try { DateTime(y, m, d, 0, 0) } catch (_: Exception) { null }
    }
}

private fun UserRatesQuery.ReleasedOn?.toDomainDate(): DateTime? {
    if (this == null) return null
    return date?.toString()?.parseGraphQLDateTime() ?: run {
        val y = year ?: return@run null
        val m = month ?: 1
        val d = day ?: 1
        try { DateTime(y, m, d, 0, 0) } catch (_: Exception) { null }
    }
}

private fun UserRatesQuery.AiredOn1?.toDomainDate(): DateTime? {
    if (this == null) return null
    return date?.toString()?.parseGraphQLDateTime() ?: run {
        val y = year ?: return@run null
        val m = month ?: 1
        val d = day ?: 1
        try { DateTime(y, m, d, 0, 0) } catch (_: Exception) { null }
    }
}

private fun UserRatesQuery.ReleasedOn1?.toDomainDate(): DateTime? {
    if (this == null) return null
    return date?.toString()?.parseGraphQLDateTime() ?: run {
        val y = year ?: return@run null
        val m = month ?: 1
        val d = day ?: 1
        try { DateTime(y, m, d, 0, 0) } catch (_: Exception) { null }
    }
}
