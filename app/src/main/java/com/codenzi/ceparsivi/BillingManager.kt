package com.codenzi.ceparsivi

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingManager(
    private val context: Context,
    private val onPurchaseResult: (String) -> Unit
) : PurchasesUpdatedListener {

    private val _isPremium = MutableStateFlow(false)
    val isPremium = _isPremium.asStateFlow()

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails = _productDetails.asStateFlow()

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    companion object {
        private const val TAG = "BillingManager"
        const val YEARLY_SKU = "yearly_premium_subscription"
        const val MONTHLY_SKU = "monthly_premium_subscription"
        val ALL_SKUS = listOf(YEARLY_SKU, MONTHLY_SKU)
    }

    init {
        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing Client bağlandı.")
                    queryProductDetails()
                    queryPurchases()
                }
            }
            override fun onBillingServiceDisconnected() { startConnection() }
        })
    }

    fun queryProductDetails() {
        val productList = ALL_SKUS.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList)

        billingClient.queryProductDetailsAsync(params.build()) { _, productDetailsList ->
            val newMap = productDetailsList.associateBy { it.productId }
            _productDetails.value = newMap
            Log.d(TAG, "Ürün detayları yüklendi: ${newMap.keys}")
        }
    }

    fun queryPurchases() {
        if (!billingClient.isReady) return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        billingClient.queryPurchasesAsync(params) { _, purchases ->
            val hasPremium = purchases.any { it.products.intersect(ALL_SKUS).isNotEmpty() && it.isAcknowledged }
            if (_isPremium.value != hasPremium) {
                _isPremium.value = hasPremium
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String) {
        val productDetails = _productDetails.value[productId]
        if (productDetails == null) {
            onPurchaseResult(context.getString(R.string.product_not_found))
            return
        }

        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""
        val productDetailsParamsList = listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productDetails).setOfferToken(offerToken).build())
        val billingFlowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(productDetailsParamsList).build()
        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handlePurchase(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> onPurchaseResult(context.getString(R.string.purchase_cancelled))
            else -> onPurchaseResult(context.getString(R.string.purchase_failed))
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            billingClient.acknowledgePurchase(ackParams) { ackResult ->
                if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isPremium.value = true
                    onPurchaseResult(context.getString(R.string.purchase_success))
                }
            }
        }
    }
}