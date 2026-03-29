package com.gnoemes.shikimori.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gnoemes.shikimori.data.local.db.dao.AnimeRateSyncRoomDao
import com.gnoemes.shikimori.data.local.db.dao.ChapterRoomDao
import com.gnoemes.shikimori.data.local.db.dao.EpisodeRoomDao
import com.gnoemes.shikimori.data.local.db.dao.MangaRateSyncRoomDao
import com.gnoemes.shikimori.data.local.db.dao.PinnedRateRoomDao
import com.gnoemes.shikimori.data.local.db.dao.TranslationSettingRoomDao
import com.gnoemes.shikimori.entity.chapters.ChapterDao
import com.gnoemes.shikimori.entity.rates.data.AnimeRateSyncDao
import com.gnoemes.shikimori.entity.rates.data.MangaRateSyncDao
import com.gnoemes.shikimori.entity.rates.data.PinnedRateDao
import com.gnoemes.shikimori.entity.series.data.EpisodeDao
import com.gnoemes.shikimori.entity.series.data.TranslationSettingDao

@Database(entities = [
    AnimeRateSyncDao::class,
    EpisodeDao::class,
    MangaRateSyncDao::class,
    TranslationSettingDao::class,
    ChapterDao::class,
    PinnedRateDao::class
], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun animeRateSyncDao(): AnimeRateSyncRoomDao
    abstract fun episodeDao(): EpisodeRoomDao
    abstract fun mangaRateSyncDao(): MangaRateSyncRoomDao
    abstract fun translationSettingDao(): TranslationSettingRoomDao
    abstract fun chapterDao(): ChapterRoomDao
    abstract fun pinnedRateDao(): PinnedRateRoomDao
}
