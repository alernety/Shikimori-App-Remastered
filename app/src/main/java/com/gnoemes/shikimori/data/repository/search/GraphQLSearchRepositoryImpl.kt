package com.gnoemes.shikimori.data.repository.search

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.gnoemes.shikimori.data.graphql.AnimeListQuery
import com.gnoemes.shikimori.data.graphql.CharacterListQuery
import com.gnoemes.shikimori.data.graphql.MangaListQuery
import com.gnoemes.shikimori.data.graphql.PersonListQuery
import com.gnoemes.shikimori.data.graphql.rxQuery
import com.gnoemes.shikimori.data.graphql.type.AnimeKindEnum
import com.gnoemes.shikimori.data.graphql.type.AnimeStatusEnum
import com.gnoemes.shikimori.data.graphql.type.MangaKindEnum
import com.gnoemes.shikimori.data.graphql.type.MangaStatusEnum
import com.gnoemes.shikimori.data.graphql.type.OrderEnum
import com.gnoemes.shikimori.entity.anime.domain.Anime
import com.gnoemes.shikimori.entity.anime.domain.AnimeType
import com.gnoemes.shikimori.entity.common.domain.Image
import com.gnoemes.shikimori.entity.common.domain.LinkedContent
import com.gnoemes.shikimori.entity.common.domain.Status
import com.gnoemes.shikimori.entity.common.domain.Type
import com.gnoemes.shikimori.entity.manga.domain.Manga
import com.gnoemes.shikimori.entity.manga.domain.MangaType
import com.gnoemes.shikimori.entity.roles.domain.Character
import com.gnoemes.shikimori.entity.roles.domain.Person
import com.gnoemes.shikimori.utils.appendHostIfNeed
import com.gnoemes.shikimori.utils.nullIfEmpty
import io.reactivex.Single
import org.joda.time.DateTime
import javax.inject.Inject

/**
 * [SearchRepository] implementation backed by GraphQL via [ApolloClient].
 *
 * Uses existing list queries (AnimeListQuery, MangaListQuery, CharacterListQuery, PersonListQuery)
 * which already support the `$search` parameter for full-text search.
 *
 * Handles all 6 methods of [SearchRepository]:
 * - getAnimeList / getMangaList / getRanobeList / getCharacterList / getPersonList
 * - getList (dispatches to the appropriate method based on [Type])
 */
class GraphQLSearchRepositoryImpl @Inject constructor(
    private val apolloClient: ApolloClient
) : SearchRepository {

    override fun getAnimeList(queryMap: Map<String, String>): Single<List<Anime>> =
        apolloClient.rxQuery(toAnimeListQuery(queryMap))
            .map { response -> response.animes.map { it.toDomainAnime() } }

    override fun getMangaList(queryMap: Map<String, String>): Single<List<Manga>> =
        apolloClient.rxQuery(toMangaListQuery(queryMap))
            .map { response -> response.mangas.map { it.toDomainManga(isRanobe = false) } }

    override fun getRanobeList(queryMap: Map<String, String>): Single<List<Manga>> =
        apolloClient.rxQuery(toMangaListQuery(queryMap))
            .map { response -> response.mangas.map { it.toDomainManga(isRanobe = true) } }

    override fun getCharacterList(queryMap: Map<String, String>): Single<List<Character>> =
        apolloClient.rxQuery(toCharacterListQuery(queryMap))
            .map { response -> response.characters.map { it.toDomainCharacter() } }

    override fun getPersonList(queryMap: Map<String, String>): Single<List<Person>> =
        apolloClient.rxQuery(toPersonListQuery(queryMap))
            .map { response -> response.people.map { it.toDomainPerson() } }

    override fun getList(type: Type, queryMap: Map<String, String>): Single<List<LinkedContent>> =
        (when (type) {
            Type.ANIME -> getAnimeList(queryMap)
            Type.MANGA -> getMangaList(queryMap)
            Type.RANOBE -> getRanobeList(queryMap)
            Type.CHARACTER -> getCharacterList(queryMap)
            Type.PERSON -> getCharacterList(queryMap) // matches existing SearchRepositoryImpl behavior
            else -> Single.error(IllegalArgumentException("$type search is not supported"))
        })
            .map { it }

    // ── Query builders ──────────────────────────────────────────────

    private fun toAnimeListQuery(filters: Map<String, String>): AnimeListQuery = AnimeListQuery(
        page = Optional.presentIfNotNull(filters["page"]?.toIntOrNull()),
        limit = Optional.presentIfNotNull(filters["limit"]?.toIntOrNull()),
        order = Optional.presentIfNotNull(filters["order"]?.let { OrderEnum.safeValueOf(it) }),
        kind = Optional.presentIfNotNull(filters["kind"]),
        status = Optional.presentIfNotNull(filters["status"]),
        season = Optional.presentIfNotNull(filters["season"]),
        score = Optional.presentIfNotNull(filters["score"]?.toIntOrNull()),
        duration = Optional.presentIfNotNull(filters["duration"]),
        rating = Optional.presentIfNotNull(filters["rating"]),
        origin = Optional.presentIfNotNull(filters["origin"]),
        genre = Optional.presentIfNotNull(filters["genre"]),
        studio = Optional.presentIfNotNull(filters["studio"]),
        franchise = Optional.presentIfNotNull(filters["franchise"]),
        censored = Optional.presentIfNotNull(filters["censored"]?.toBooleanStrictOrNull()),
        mylist = Optional.presentIfNotNull(filters["mylist"]),
        ids = Optional.presentIfNotNull(filters["ids"]),
        excludeIds = Optional.presentIfNotNull(filters["excludeIds"]),
        search = Optional.presentIfNotNull(filters["search"])
    )

    private fun toMangaListQuery(filters: Map<String, String>): MangaListQuery = MangaListQuery(
        page = Optional.presentIfNotNull(filters["page"]?.toIntOrNull()),
        limit = Optional.presentIfNotNull(filters["limit"]?.toIntOrNull()),
        order = Optional.presentIfNotNull(filters["order"]?.let { OrderEnum.safeValueOf(it) }),
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

    private fun toCharacterListQuery(filters: Map<String, String>): CharacterListQuery = CharacterListQuery(
        page = Optional.presentIfNotNull(filters["page"]?.toIntOrNull()),
        limit = Optional.presentIfNotNull(filters["limit"]?.toIntOrNull()),
        ids = Optional.presentIfNotNull(filters["ids"]),
        search = Optional.presentIfNotNull(filters["search"])
    )

    private fun toPersonListQuery(filters: Map<String, String>): PersonListQuery = PersonListQuery(
        page = Optional.presentIfNotNull(filters["page"]?.toIntOrNull()),
        limit = Optional.presentIfNotNull(filters["limit"]?.toIntOrNull()),
        ids = Optional.presentIfNotNull(filters["ids"]?.split(",")?.map { it.trim() }),
        search = Optional.presentIfNotNull(filters["search"]),
        isSeyu = Optional.presentIfNotNull(filters["isSeyu"]?.toBooleanStrictOrNull()),
        isProducer = Optional.presentIfNotNull(filters["isProducer"]?.toBooleanStrictOrNull()),
        isMangaka = Optional.presentIfNotNull(filters["isMangaka"]?.toBooleanStrictOrNull())
    )

    // ── Domain conversion: AnimeListQuery → domain Anime ──────────

    private fun AnimeListQuery.Anime.toDomainAnime(): Anime = Anime(
        id = id.toLongOrNull() ?: 0L,
        name = name.trim(),
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

    private fun AnimeListQuery.Poster?.toDomainImage(): Image = when (this) {
        null -> Image(null, null, null, null)
        else -> Image(
            original = mainUrl.appendHostIfNeed(),
            preview = previewUrl.appendHostIfNeed(),
            x96 = main2xUrl.appendHostIfNeed(),
            x48 = preview2xUrl.appendHostIfNeed()
        )
    }

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

    private fun AnimeListQuery.AiredOn?.toDomainDate(): DateTime? {
        if (this == null) return null
        val y = year ?: return null
        val m = month ?: 1
        val d = day ?: 1
        return try { DateTime(y, m, d, 0, 0) } catch (_: Exception) { null }
    }

    private fun AnimeListQuery.ReleasedOn?.toDomainDate(): DateTime? {
        if (this == null) return null
        val y = year ?: return null
        val m = month ?: 1
        val d = day ?: 1
        return try { DateTime(y, m, d, 0, 0) } catch (_: Exception) { null }
    }

    // ── Domain conversion: MangaListQuery → domain Manga ──────────

    private fun MangaListQuery.Manga.toDomainManga(isRanobe: Boolean): Manga = Manga(
        id = id.toLongOrNull() ?: 0L,
        name = name.trim(),
        nameRu = russian?.trim().nullIfEmpty(),
        image = poster.toDomainImage(),
        url = url.appendHostIfNeed(),
        type = kind.toDomainMangaType(),
        score = score,
        status = status.toDomainStatus(),
        volumes = volumes,
        chapters = chapters,
        dateAired = airedOn.toDomainDate(),
        dateReleased = releasedOn.toDomainDate(),
        isRanobe = isRanobe
    )

    private fun MangaListQuery.Poster?.toDomainImage(): Image = when (this) {
        null -> Image(null, null, null, null)
        else -> Image(
            original = mainUrl.appendHostIfNeed(),
            preview = previewUrl.appendHostIfNeed(),
            x96 = main2xUrl.appendHostIfNeed(),
            x48 = preview2xUrl.appendHostIfNeed()
        )
    }

    private fun MangaKindEnum?.toDomainMangaType(): MangaType = when (this) {
        MangaKindEnum.manga -> MangaType.MANGA
        MangaKindEnum.manhwa -> MangaType.MANHWA
        MangaKindEnum.manhua -> MangaType.MANHUA
        MangaKindEnum.novel -> MangaType.NOVEL
        // MangaType.LIGHT_NOVEL exists but MangaKindEnum has no light_novel value
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

    private fun MangaListQuery.AiredOn?.toDomainDate(): DateTime? {
        if (this == null) return null
        val y = year ?: return null
        val m = month ?: 1
        val d = day ?: 1
        return try { DateTime(y, m, d, 0, 0) } catch (_: Exception) { null }
    }

    private fun MangaListQuery.ReleasedOn?.toDomainDate(): DateTime? {
        if (this == null) return null
        val y = year ?: return null
        val m = month ?: 1
        val d = day ?: 1
        return try { DateTime(y, m, d, 0, 0) } catch (_: Exception) { null }
    }

    // ── Domain conversion: CharacterListQuery → domain Character ──

    private fun CharacterListQuery.Character.toDomainCharacter(): Character = Character(
        id = id.toLongOrNull() ?: 0L,
        name = name.trim(),
        nameRu = russian?.trim().nullIfEmpty(),
        image = poster.toDomainImage(),
        url = url.appendHostIfNeed()
    )

    private fun CharacterListQuery.Poster?.toDomainImage(): Image = when (this) {
        null -> Image(null, null, null, null)
        else -> Image(
            original = mainUrl.appendHostIfNeed(),
            preview = previewUrl.appendHostIfNeed(),
            x96 = null,
            x48 = preview2xUrl.appendHostIfNeed()
        )
    }

    // ── Domain conversion: PersonListQuery → domain Person ────────

    private fun PersonListQuery.Person.toDomainPerson(): Person = Person(
        id = id.toLongOrNull() ?: 0L,
        name = name.trim(),
        nameRu = russian?.trim().nullIfEmpty(),
        image = poster.toDomainImage(),
        url = url.appendHostIfNeed()
    )

    private fun PersonListQuery.Poster?.toDomainImage(): Image = when (this) {
        null -> Image(null, null, null, null)
        else -> Image(
            original = mainUrl.appendHostIfNeed(),
            preview = previewUrl.appendHostIfNeed(),
            x96 = null,
            x48 = preview2xUrl.appendHostIfNeed()
        )
    }
}
