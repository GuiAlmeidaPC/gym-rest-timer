package com.gymresttimer.di

import android.content.Context
import com.gymresttimer.data.AppDatabase
import com.gymresttimer.data.RestProfileDao
import com.gymresttimer.data.RestProfileRepository
import com.gymresttimer.data.RoomRestProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        @AppScope scope: CoroutineScope,
    ): AppDatabase = AppDatabase.build(context, scope)

    @Provides
    fun provideRestProfileDao(db: AppDatabase): RestProfileDao = db.restProfileDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindRestProfileRepository(impl: RoomRestProfileRepository): RestProfileRepository
}
