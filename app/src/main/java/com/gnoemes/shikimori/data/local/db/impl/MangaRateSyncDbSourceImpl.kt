package com.gnoemes.shikimori.data.local.db.impl

import com.gnoemes.shikimori.data.local.db.MangaRateSyncDbSource
import com.gnoemes.shikimori.data.local.db.dao.MangaRateSyncRoomDao
import com.gnoemes.shikimori.entity.rates.data.MangaRateSyncDao
import com.gnoemes.shikimori.entity.rates.domain.UserRate
import io.reactivex.Completable
import io.reactivex.Single
import javax.inject.Inject

class MangaRateSyncDbSourceImpl @Inject constructor(
        private val mangaRateSyncDao: MangaRateSyncRoomDao
) : MangaRateSyncDbSource {

    override fun getRate(rateId: Long): Single<UserRate> =
            mangaRateSyncDao.getRate(rateId)
                    .map { UserRate(it.rateId, targetId = it.mangaId, chapters = it.chapters) }
                    .toSingle()

    override fun saveRate(userRate: UserRate): Completable =
            mangaRateSyncDao.insert(MangaRateSyncDao(userRate.id!!, userRate.targetId!!, userRate.chapters!!))

    override fun getChaptersCount(mangaId: Long): Single<Int> =
            mangaRateSyncDao.getChaptersCount(mangaId)
                    .toSingle(0)

    override fun clearRate(mangaId: Long): Completable =
            mangaRateSyncDao.deleteByMangaId(mangaId)
}
