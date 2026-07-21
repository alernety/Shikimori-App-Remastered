package com.gnoemes.shikimori.entity.common.domain

enum class Genre(
        val animeId: String,
        val mangaId: String,
        val russianName: String,
        val isR18: Boolean = false
) {
    SHOUNEN("shounen", "shounen", "Сёнен"),
    SHOUNEN_AI("shounen_ai", "shounen_ai", "Сёнен Ай"),
    SEINEN("seinen", "seinen", "Сейнен"),
    SHOUJO("shoujo", "shoujo", "Сёдзе"),
    SHOUJO_AI("shoujo_ai", "shoujo_ai", "Сёдзе Ай"),
    JOSEI("josei", "josei", "Дзёсей"),
    COMEDY("comedy", "comedy", "Комедия"),
    ROMANCE("romance", "romance", "Романтика"),
    SCHOOL("school", "school", "Школа"),

    ACTION("action", "action", "Экшен"),
    ADVENTURE("adventure", "adventure", "Приключения"),
    PARODY("parody", "parody", "Пародия"),
    DRAMA("drama", "drama", "Драма"),
    GAME("game", "game", "Игры"),
    FANTASY("fantasy", "fantasy", "Фэнтези"),
    MAGIC("magic", "magic", "Магия"),
    ECCHI("ecchi", "ecchi", "Этти", isR18 = true),
    DEMONS("demons", "demons", "Демоны"),
    SUPER_POWER("super_power", "super_power", "Супер сила"),
    HAREM("harem", "harem", "Гарем"),
    MECHA("mecha", "mecha", "Меха"),
    SPORTS("sports", "sports", "Спорт"),
    SPACE("space", "space", "Космос"),
    SCI_FI("sci_fi", "sci_fi", "Фантастика"),
    MARTIAL_ARTS("martial_arts", "martial_arts", "Боевые искусства"),
    DEMENTIA("dementia", "dementia", "Безумие"),
    HISTORICAL("historical", "historical", "Исторический"),
    HORROR("horror", "horror", "Ужасы"),
    MILITARY("military", "military", "Военное"),
    POLICE("police", "police", "Полиция"),
    MUSIC("music", "music", "Музыка"),
    SAMURAI("samurai", "samurai", "Самураи"),
    VAMPIRE("vampire", "vampire", "Вампиры"),
    SLICE_OF_LIFE("slice_of_life", "slice_of_life", "Повседневность"),
    PSYCHOLOGICAL("psychological", "psychological", "Психологическое"),
    SUPERNATURAL("supernatural", "supernatural", "Сверхъестественное"),
    THRILLER("thriller", "thriller", "Триллер"),
    KIDS("kids", "kids", "Детское"),
    CARS("cars", "cars", "Машины"),
    MYSTERY("mystery", "mystery", "Детектив"),
    DOUJINSHI("doujinshi", "doujinshi", "Додзинси"),
    GENDER_BENDER("gender_bender", "gender_bender", "Смена пола"),
    GOURMET("gourmet", "gourmet", "Гурман"),
    WORK_LIFE("work_life", "work_life", "Работа"),
    EROTICA("erotica", "erotica", "Эротика", isR18 = true),
    HENTAI("hentai", "hentai", "Хентай", isR18 = true),
    YAOI("yaoi", "yaoi", "Яой", isR18 = true),
    YURI("yuri", "yuri", "Юри", isR18 = true);

    fun equalsName(otherName: String): Boolean {
        return name.equals(otherName, ignoreCase = true)
    }

    fun hasContentId(anime: Boolean): Boolean = true
}
