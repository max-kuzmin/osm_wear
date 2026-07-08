package com.osm.wear.repositories

import android.app.Activity
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.flow.StateFlow

interface IBillingRepository {
    val purchasedProductIds: StateFlow<Set<String>>
    val productDetails: StateFlow<Map<String, ProductDetails>>

    fun connect()
    fun queryProductDetails(productIds: List<String>)
    fun launchBillingFlow(activity: Activity, productId: String)
}
