package com.example.ar

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Maneja la compra del plan Pro a través de Google Play Billing.
 *
 * Uso:
 *   val billing = BillingManager(activity) { isPro -> viewModel.isPro.value = isPro }
 *   billing.connect()               // llamar en onCreate
 *   billing.launchPurchase()        // llamar desde el botón "Comprar Pro"
 */
class BillingManager(
    private val activity: Activity,
    private val onProStatusChanged: (Boolean) -> Unit
) : PurchasesUpdatedListener {

    companion object {
        // ⚠️ Este ID debe coincidir exactamente con el que crees en Play Console
        // Play Console → Tu app → Monetizar → Productos integrados en la app → Crear producto
        const val SKU_PRO = "synapse_ar_pro"
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    // ── Conexión con Play Store ───────────────────────────────────────────────

    fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    restorePurchases()   // verificar si ya compró antes
                }
            }
            override fun onBillingServiceDisconnected() {
                // Play Store se desconectó, reconectar en el próximo intento
            }
        })
    }

    // ── Verificar compras existentes (restaurar si reinstala la app) ─────────

    fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPro = purchases.any { purchase ->
                    purchase.products.contains(SKU_PRO) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                ProManager.setPro(activity, hasPro)
                onProStatusChanged(hasPro)
            }
        }
    }

    // ── Lanzar flujo de compra ────────────────────────────────────────────────

    fun launchPurchase() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_PRO)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK &&
                productDetailsList.isNotEmpty()) {

                val productDetails = productDetailsList[0]
                val productDetailsParamsList = listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build()

                activity.runOnUiThread {
                    billingClient.launchBillingFlow(activity, billingFlowParams)
                }
            }
        }
    }

    // ── Resultado de la compra ────────────────────────────────────────────────

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

    // ── Confirmar compra (obligatorio, si no Play Store la revierte a los 3 días) ──

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
