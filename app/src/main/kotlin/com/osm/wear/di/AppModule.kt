package com.osm.wear.di

import android.content.Context
import com.osm.wear.repositories.*
import com.osm.wear.services.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    @Provides
    @Singleton
    fun provideCursorRepository(@ApplicationContext context: Context): ICursorRepository {
        return CursorRepository(context)
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): android.content.SharedPreferences {
        return context.getSharedPreferences("osm_wear_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun providePreferencesRepository(prefs: android.content.SharedPreferences): IPreferencesRepository {
        return PreferencesRepository(prefs)
    }

    @Provides
    @Singleton
    fun provideGpxRepository(
        @ApplicationContext context: Context,
        prefs: android.content.SharedPreferences
    ): IGpxRepository {
        return GpxRepository(context, prefs)
    }

    @Provides
    @Singleton
    fun provideRegionRepository(
        @ApplicationContext context: Context,
        prefs: android.content.SharedPreferences,
        client: OkHttpClient
    ): IRegionRepository {
        return RegionRepository(context, prefs, client)
    }

    @Provides
    @Singleton
    fun provideTrackToMapMatcherService(
        @ApplicationContext context: Context,
        regionRepository: IRegionRepository
    ): ITrackToMapMatcherService {
        return TrackToMapMatcherService(context, regionRepository)
    }

    @Provides
    @Singleton
    fun provideNavigationService(
        @ApplicationContext context: Context,
        trackToMapMatcher: ITrackToMapMatcherService
    ): INavigationService {
        return NavigationService(context, trackToMapMatcher)
    }

    @Provides
    @Singleton
    fun provideMarkersRepository(
        prefs: android.content.SharedPreferences
    ): IMarkersRepository {
        return MarkersRepository(prefs)
    }

    @Provides
    @Singleton
    fun provideGeocodingRepository(client: OkHttpClient): IGeocodingRepository {
        return GeocodingRepository(client)
    }

    @Provides
    @Singleton
    fun provideNavigationTrackingService(
        navigationService: INavigationService,
        preferencesRepository: IPreferencesRepository,
        regionRepository: IRegionRepository,
        checkGpxCoverageUseCase: CheckGpxCoverageUseCase,
        cursorRepository: ICursorRepository,
        geocodingRepository: IGeocodingRepository
    ): INavigationTrackingService {
        return NavigationTrackingService(
            navigationService,
            preferencesRepository,
            regionRepository,
            checkGpxCoverageUseCase,
            cursorRepository,
            geocodingRepository
        )
    }
}
