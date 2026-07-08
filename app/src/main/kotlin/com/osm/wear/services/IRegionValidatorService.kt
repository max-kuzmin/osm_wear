package com.osm.wear.services

import kotlinx.coroutines.flow.StateFlow

interface IRegionValidatorService {
    val freeRegionId: StateFlow<String?>
    fun isRegionValid(regionId: String, purchasedIds: Set<String>): Boolean
    fun claimFreeRegion(regionId: String)
    fun isFreeTrialExpired(): Boolean
}
