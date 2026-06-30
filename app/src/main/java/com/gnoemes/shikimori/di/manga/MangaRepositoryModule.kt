package com.gnoemes.shikimori.di.manga

import com.gnoemes.shikimori.data.repository.manga.GraphQLMangaRepositoryImpl
import com.gnoemes.shikimori.data.repository.manga.MangaRepository
import com.gnoemes.shikimori.data.repository.ranobe.GraphQLRanobeRepositoryImpl
import com.gnoemes.shikimori.data.repository.ranobe.RanobeRepository
import dagger.Binds
import dagger.Module

@Module
interface MangaRepositoryModule {

    @Binds
    fun bindMangaRepository(repository : GraphQLMangaRepositoryImpl) : MangaRepository

    @Binds
    fun bindRanobeRepository(repository : GraphQLRanobeRepositoryImpl) : RanobeRepository

}