package com.gnoemes.shikimori.data.repository.user

import com.apollographql.apollo.ApolloClient
import com.gnoemes.shikimori.data.graphql.CurrentUserQuery
import com.gnoemes.shikimori.data.graphql.rxQuery
import com.gnoemes.shikimori.data.local.preference.UserSource
import com.gnoemes.shikimori.data.network.UserApi
import com.gnoemes.shikimori.data.repository.club.ClubResponseConverter
import com.gnoemes.shikimori.data.repository.user.converter.*
import com.gnoemes.shikimori.entity.app.domain.Constants
import com.gnoemes.shikimori.entity.club.domain.Club
import com.gnoemes.shikimori.entity.user.domain.*
import com.gnoemes.shikimori.utils.appendHostIfNeed
import io.reactivex.Completable
import io.reactivex.Single
import org.joda.time.DateTime
import javax.inject.Inject

/**
 * Partial GraphQL-migrated [UserRepository].
 *
 * Uses [CurrentUserQuery] for fetching the current user profile (read-only) via
 * Apollo/GraphQL, while delegating all other endpoints to [UserApi] (REST):
 *   - auth-related calls
 *   - messages
 *   - history
 *   - friends
 *   - favourites
 *
 * Per migration plan: only read endpoints migrate; mutations stay on REST.
 */
class GraphQLUserRepositoryImpl @Inject constructor(
    private val apolloClient: ApolloClient,
    private val api: UserApi,
    private val userSource: UserSource,
    private val converter: UserBriefResponseConverter,
    private val detailsConverter: UserDetailsResponseConverter,
    private val historyConverter: UserHistoryConverter,
    private val favoriteConverter: FavoriteListResponseConverter,
    private val clubConverter: ClubResponseConverter,
    private val messageConverter: MessageResponseConverter
) : UserRepository {

    //region GraphQL — currentUser

    override fun getMyUserId(): Single<Long> =
        Single.just(userSource.getUserId())
            .flatMap {
                if (it == Constants.NO_ID) getMyUserBrief().map { it.id }
                else Single.just(it)
            }

    override fun getMyUserBrief(): Single<UserBrief> =
        Single.fromCallable { userSource.getUserStatus() }
            .flatMap { status ->
                if (status != UserStatus.AUTHORIZED) {
                    fetchCurrentUser()
                } else {
                    userSource.getUser()?.let { Single.just(it) } ?: fetchCurrentUser()
                }
            }

    private fun fetchCurrentUser(): Single<UserBrief> =
        apolloClient.rxQuery(CurrentUserQuery())
            .map { data -> convertCurrentUser(data.currentUser) }
            .doOnSuccess { userSource.setUser(it) }
            .doOnSuccess { userSource.setUserId(it.id) }

    private fun convertCurrentUser(currentUser: CurrentUserQuery.CurrentUser?): UserBrief {
        if (currentUser == null) {
            throw IllegalStateException("CurrentUserQuery returned null currentUser")
        }

        val avatarUrl = currentUser.avatarUrl.appendHostIfNeed()

        return UserBrief(
            id = currentUser.id.toLong(),
            nickname = currentUser.nickname,
            avatar = avatarUrl,
            image = UserImage(
                x160 = avatarUrl,
                x148 = avatarUrl,
                x80 = avatarUrl,
                x64 = avatarUrl,
                x48 = avatarUrl,
                x32 = avatarUrl,
                x16 = avatarUrl
            ),
            dateLastOnline = parseIso8601(currentUser.lastOnlineAt),
            name = null,
            sex = null,
            website = null,
            dateBirth = null,
            locale = null
        )
    }

    /**
     * Safely parse an [Any] value (expected ISO 8601 string from GraphQL
     * `ISO8601DateTime` scalar) into a Joda-Time [DateTime].
     *
     * Returns [DateTime(0)] (epoch) if parsing fails.
     */
    private fun parseIso8601(value: Any?): DateTime {
        if (value == null) return DateTime(0)
        return when (value) {
            is String -> try {
                DateTime.parse(value)
            } catch (e: Exception) {
                DateTime(0)
            }
            is Number -> DateTime(value.toLong() * 1000L)
            else -> DateTime(0)
        }
    }

    //endregion

    //region REST — messages

    override fun getUserMessages(type: MessageType): Single<List<Message>> =
        getMyUserBrief()
            .flatMap { api.getUserMessages(it.id, type) }
            .map(messageConverter)

    //endregion

    //region REST — profile details

    override fun getDetails(id: Long): Single<UserDetails> =
        api.getUserProfile(id)
            .map(detailsConverter::convertResponse)

    //endregion

    //region REST — friends

    override fun getFriends(id: Long): Single<List<UserBrief>> =
        api.getUserFriends(id)
            .map(converter)

    //endregion

    //region REST — favourites

    override fun getFavorites(id: Long): Single<FavoriteList> =
        api.getUserFavourites(id)
            .map(favoriteConverter)

    //endregion

    //region REST — clubs

    override fun getClubs(id: Long): Single<List<Club>> =
        api.getUserClubs(id)
            .map(clubConverter)

    //endregion

    //region REST — history

    override fun getHistory(id: Long, page: Int, limit: Int): Single<List<UserHistory>> =
        api.getUserHistory(id, page, limit)
            .map(historyConverter)
            // server returns N+1 elements, if next page exists
            .map { if (it.isNotEmpty()) it.take(limit) else it }

    //endregion

    //region REST — social actions

    override fun ignore(id: Long): Completable = api.ignoreUser(id)

    override fun unignore(id: Long): Completable = api.unignoreUser(id)

    override fun addToFriends(id: Long): Completable = api.addToFriends(id)

    override fun removeFriend(id: Long): Completable = api.deleteFriend(id)

    //endregion

    //region Local — user state

    override fun getUserStatus(): UserStatus = userSource.getUserStatus()

    override fun setUserStatus(status: UserStatus) = userSource.setUserStatus(status)

    override fun clearUser() = userSource.clearUser()

    //endregion
}
