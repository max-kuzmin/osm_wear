package com.osm.wear

import android.app.Application
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class OsmWearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Mapsforge AndroidGraphicFactory once for the whole app lifecycle
        AndroidGraphicFactory.createInstance(this)
    }
}
