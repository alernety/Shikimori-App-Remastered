package com.gnoemes.shikimori.data.repository.calendar

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.gnoemes.shikimori.data.graphql.UserRatesQuery
import com.gnoemes.shikimori.data.graphql.rxQuery
import com.gnoemes.shikimori.data.graphql.type.UserRateStatusEnum
import com.gnoemes.shikimori.data.graphql.type.UserRateTargetTypeEnum
import com.gnoemes.shikimori.data.network.CalendarApi
import com.gnoemes.shikimori.data.repository.calendar.converter.CalendarResponseConverter
import com.gnoemes.shikimori.entity.calendar.domain.CalendarItem
import com.gnoemes.shikimori.entity.common.domain.Type
import com.gnoemes.shikimori.entity.rates.domain.RateStatus
import com.gnoemes.shikimori.entity.rates.domain.UserRate
import io.reactivex.Single
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import javax.inject.Inject

/**
 * [CalendarRepository] implementation that uses Apollo GraphQL for user rate lookups
 * where possible, falling back to the Retrofit REST API for calendar data when no
 * GraphQL calendar query is available in the Shikimori GraphQL schema.
 *
 * Read endpoints migrated to GraphQL:
 * - [getCalendarRates] uses [UserRatesQuery] for anime user rates
 *
 * Endpoints still on REST:
 * - [getData] falls back to Retrofit because the GraphQL schema has no `calendar` query
 */
class GraphQLCalendarRepositoryImpl @Inject constructor(
    private val apolloClient: ApolloClient,
    private val calendarApi: CalendarApi,
    private val converter: CalendarResponseConverter
) : CalendarRepository {

    override fun getData(): Single<List<CalendarItem>> =
        calendarApi.getCalendar()
            .map(converter)

    override fun getCalendarRates(userId: Long): Single<List<UserRate>> =
        apolloClient.rxQuery(
            UserRatesQuery(
                page = Optional.Absent,
                limit = Optional.Absent,
                userId = Optional.present(userId.toString()),
                targetType = Optional.present(UserRateTargetTypeEnum.Anime),
                status = Optional.Absent,
                order = Optional.Absent,
                includeAnime = true,
                includeManga = false
            )
        ).map { response ->
            response.userRates.map { it.toDomainUserRate(userId) }
        }
}

// ──────────────────────────────────────────────────
//  Mapping: GraphQL generated types → domain types
// ──────────────────────────────────────────────────

/**
 * Convert a [UserRatesQuery.UserRate] (Apollo generated) to the domain [UserRate].
 *
 * Since the generated `anime`/`manga` fields are only present when the corresponding
 * `@include` directive is `true`, we derive [targetType] and [targetId] accordingly.
 */
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
        textHtml = null,
        dateCreated = createdAt.parseGraphQLDateTime(),
        dateUpdated = updatedAt.parseGraphQLDateTime()
    )
}

/**
 * Convert a [UserRateStatusEnum] (Apollo GraphQL) to the domain [RateStatus].
 */
private fun UserRateStatusEnum?.toDomainRateStatus(): RateStatus? = when (this) {
    UserRateStatusEnum.watching -> RateStatus.WATCHING
    UserRateStatusEnum.planned -> RateStatus.PLANNED
    UserRateStatusEnum.rewatching -> RateStatus.REWATCHING
    UserRateStatusEnum.completed -> RateStatus.COMPLETED
    UserRateStatusEnum.on_hold -> RateStatus.ON_HOLD
    UserRateStatusEnum.dropped -> RateStatus.DROPPED
    else -> null
}

/**
 * Parse a GraphQL DateTime (ISO 8601 string) to Joda [DateTime].
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
