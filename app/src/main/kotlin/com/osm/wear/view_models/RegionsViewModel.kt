package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.DownloadState
import com.osm.wear.models.DownloadedRegion
import com.osm.wear.models.MapRegion
import com.osm.wear.repositories.IRegionRepository
import com.osm.wear.repositories.IBillingRepository
import com.osm.wear.services.IRegionValidatorService
import com.osm.wear.AppConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegionsViewModel @Inject constructor(
    private val regionRepository: IRegionRepository,
    private val regionValidatorService: IRegionValidatorService,
    private val billingRepository: IBillingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegionsUiState())
    val uiState: StateFlow<RegionsUiState> = _uiState.asStateFlow()

    private val _effect = Channel<RegionsEffect>(Channel.BUFFERED)
    val effect: Flow<RegionsEffect> = _effect.receiveAsFlow()

    private val _activeRegionId = MutableStateFlow<String?>(null)

    init {
        _activeRegionId.value = regionRepository.getActiveRegionId()
        
        // Group regions once
        val grouped = regionRepository.all.groupBy { it.continent }
        _uiState.update { it.copy(groupedRegions = grouped) }

        viewModelScope.launch {
            regionRepository.activeRegionId.collect { activeId ->
                _activeRegionId.value = activeId
                refreshDownloadedRegionsInternal()
            }
        }

        viewModelScope.launch {
            regionRepository.downloadState.collect { state ->
                _uiState.update { it.copy(downloadState = state) }
                if (state is DownloadState.Idle) {
                    refreshDownloadedRegionsInternal()
                }
            }
        }
        
        refreshDownloadedRegionsInternal()

        _uiState.update { it.copy(
            isMonetizationEnabled = AppConfig.IS_MONETIZATION_ENABLED,
            isFreeTrialExpired = regionValidatorService.isFreeTrialExpired()
        ) }

        viewModelScope.launch {
            regionValidatorService.freeRegionId.collect { id ->
                _uiState.update { it.copy(freeRegionId = id) }
                refreshValidity()
            }
        }

        viewModelScope.launch {
            billingRepository.purchasedProductIds.collect {
                refreshValidity()
            }
        }

        viewModelScope.launch {
            billingRepository.productDetails.collect { details ->
                val prices = details.mapValues { it.value.oneTimePurchaseOfferDetails?.formattedPrice ?: "" }
                _uiState.update { it.copy(productPrices = prices) }
            }
        }

        billingRepository.queryProductDetails(regionRepository.all.map { it.id })
    }

    fun onIntent(intent: RegionsIntent) {
        when (intent) {
            is RegionsIntent.RefreshDownloadedRegions -> refreshDownloadedRegionsInternal()
            is RegionsIntent.SetActiveRegion -> setActiveRegion(intent.region)
            is RegionsIntent.DeleteRegion -> deleteRegion(intent.region)
            is RegionsIntent.DownloadRegion -> downloadRegion(intent.region)
            is RegionsIntent.CancelDownload -> cancelDownload()
            is RegionsIntent.InitiatePurchase -> initiatePurchase(intent.region)
            is RegionsIntent.ClaimFreeRegion -> claimFreeRegion(intent.region)
        }
    }

    private fun refreshValidity() {
        val validIds = regionRepository.all
            .filter { regionValidatorService.isRegionValid(it.id, billingRepository.purchasedProductIds.value) }
            .map { it.id }
            .toSet()
        _uiState.update { it.copy(validRegionIds = validIds) }
    }

    private fun refreshDownloadedRegionsInternal() {
        val downloaded = regionRepository.getDownloadedRegions(
            activeId = _activeRegionId.value
        )
        _uiState.update { it.copy(downloadedRegions = downloaded) }
    }

    private fun setActiveRegion(region: MapRegion) {
        val file = regionRepository.getLocalFile(region)
        if (!file.exists()) return
        
        _activeRegionId.value = region.id
        regionRepository.setActiveRegionId(region.id)
        refreshDownloadedRegionsInternal()
    }

    private fun deleteRegion(region: MapRegion) {
        viewModelScope.launch {
            regionRepository.deleteRegion(region)
            if (_activeRegionId.value == region.id) {
                _activeRegionId.value = null
                regionRepository.setActiveRegionId(null)
            }
            refreshDownloadedRegionsInternal()
        }
    }

    private fun downloadRegion(region: MapRegion) {
        viewModelScope.launch {
            regionRepository.downloadRegion(region)
        }
    }

    private fun cancelDownload() {
        regionRepository.cancelDownload()
    }

    fun launchBillingFlow(activity: android.app.Activity, productId: String) {
        billingRepository.launchBillingFlow(activity, productId)
    }

    private fun initiatePurchase(region: MapRegion) {
        // Obsolete, but keeping for intent completeness if needed
    }

    private fun claimFreeRegion(region: MapRegion) {
        regionValidatorService.claimFreeRegion(region.id)
        downloadRegion(region)
    }
}

sealed class RegionsEffect {
    data class LaunchBillingFlow(val productId: String) : RegionsEffect()
}
