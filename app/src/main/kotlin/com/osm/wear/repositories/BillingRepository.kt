package com.osm.wear.repositories

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.osm.wear.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingRepository(private val context: Context) : IBillingRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _purchasedProductIds = MutableStateFlow<Set<String>>(emptySet())
    override val purchasedProductIds: StateFlow<Set<String>> = _purchasedProductIds.asStateFlow()

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    override val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d("BillingRepository", "User canceled purchase")
        } else {
            Log.e("BillingRepository", "Purchase error: \${billingResult.debugMessage}")
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    override fun connect() {
        if (!AppConfig.IS_MONETIZATION_ENABLED) return

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
            }
        })
    }

    private fun queryPurchases() {
        if (!billingClient.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val purchasedIds = mutableSetOf<String>()
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        purchasedIds.addAll(purchase.products)
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase.purchaseToken)
                        }
                    }
                }
                _purchasedProductIds.value = purchasedIds
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val currentPurchased = _purchasedProductIds.value.toMutableSet()
            currentPurchased.addAll(purchase.products)
            _purchasedProductIds.value = currentPurchased

            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase.purchaseToken)
            }
        }
    }

    private fun acknowledgePurchase(purchaseToken: String) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d("BillingRepository", "Purchase acknowledged successfully")
            }
        }
    }

    override fun queryProductDetails(productIds: List<String>) {
        if (!billingClient.isReady || !AppConfig.IS_MONETIZATION_ENABLED || productIds.isEmpty()) return

        val productList = productIds.map { productId ->
            val formattedId = productId.replace("/", "_")
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(formattedId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsListResult ->
            val productDetailsList = productDetailsListResult.productDetailsList;
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val newDetails = productDetailsList.associateBy { it.productId }
                // Merge with existing details so we don't lose previously fetched ones
                _productDetails.value = _productDetails.value + newDetails
            }
        }
    }

    override fun launchBillingFlow(activity: Activity, productId: String) {
        if (!billingClient.isReady) return

        val formattedId = productId.replace("/", "_")
        val details = _productDetails.value[formattedId]
        
        if (details != null) {
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build()
            )

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()

            billingClient.launchBillingFlow(activity, billingFlowParams)
        } else {
            // Product details not yet fetched, fetch and then launch
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(formattedId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsListResult ->
                val productDetailsList = productDetailsListResult.productDetailsList;
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                    val pDetails = productDetailsList.first()
                    _productDetails.value = _productDetails.value + mapOf(formattedId to pDetails)
                    
                    val productDetailsParamsList = listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(pDetails)
                            .build()
                    )

                    val billingFlowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(productDetailsParamsList)
                        .build()

                    billingClient.launchBillingFlow(activity, billingFlowParams)
                }
            }
        }
    }
}
