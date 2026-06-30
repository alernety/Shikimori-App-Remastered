package com.gnoemes.shikimori.di.app.module.network

import com.gnoemes.shikimori.data.repository.common.GenreGraphQLSource
import com.gnoemes.shikimori.data.repository.common.impl.GenreGraphQLSourceImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable

/**
 * Dagger module that binds GraphQL data source implementations.
 *
 * Provides GraphQL-backed sources as injectable interfaces, keeping
 * the Apollo layer isolated behind clean abstractions.
 */
@Module
interface GraphQLSourceModule {

    @Binds
    @Reusable
    fun bindGenreGraphQLSource(impl: GenreGraphQLSourceImpl): GenreGraphQLSource
}
