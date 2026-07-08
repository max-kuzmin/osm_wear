package com.osm.wear.services

import android.content.Context
import com.osm.wear.AppConfig
import com.osm.wear.data_sources.ILocalPreferencesDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

class RegionValidatorService(
    private val context: Context,
    private val prefs: ILocalPreferencesDataSource
) : IRegionValidatorService {

    private val _freeRegionId = MutableStateFlow<String?>(prefs.getString("free_region_id", null))
    override val freeRegionId: StateFlow<String?> = _freeRegionId.asStateFlow()

    private val firstInstallTimeMs: Long by lazy {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.firstInstallTime
        } catch (e: Exception) {
            System.currentTimeMillis() // Fallback to now if error
        }
    }

    override fun isFreeTrialExpired(): Boolean {
        val now = System.currentTimeMillis()
        val trialDurationMs = TimeUnit.DAYS.toMillis(AppConfig.FIRST_REGION_FREE_DAYS.toLong())
        return (now - firstInstallTimeMs) > trialDurationMs
    }

    override fun isRegionValid(regionId: String, purchasedIds: Set<String>): Boolean {
        if (!AppConfig.IS_MONETIZATION_ENABLED) return true

        // Check if purchased
        val formattedId = regionId.replace("/", "_")
        if (purchasedIds.contains(formattedId) || purchasedIds.contains(regionId)) {
            return true
        }

        // Check free trial
        if (regionId == _freeRegionId.value && !isFreeTrialExpired()) {
            return true
        }

        return false
    }

    override fun claimFreeRegion(regionId: String) {
        if (_freeRegionId.value == null && !isFreeTrialExpired()) {
            _freeRegionId.value = regionId
            prefs.putString("free_region_id", regionId)
        }
    }
}
