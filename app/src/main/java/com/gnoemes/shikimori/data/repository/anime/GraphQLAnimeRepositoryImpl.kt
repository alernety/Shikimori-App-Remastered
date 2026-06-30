package com.gnoemes.shikimori.data.repository.anime

import com.apollographql.apollo.ApolloClient
import com.gnoemes.shikimori.data.graphql.AnimeByIdQuery
import com.gnoemes.shikimori.data.graphql.AnimeStaffQuery
import com.gnoemes.shikimori.data.graphql.rxQuery
import com.gnoemes.shikimori.data.graphql.type.AnimeKindEnum
import com.gnoemes.shikimori.data.graphql.type.AnimeRatingEnum
import com.gnoemes.shikimori.data.graphql.type.AnimeStatusEnum
import com.gnoemes.shikimori.data.graphql.type.UserRateStatusEnum
import com.gnoemes.shikimori.data.graphql.type.VideoKindEnum
import com.gnoemes.shikimori.data.local.db.AnimeRateSyncDbSource
import com.gnoemes.shikimori.data.local.db.EpisodeDbSource
import com.gnoemes.shikimori.data.network.AnimeApi
import com.gnoemes.shikimori.data.repository.common.AnimeResponseConverter
import com.gnoemes.shikimori.data.repository.common.FranchiseResponseConverter
import com.gnoemes.shikimori.entity.anime.domain.Anime
import com.gnoemes.shikimori.entity.anime.domain.AnimeDetails
import com.gnoemes.shikimori.entity.anime.domain.AnimeType
import com.gnoemes.shikimori.entity.anime.domain.AnimeVideo
import com.gnoemes.shikimori.entity.anime.domain.AnimeVideoType
import com.gnoemes.shikimori.entity.anime.domain.Screenshot
import com.gnoemes.shikimori.entity.common.domain.AgeRating
import com.gnoemes.shikimori.entity.common.domain.Genre
import com.gnoemes.shikimori.entity.common.domain.Image
import com.gnoemes.shikimori.entity.common.domain.Link
import com.gnoemes.shikimori.entity.common.domain.Roles
import com.gnoemes.shikimori.entity.common.domain.Status
import com.gnoemes.shikimori.entity.rates.domain.RateStatus
import com.gnoemes.shikimori.entity.rates.domain.UserRate
import com.gnoemes.shikimori.entity.roles.domain.Character
import com.gnoemes.shikimori.entity.roles.domain.Person
import com.gnoemes.shikimori.entity.studio.Studio
import com.gnoemes.shikimori.entity.user.domain.Statistic
import com.gnoemes.shikimori.utils.appendHostIfNeed
import com.gnoemes.shikimori.utils.nullIfEmpty
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import org.joda.time.DateTime
import javax.inject.Inject

/**
 * [AnimeRepository] implementation backed by GraphQL via [ApolloClient],
 * with a Retrofit [AnimeApi] fallback for operations not yet covered by the GraphQL schema.
 *
 * GraphQL methods:
 * - [getDetails] via [AnimeByIdQuery]
 * - [getScreenshots] via [AnimeByIdQuery] (inline `screenshots` field)
 * - [getLinks] via [AnimeByIdQuery] (inline `externalLinks` field)
 * - [getRoles] via [AnimeStaffQuery] (separate query for `characterRoles` + `personRoles` fields)
 *
 * Fallback (Retrofit):
 * - [getSimilar] — not in current GraphQL schema (uses [/api/animes/{id}/similar])
 * - [getFranchise] — not in current GraphQL schema
 * - [getLocalWatchedAnimeIds] — local DB operation
 */
class GraphQLAnimeRepositoryImpl @Inject constructor(
        private val apolloClient: ApolloClient,
        private val api: AnimeApi,
        private val syncDbSource: AnimeRateSyncDbSource,
        private val episodeDbSource: EpisodeDbSource,
        private val animeConverter: AnimeResponseConverter,
        private val franchiseConverter: FranchiseResponseConverter
) : AnimeRepository {

    override fun getDetails(id: Long): Single<AnimeDetails> {
        return apolloClient.rxQuery(AnimeByIdQuery(ids = id.toString()))
                .map { response -> response.animes.first() }
                .map { anime -> anime.toAnimeDetails() }
                .flatMap { syncRate(it).toSingleDefault(it).onErrorReturnItem(it) }
    }

    override fun getScreenshots(id: Long): Single<List<Screenshot>> =
            apolloClient.rxQuery(AnimeByIdQuery(ids = id.toString()))
                    .map { response -> response.animes.first() }
                    .map { anime -> anime.screenshots.map { it.toDomainScreenshot() } }

    override fun getSimilar(id: Long): Single<List<Anime>> =
            api.getSimilar(id)
                    .map(animeConverter)

    override fun getLinks(id: Long): Single<List<Link>> =
            apolloClient.rxQuery(AnimeByIdQuery(ids = id.toString()))
                    .map { response -> response.animes.first() }
                    .map { anime -> anime.externalLinks?.map { it.toDomainLink() } ?: emptyList() }

    override fun getRoles(id: Long): Single<Roles> =
            apolloClient.rxQuery(AnimeStaffQuery(ids = id.toString()))
                    .map { response -> response.animes.first() }
                    .map { anime ->
                        Roles(
                                characters = anime.characterRoles?.map { it.toDomainCharacter() } ?: emptyList(),
                                persons = anime.personRoles?.map { it.toDomainPersonWithRoles() } ?: emptyList()
                        )
                    }

    // --- Retrofit fallback methods ---

    override fun getFranchise(id: Long) =
            api.getFranchise(id).map(franchiseConverter)

    override fun getLocalWatchedAnimeIds(): Single<LinkedHashSet<Long>> =
            episodeDbSource.getWatchedAnimeIds().map { LinkedHashSet(it) }

    private fun syncRate(details: AnimeDetails): Completable =
            Maybe.fromCallable { details.userRate }
                    .filter { it.id != null && it.targetId != null && it.episodes != null }
                    .flatMapCompletable { syncDbSource.saveRate(it) }

    // ========================
    // Mapping extensions
    // ========================

    private fun AnimeByIdQuery.Anime.toAnimeDetails(): AnimeDetails {
        return AnimeDetails(
                id = id.toLongOrNull() ?: 0L,
                name = name,
                nameRu = russian.nullIfEmpty(),
                image = poster.toDomainImage(),
                url = url.appendHostIfNeed(),
                type = kind.toAnimeType(),
                status = status.toDomainStatus(),
                episodes = episodes,
                episodesAired = episodesAired,
                dateAired = airedOn.toDateTime(),
                dateReleased = releasedOn.toDateTime(),
                nextEpisodeDate = nextEpisodeAt?.toString()?.let { parseDateTime(it) },
                namesEnglish = listOfNotNull(english).nullIfEmpty(),
                namesJapanese = listOfNotNull(japanese).nullIfEmpty(),
                ageRating = rating.toAgeRating(),
                score = score ?: 0.0,
                duration = duration ?: 0,
                description = description,
                descriptionHtml = descriptionHtml ?: "",
                franchise = franchise,
                favoured = false,
                topicId = topic?.id?.toLongOrNull(),
                genres = genres?.mapNotNull { it.toDomainGenre() } ?: emptyList(),
                userRate = userRate?.toDomainUserRate(id.toLongOrNull() ?: 0L),
                videos = videos.map { it.toAnimeVideo() }.nullIfEmpty(),
                studios = studios.map { it.toDomainStudio() },
                rateScoresStats = scoresStats?.map { Statistic(it.score.toString(), it.count) } ?: emptyList(),
                rateStatusesStats = statusesStats?.map { Statistic(it.status.rawValue, it.count) } ?: emptyList()
        )
    }

    private fun AnimeByIdQuery.Poster?.toDomainImage(): Image {
        if (this == null) return Image(null, null, null, null)
        return Image(
                original = originalUrl.appendHostIfNeed(),
                preview = mainUrl.appendHostIfNeed(),
                x96 = main2xUrl.appendHostIfNeed(),
                x48 = previewUrl.appendHostIfNeed()
        )
    }

    private fun AnimeKindEnum?.toAnimeType(): AnimeType {
        return when (this) {
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
    }

    private fun AnimeStatusEnum?.toDomainStatus(): Status {
        return when (this) {
            AnimeStatusEnum.anons -> Status.ANONS
            AnimeStatusEnum.ongoing -> Status.ONGOING
            AnimeStatusEnum.released -> Status.RELEASED
            null -> Status.NONE
            else -> Status.NONE
        }
    }

    private fun AnimeRatingEnum?.toAgeRating(): AgeRating? {
        return when (this) {
            AnimeRatingEnum.none -> AgeRating.NONE
            AnimeRatingEnum.g -> AgeRating.G
            AnimeRatingEnum.pg -> AgeRating.PG
            AnimeRatingEnum.pg_13 -> AgeRating.PG_13
            AnimeRatingEnum.r -> AgeRating.R
            AnimeRatingEnum.r_plus -> AgeRating.R_PLUS
            AnimeRatingEnum.rx -> AgeRating.RX
            null -> AgeRating.NONE
            else -> AgeRating.NONE
        }
    }

    private fun AnimeByIdQuery.AiredOn?.toDateTime(): DateTime? {
        if (this == null) return null
        val year = year ?: return null
        val month = month ?: 1
        val day = day ?: 1
        return try {
            DateTime(year, month, day, 0, 0)
        } catch (e: Exception) {
            // fallback to date string parsing if available
            date?.toString()?.let { parseDateTime(it) }
        }
    }

    private fun AnimeByIdQuery.ReleasedOn?.toDateTime(): DateTime? {
        if (this == null) return null
        val year = year ?: return null
        val month = month ?: 1
        val day = day ?: 1
        return try {
            DateTime(year, month, day, 0, 0)
        } catch (e: Exception) {
            date?.toString()?.let { parseDateTime(it) }
        }
    }

    private fun parseDateTime(dateString: String): DateTime? {
        return try {
            DateTime.parse(dateString)
        } catch (e: Exception) {
            null
        }
    }

    private fun AnimeByIdQuery.Genre.toDomainGenre(): Genre? {
        val normalizedName = name.lowercase()
                .replace(" ", "_")
                .replace("-", "_")
        return Genre.values().firstOrNull { it.equalsName(normalizedName) }
    }

    private fun AnimeByIdQuery.UserRate.toDomainUserRate(targetId: Long): UserRate {
        return UserRate(
                id = id.toLongOrNull(),
                userId = null,
                targetId = targetId,
                targetType = null,
                score = score.toDouble(),
                status = status.toRateStatus(),
                episodes = episodes,
                chapters = chapters,
                volumes = volumes,
                rewatches = rewatches,
                text = text,
                textHtml = null,
                dateCreated = createdAt.toString().let { parseDateTime(it) },
                dateUpdated = updatedAt.toString().let { parseDateTime(it) }
        )
    }

    private fun UserRateStatusEnum.toRateStatus(): RateStatus {
        return when (this) {
            UserRateStatusEnum.planned -> RateStatus.PLANNED
            UserRateStatusEnum.watching -> RateStatus.WATCHING
            UserRateStatusEnum.rewatching -> RateStatus.REWATCHING
            UserRateStatusEnum.completed -> RateStatus.COMPLETED
            UserRateStatusEnum.on_hold -> RateStatus.ON_HOLD
            UserRateStatusEnum.dropped -> RateStatus.DROPPED
            else -> RateStatus.PLANNED
        }
    }

    private fun AnimeByIdQuery.Video.toAnimeVideo(): AnimeVideo {
        return AnimeVideo(
                id = id.toLongOrNull() ?: 0L,
                name = name,
                imageUrl = imageUrl,
                url = playerUrl,
                type = kind.toAnimeVideoType(),
                hosting = null
        )
    }

    private fun VideoKindEnum.toAnimeVideoType(): AnimeVideoType {
        return when (this) {
            VideoKindEnum.op -> AnimeVideoType.OPENING
            VideoKindEnum.ed -> AnimeVideoType.ENDING
            VideoKindEnum.pv -> AnimeVideoType.PROMO
            else -> AnimeVideoType.OTHER
        }
    }

    private fun AnimeByIdQuery.Studio.toDomainStudio(): Studio {
        return Studio(
                id = id.toLongOrNull() ?: 0L,
                name = name,
                nameFiltered = "",
                isReal = true,
                imageUrl = imageUrl?.appendHostIfNeed()
        )
    }

    private fun AnimeByIdQuery.Screenshot.toDomainScreenshot(): Screenshot {
        return Screenshot(
                original = originalUrl.appendHostIfNeed(),
                preview = x166Url.appendHostIfNeed()
        )
    }

    private fun AnimeByIdQuery.ExternalLink.toDomainLink(): Link {
        return Link(
                id = id?.toString()?.toLongOrNull() ?: 0L,
                name = kind.rawValue,
                url = url.appendHostIfNeed()
        )
    }

    private fun AnimeStaffQuery.CharacterRole.toDomainCharacter(): Character {
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

    private fun AnimeStaffQuery.PersonRole.toDomainPersonWithRoles(): Pair<Person, List<String>> {
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

    /**
     * Helper to return null if a list is empty (to match null expectations in domain models).
     */
    private fun <E> List<E>?.nullIfEmpty(): List<E>? {
        return if (this.isNullOrEmpty()) null else this
    }
}
