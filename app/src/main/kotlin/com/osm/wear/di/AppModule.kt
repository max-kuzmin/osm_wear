package com.osm.wear.di

import android.content.Context
import android.content.SharedPreferences
import com.osm.wear.data_sources.*
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
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("osm_wear_prefs", Context.MODE_PRIVATE)
    }

    // --- Data Sources ---

    @Provides
    @Singleton
    fun provideLocalPreferencesDataSource(prefs: SharedPreferences): ILocalPreferencesDataSource {
        return LocalPreferencesDataSource(prefs)
    }

    @Provides
    @Singleton
    fun provideLocalFileDataSource(@ApplicationContext context: Context): ILocalFileDataSource {
        return LocalFileDataSource(context)
    }

    @Provides
    @Singleton
    fun provideRemoteGeocodingDataSource(client: OkHttpClient): IRemoteGeocodingDataSource {
        return RemoteGeocodingDataSource(client)
    }

    @Provides
    @Singleton
    fun provideRemoteRegionDataSource(client: OkHttpClient): IRemoteRegionDataSource {
        return RemoteRegionDataSource(client)
    }

    @Provides
    @Singleton
    fun provideDeviceLocationDataSource(@ApplicationContext context: Context): IDeviceLocationDataSource {
        return DeviceLocationDataSource(context)
    }

    @Provides
    @Singleton
    fun provideDeviceAlertsDataSource(@ApplicationContext context: Context): IDeviceAlertsDataSource {
        return DeviceAlertsDataSource(context)
    }

    // --- Repositories ---

    @Provides
    @Singleton
    fun provideCursorRepository(
        locationDataSource: IDeviceLocationDataSource
    ): ICursorRepository {
        return CursorRepository(locationDataSource)
    }

    @Provides
    @Singleton
    fun providePreferencesRepository(
        prefs: ILocalPreferencesDataSource
    ): IPreferencesRepository {
        return PreferencesRepository(prefs)
    }

    @Provides
    @Singleton
    fun provideGpxRepository(
        localFileDataSource: ILocalFileDataSource,
        prefs: ILocalPreferencesDataSource
    ): IGpxRepository {
        return GpxRepository(localFileDataSource, prefs)
    }

    @Provides
    @Singleton
    fun provideRegionRepository(
        localFileDataSource: ILocalFileDataSource,
        prefs: ILocalPreferencesDataSource,
        remoteRegionDataSource: IRemoteRegionDataSource
    ): IRegionRepository {
        return RegionRepository(localFileDataSource, prefs, remoteRegionDataSource)
    }

    @Provides
    @Singleton
    fun provideMarkersRepository(
        prefs: ILocalPreferencesDataSource
    ): IMarkersRepository {
        return MarkersRepository(prefs)
    }

    @Provides
    @Singleton
    fun provideGeocodingRepository(
        remoteGeocodingDataSource: IRemoteGeocodingDataSource
    ): IGeocodingRepository {
        return GeocodingRepository(remoteGeocodingDataSource)
    }

    @Provides
    @Singleton
    fun provideAlertsRepository(
        deviceAlertsDataSource: IDeviceAlertsDataSource,
        prefs: ILocalPreferencesDataSource
    ): IAlertsRepository {
        return AlertsRepository(deviceAlertsDataSource, prefs)
    }

    @Provides
    @Singleton
    fun provideBillingRepository(
        @ApplicationContext context: Context
    ): IBillingRepository {
        val repo = BillingRepository(context)
        repo.connect()
        return repo
    }

    @Provides
    @Singleton
    fun provideRegionValidatorService(
        @ApplicationContext context: Context,
        prefs: ILocalPreferencesDataSource
    ): IRegionValidatorService {
        return RegionValidatorService(context, prefs)
    }

    // --- Services ---

    @Provides
    @Singleton
    fun provideNavigationTrackingService(
        @ApplicationContext context: Context,
        buildInitialNavigationStateUseCase: BuildInitialNavigationStateUseCase,
        updateNavigationStateUseCase: UpdateNavigationStateUseCase,
        alertsRepository: IAlertsRepository,
        cursorRepository: ICursorRepository
    ): INavigationTrackingService {
        return NavigationTrackingService(
            context,
            buildInitialNavigationStateUseCase,
            updateNavigationStateUseCase,
            alertsRepository,
            cursorRepository
        )
    }

    @Provides
    @Singleton
    fun provideUiNavigationManager(): IUiRouter {
        return UiRouter()
    }
}
