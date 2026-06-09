package com.osm.wear.di

import android.content.Context
import com.osm.wear.data.gpx.GpxRepository
import com.osm.wear.data.location.LocationRepository
import com.osm.wear.data.map.MapDownloadManager
import com.osm.wear.data.navigation.NavigationEngine
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
    fun provideLocationRepository(@ApplicationContext context: Context): LocationRepository {
        return LocationRepository(context)
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): android.content.SharedPreferences {
        return context.getSharedPreferences("osm_wear_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideGpxRepository(
        @ApplicationContext context: Context,
        prefs: android.content.SharedPreferences
    ): GpxRepository {
        return GpxRepository(context, prefs)
    }

    @Provides
    @Singleton
    fun provideMapDownloadManager(@ApplicationContext context: Context): MapDownloadManager {
        return MapDownloadManager(context)
    }

    @Provides
    @Singleton
    fun provideNavigationEngine(@ApplicationContext context: Context): NavigationEngine {
        return NavigationEngine(context)
    }
}
