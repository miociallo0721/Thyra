package dev.thyra.android

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.thyra.core.data.DefaultThyraRepository
import dev.thyra.core.data.ThyraRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
  @Provides
  @Singleton
  fun provideRepository(@ApplicationContext context: Context): ThyraRepository = DefaultThyraRepository(context)
}
