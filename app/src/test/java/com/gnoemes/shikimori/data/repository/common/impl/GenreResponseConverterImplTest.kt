package com.gnoemes.shikimori.data.repository.common.impl

import com.gnoemes.shikimori.entity.common.data.GenreResponse
import com.gnoemes.shikimori.entity.common.domain.Genre
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("GenreResponseConverterImpl")
class GenreResponseConverterImplTest {

    private lateinit var converter: GenreResponseConverterImpl

    @BeforeEach
    fun setup() {
        converter = GenreResponseConverterImpl()
    }

    @Nested
    @DisplayName("apply")
    inner class Apply {

        @Test
        fun `should convert known genre responses to Genre enum values`() {
            val responses = listOf(
                    GenreResponse(id = 1, name = "Action", nameRu = "Экшен", type = "genre"),
                    GenreResponse(id = 2, name = "Comedy", nameRu = "Комедия", type = "genre")
            )

            val result = converter.apply(responses)

            assertEquals(2, result.size)
            assertTrue(result.contains(Genre.ACTION))
            assertTrue(result.contains(Genre.COMEDY))
        }

        @Test
        fun `should handle hyphenated genre names`() {
            val responses = listOf(
                    GenreResponse(id = 1, name = "slice_of_life", nameRu = "Повседневность", type = "genre"),
                    GenreResponse(id = 2, name = "Sci-Fi", nameRu = "Фантастика", type = "genre")
            )

            val result = converter.apply(responses)

            assertEquals(2, result.size)
            assertTrue(result.contains(Genre.SLICE_OF_LIFE))
            assertTrue(result.contains(Genre.SCI_FI))
        }

        @Test
        fun `should handle genre names with spaces`() {
            val responses = listOf(
                    GenreResponse(id = 1, name = "Super Power", nameRu = "Супер сила", type = "genre"),
                    GenreResponse(id = 2, name = "Martial Arts", nameRu = "Боевые искусства", type = "genre")
            )

            val result = converter.apply(responses)

            assertEquals(2, result.size)
            assertTrue(result.contains(Genre.SUPER_POWER))
            assertTrue(result.contains(Genre.MARTIAL_ARTS))
        }

        @Test
        fun `should skip unknown genre names not in the Genre enum`() {
            val responses = listOf(
                    GenreResponse(id = 1, name = "Action", nameRu = "Экшен", type = "genre"),
                    GenreResponse(id = 999, name = "TotallyNewGenre", nameRu = "Новый жанр", type = "genre")
            )

            val result = converter.apply(responses)

            assertEquals(1, result.size)
            assertEquals(Genre.ACTION, result[0])
        }

        @Test
        fun `should return empty list for empty input`() {
            val result = converter.apply(emptyList())

            assertTrue(result.isEmpty())
        }

        @Test
        fun `should be case-insensitive when matching genre names`() {
            val responses = listOf(
                    GenreResponse(id = 1, name = "SHOUNEN", nameRu = "Сёнен", type = "genre"),
                    GenreResponse(id = 2, name = "horror", nameRu = "Ужасы", type = "genre")
            )

            val result = converter.apply(responses)

            assertEquals(2, result.size)
            assertTrue(result.contains(Genre.SHOUNEN))
            assertTrue(result.contains(Genre.HORROR))
        }

        @Test
        fun `should handle names that contain underscores`() {
            val responses = listOf(
                    GenreResponse(id = 1, name = "slice_of_life", nameRu = "Повседневность", type = "genre"),
                    GenreResponse(id = 2, name = "shounen_ai", nameRu = "Сёнен Ай", type = "genre")
            )

            val result = converter.apply(responses)

            assertEquals(2, result.size)
            assertTrue(result.contains(Genre.SLICE_OF_LIFE))
            assertTrue(result.contains(Genre.SHOUNEN_AI))
        }

        @Test
        fun `should convert mixed content preserving known genres`() {
            val responses = listOf(
                    GenreResponse(id = 1, name = "Action", nameRu = "Экшен", type = "genre"),
                    GenreResponse(id = 999, name = "Isekai", nameRu = "Исекай", type = "genre"),
                    GenreResponse(id = 3, name = "Dementia", nameRu = "Безумие", type = "genre")
            )

            val result = converter.apply(responses)

            assertEquals(2, result.size)
            assertTrue(result.contains(Genre.ACTION))
            assertTrue(result.contains(Genre.DEMENTIA))
        }
    }
}
