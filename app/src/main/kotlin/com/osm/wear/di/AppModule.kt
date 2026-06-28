package com.osm.wear.di

import android.content.Context
import com.osm.wear.repositories.GpxRepository
import com.osm.wear.repositories.CursorRepository
import com.osm.wear.repositories.MapDownloadRepository
import com.osm.wear.repositories.RouteRepositoryImpl
import com.osm.wear.repositories.SettingsRepositoryImpl
import com.osm.wear.repositories.IGpxRepository
import com.osm.wear.repositories.ICursorRepository
import com.osm.wear.repositories.IRouteRepository
import com.osm.wear.repositories.ISettingsRepository
import com.osm.wear.repositories.IMarkersRepository
import com.osm.wear.repositories.MarkersRepositoryImpl
import com.osm.wear.repositories.INavigationRepository
import com.osm.wear.repositories.NavigationRepositoryImpl
import com.osm.wear.repositories.IGeocodingRepository
import com.osm.wear.repositories.GeocodingRepositoryImpl
import com.osm.wear.services.INavigationService
import com.osm.wear.services.IMapRegionCatalogService
import com.osm.wear.services.IMapBoundariesService
import com.osm.wear.services.MapBoundariesService
import com.osm.wear.repositories.IMapFileRepository
import com.osm.wear.services.MapRegionCatalogService
import com.osm.wear.repositories.MapFileRepository
import com.osm.wear.services.NavigationService
import com.osm.wear.services.TrackToMapMatcherService
import com.osm.wear.services.ITrackToMapMatcherService
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
    fun provideSettingsRepository(prefs: android.content.SharedPreferences): ISettingsRepository {
        return SettingsRepositoryImpl(prefs)
    }

    @Provides
    @Singleton
    fun provideRouteRepository(client: OkHttpClient): IRouteRepository {
        return RouteRepositoryImpl(client)
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
    fun provideMapDownloadRepository(@ApplicationContext context: Context): MapDownloadRepository {
        return MapDownloadRepository(context)
    }

    @Provides
    @Singleton
    fun provideMapRegionCatalogService(): IMapRegionCatalogService {
        return MapRegionCatalogService()
    }

    @Provides
    @Singleton
    fun provideMapFileRepository(): IMapFileRepository {
        return MapFileRepository()
    }

    @Provides
    @Singleton
    fun provideTrackToMapMatcherService(
        @ApplicationContext context: Context,
        mapFileRepository: IMapFileRepository
    ): ITrackToMapMatcherService {
        return TrackToMapMatcherService(context, mapFileRepository)
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
    fun provideMapBoundariesService(
        mapFileRepository: IMapFileRepository
    ): IMapBoundariesService {
        return MapBoundariesService(mapFileRepository)
    }

    @Provides
    @Singleton
    fun provideMarkersRepository(
        prefs: android.content.SharedPreferences,
        settingsRepository: ISettingsRepository
    ): IMarkersRepository {
        return MarkersRepositoryImpl(prefs, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideNavigationRepository(
        navigationService: INavigationService,
        settingsRepository: ISettingsRepository,
        mapFileRepository: IMapFileRepository,
        mapBoundariesService: IMapBoundariesService,
        cursorRepository: ICursorRepository,
        routeRepository: IRouteRepository
    ): INavigationRepository {
        return NavigationRepositoryImpl(
            navigationService,
            settingsRepository,
            mapFileRepository,
            mapBoundariesService,
            cursorRepository,
            routeRepository
        )
    }

    @Provides
    @Singleton
    fun provideGeocodingRepository(client: OkHttpClient): IGeocodingRepository {
        return GeocodingRepositoryImpl(client)
    }
}
