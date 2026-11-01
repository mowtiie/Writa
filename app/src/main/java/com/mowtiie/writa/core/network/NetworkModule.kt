package com.mowtiie.writa.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlainClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthedClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json = apiJson

    @Provides
    @Singleton
    @PlainClient
    fun providePlainClient(): OkHttpClient = HttpClients.plain()

    @Provides
    @Singleton
    @AuthedClient
    fun provideAuthedClient(
        @PlainClient plain: OkHttpClient,
        authenticator: TokenAuthenticator,
    ): OkHttpClient = HttpClients.authed(plain, authenticator)
}
