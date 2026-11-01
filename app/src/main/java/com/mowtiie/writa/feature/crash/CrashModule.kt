package com.mowtiie.writa.feature.crash

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object CrashModule {
    @Provides
    fun provideCrashReportStore(
        @ApplicationContext context: Context,
    ): CrashReportStore = CrashReportStore.forApp(context)
}
