package com.osm.wear.repositories

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegionRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val prefs: SharedPreferences
) : IRegionRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mapsDir: File get() = File(context.filesDir, "maps").also { it.mkdirs() }

    private val _activeRegionId = MutableStateFlow<String?>(prefs.getString("active_region_id", null))
    override val activeRegionId: StateFlow<String?> = _activeRegionId.asStateFlow()

    override val activeMapFile: StateFlow<File?> = _activeRegionId
        .map { id ->
            if (id == null) null
            else {
                val file = File(mapsDir, id.replace("/", "_") + ".map")
                if (file.exists() && file.length() > 0) file else null
            }
        }
        .stateIn(repositoryScope, SharingStarted.Eagerly, null)

    override fun getActiveMapFile(): File? {
        val id = getActiveRegionId() ?: return null
        val file = File(mapsDir, id.replace("/", "_") + ".map")
        return if (file.exists() && file.length() > 0) file else null
    }

    override fun getActiveRegionId(): String? = _activeRegionId.value

    override fun setActiveRegionId(id: String?) {
        _activeRegionId.value = id
        if (id == null) {
            prefs.edit().remove("active_region_id").apply()
        } else {
            prefs.edit().putString("active_region_id", id).apply()
        }
    }
}
