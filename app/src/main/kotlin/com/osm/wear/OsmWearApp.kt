package com.osm.wear

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

@HiltAndroidApp
class OsmWearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Mapsforge AndroidGraphicFactory once for the whole app lifecycle
        AndroidGraphicFactory.createInstance(this)
    }
}
