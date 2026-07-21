package com.gnoemes.shikimori.di.search;

import com.gnoemes.shikimori.data.repository.search.GraphQLSearchRepositoryImpl;
import com.gnoemes.shikimori.data.repository.search.SearchRepository;

import dagger.Binds;
import dagger.Module;

@Module
public interface SearchRepositoryModule {
    @Binds
    SearchRepository bindSearchRepository(GraphQLSearchRepositoryImpl searchRepository);

}
