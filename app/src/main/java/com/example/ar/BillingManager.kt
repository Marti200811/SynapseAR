package com.example.ar

import android.app.Activity
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Maneja la suscripción anual Pro con 7 días de prueba gratuita.
 *
 * En Play Console crear:
 *   Tipo: Suscripción
 *   ID del producto: synapse_ar_pro
 *   Plan base ID: annual
 *   Precio: $9.99 / año
 *   Oferta: 7 días de prueba gratuita
 */
class BillingManager(
    private val activity: Activity,
    private val onProStatusChanged: (Boolean) -> Unit
) : PurchasesUpdatedListener {

    companion object {
        const val SKU_PRO = "synapse_ar_pro"
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    // ── Conexión ──────────────────────────────────────────────────────────────

    fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    restorePurchases()
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    // ── Verificar suscripción activa (restaurar si reinstala) ─────────────────

    fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)   // ← SUBS para suscripciones
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val isActive = purchases.any { purchase ->
                    purchase.products.contains(SKU_PRO) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                ProManager.setPro(activity, isActive)
                onProStatusChanged(isActive)
            }
        }
    }

    // ── Lanzar flujo de suscripción con trial ─────────────────────────────────

    fun launchPurchase() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_PRO)
                .setProductType(BillingClient.ProductType.SUBS)  // ← SUBS
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK &&
                productDetailsList.isNotEmpty()) {

                val productDetails = productDetailsList[0]

                // Buscar la oferta con trial gratuito primero, sino usar la primera disponible
                val offerToken = productDetails.subscriptionOfferDetails
                    ?.firstOrNull { offer ->
                        offer.pricingPhases.pricingPhaseList.any { phase ->
                            phase.priceAmountMicros == 0L  // precio $0 = período de prueba
                        }
                    }?.offerToken
                    ?: productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
                    ?: return@queryProductDetailsAsync

                val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()

                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productDetailsParams))
                    .build()

                activity.runOnUiThread {
                    billingClient.launchBillingFlow(activity, billingFlowParams)
                }
            }
        }
    }

    // ── Resultado de la suscripción ───────────────────────────────────────────

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { purchase ->
                if (purchase.products.contains(SKU_PRO) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    acknowledgePurchase(purchase)
                    ProManager.setPro(activity, true)
                    onProStatusChanged(true)
                }
            }
        }
    }

    // ── Confirmar suscripción (obligatorio para que no se revierta) ───────────

    private fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            CoroutineScope(Dispatchers.IO).launch {
                billingClient.acknowledgePurchase(params)
            }
        }
    }

    fun disconnect() {
        billingClient.endConnection()
    }
}
