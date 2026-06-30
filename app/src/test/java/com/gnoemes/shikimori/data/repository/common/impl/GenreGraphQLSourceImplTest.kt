package com.gnoemes.shikimori.data.repository.common.impl

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.gnoemes.shikimori.data.graphql.AnimeGenresQuery
import com.gnoemes.shikimori.data.graphql.MangaGenresQuery
import com.gnoemes.shikimori.data.graphql.type.GenreEntryTypeEnum
import com.gnoemes.shikimori.data.graphql.type.GenreKindEnum
import com.gnoemes.shikimori.entity.common.data.GenreResponse
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("GenreGraphQLSourceImpl")
class GenreGraphQLSourceImplTest {

    private val apolloClient: ApolloClient = mockk()
    private lateinit var source: GenreGraphQLSourceImpl

    @BeforeEach
    fun setup() {
        source = GenreGraphQLSourceImpl(apolloClient)
    }

    @Nested
    @DisplayName("getAnimeGenres")
    inner class GetAnimeGenres {
        private val operation: Operation<AnimeGenresQuery.Data> = AnimeGenresQuery()

        @Test
        fun `should fetch anime genres and map to GenreResponse`() {
            val genre1 = AnimeGenresQuery.Genre(
                    id = "1",
                    name = "Action",
                    russian = "Экшен",
                    kind = GenreKindEnum.genre,
                    entryType = GenreEntryTypeEnum.Anime
            )
            val genre2 = AnimeGenresQuery.Genre(
                    id = "2",
                    name = "Comedy",
                    russian = "Комедия",
                    kind = GenreKindEnum.genre,
                    entryType = GenreEntryTypeEnum.Anime
            )
            val data = AnimeGenresQuery.Data(genres = listOf(genre1, genre2))
            val response = ApolloResponse.Builder<AnimeGenresQuery.Data>(operation, UUID.randomUUID())
                    .data(data)
                    .build()

            coEvery {
                apolloClient.query(any<AnimeGenresQuery>()).execute()
            } returns response

            val result = source.getAnimeGenres().blockingGet()

            assertEquals(2, result.size)
            assertEquals(GenreResponse(id = 1L, name = "Action", nameRu = "Экшен", type = "genre"), result[0])
            assertEquals(GenreResponse(id = 2L, name = "Comedy", nameRu = "Комедия", type = "genre"), result[1])
        }

        @Test
        fun `should handle non-numeric string id gracefully`() {
            val genre = AnimeGenresQuery.Genre(
                    id = "not-a-number",
                    name = "Test",
                    russian = "Тест",
                    kind = GenreKindEnum.genre,
                    entryType = GenreEntryTypeEnum.Anime
            )
            val data = AnimeGenresQuery.Data(genres = listOf(genre))
            val response = ApolloResponse.Builder<AnimeGenresQuery.Data>(operation, UUID.randomUUID())
                    .data(data)
                    .build()

            coEvery {
                apolloClient.query(any<AnimeGenresQuery>()).execute()
            } returns response

            val result = source.getAnimeGenres().blockingGet()

            assertEquals(1, result.size)
            assertEquals(0L, result[0].id)
        }

        @Test
        fun `should handle theme kind enum conversion to type string`() {
            val genre = AnimeGenresQuery.Genre(
                    id = "5",
                    name = "Demons",
                    russian = "Демоны",
                    kind = GenreKindEnum.theme,
                    entryType = GenreEntryTypeEnum.Anime
            )
            val data = AnimeGenresQuery.Data(genres = listOf(genre))
            val response = ApolloResponse.Builder<AnimeGenresQuery.Data>(operation, UUID.randomUUID())
                    .data(data)
                    .build()

            coEvery {
                apolloClient.query(any<AnimeGenresQuery>()).execute()
            } returns response

            val result = source.getAnimeGenres().blockingGet()

            assertEquals(1, result.size)
            assertEquals("theme", result[0].type)
        }

        @Test
        fun `should convert genre kind to lowercase`() {
            val genre = AnimeGenresQuery.Genre(
                    id = "10",
                    name = "Fantasy",
                    russian = "Фэнтези",
                    kind = GenreKindEnum.genre,
                    entryType = GenreEntryTypeEnum.Anime
            )
            val data = AnimeGenresQuery.Data(genres = listOf(genre))
            val response = ApolloResponse.Builder<AnimeGenresQuery.Data>(operation, UUID.randomUUID())
                    .data(data)
                    .build()

            coEvery {
                apolloClient.query(any<AnimeGenresQuery>()).execute()
            } returns response

            val result = source.getAnimeGenres().blockingGet()

            assertEquals(1, result.size)
            assertEquals("genre", result[0].type)
        }
    }

    @Nested
    @DisplayName("getMangaGenres")
    inner class GetMangaGenres {
        private val operation: Operation<MangaGenresQuery.Data> = MangaGenresQuery()

        @Test
        fun `should fetch manga genres and map to GenreResponse`() {
            val genre = MangaGenresQuery.Genre(
                    id = "10",
                    name = "Fantasy",
                    russian = "Фэнтези",
                    kind = GenreKindEnum.genre,
                    entryType = GenreEntryTypeEnum.Manga
            )
            val data = MangaGenresQuery.Data(genres = listOf(genre))
            val response = ApolloResponse.Builder<MangaGenresQuery.Data>(operation, UUID.randomUUID())
                    .data(data)
                    .build()

            coEvery {
                apolloClient.query(any<MangaGenresQuery>()).execute()
            } returns response

            val result = source.getMangaGenres().blockingGet()

            assertEquals(1, result.size)
            assertEquals(GenreResponse(id = 10L, name = "Fantasy", nameRu = "Фэнтези", type = "genre"), result[0])
        }

        @Test
        fun `should map manga genre entry type`() {
            val genre = MangaGenresQuery.Genre(
                    id = "27",
                    name = "Shounen",
                    russian = "Сёнен",
                    kind = GenreKindEnum.genre,
                    entryType = GenreEntryTypeEnum.Manga
            )
            val data = MangaGenresQuery.Data(genres = listOf(genre))
            val response = ApolloResponse.Builder<MangaGenresQuery.Data>(operation, UUID.randomUUID())
                    .data(data)
                    .build()

            coEvery {
                apolloClient.query(any<MangaGenresQuery>()).execute()
            } returns response

            val result = source.getMangaGenres().blockingGet()

            assertEquals(1, result.size)
            assertEquals(27L, result[0].id)
            assertEquals("Shounen", result[0].name)
        }
    }
}
