package com.gnoemes.shikimori.di.app.module.network

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.http.DefaultHttpEngine
import com.gnoemes.shikimori.BuildConfig
import com.gnoemes.shikimori.di.app.annotations.AuthCommonApi
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
object GraphQLModule {

    @Provides
    @Singleton
    fun provideApolloClient(@AuthCommonApi okHttpClient: OkHttpClient): ApolloClient {
        return ApolloClient.Builder()
            .serverUrl(BuildConfig.ShikimoriBaseUrl + "api/graphql")
            .httpEngine(DefaultHttpEngine { okHttpClient })
            .build()
    }
}