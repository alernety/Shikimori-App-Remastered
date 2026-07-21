package com.gnoemes.shikimori.di.anime;

import com.gnoemes.shikimori.data.repository.anime.AnimeRepository;
import com.gnoemes.shikimori.data.repository.anime.GraphQLAnimeRepositoryImpl;

import dagger.Binds;
import dagger.Module;

@Module
public interface AnimeRepositoryModule {

    @Binds
    AnimeRepository bindAnimeRepository(GraphQLAnimeRepositoryImpl animeRepository);
}
