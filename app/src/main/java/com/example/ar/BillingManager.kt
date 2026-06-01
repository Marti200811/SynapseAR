package com.example.ar

import android.app.Activity
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Maneja el pago único Pro.
 *
 * En Play Console crear:
 *   Tipo: Producto dentro de la app (pago único)
 *   ID del producto: synapse_ar_pro
 *   Precio base: $9.99
 *   (Promo de lanzamiento opcional vía "Oferta/Sale" en Play Console.
 *    El precio mostrado en la app es SIEMPRE el real de Google, así que
 *    una promo o la moneda local del usuario se reflejan sin tocar código.)
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

    /** Detalles del producto cacheados tras consultar a Google Play. */
    private var proDetails: ProductDetails? = null

    /** Precio formateado y localizado por Google (ej. "US$9.99", "$2.999,00"). Null si aún no cargó. */
    var formattedProPrice: String? = null
        private set

    /** Precio en micros (1.000.000 = 1 unidad de moneda). 0 si aún no cargó. */
    var proPriceMicros: Long = 0L
        private set

    /** Código de moneda ISO 4217 del precio (ej. "USD", "ARS", "EUR"). Null si aún no cargó. */
    var proPriceCurrencyCode: String? = null
        private set

    /** Listener para enterarse cuando el precio está disponible (la UI lo usa para refrescar el botón). */
    private var onPriceReady: ((String) -> Unit)? = null

    fun setPriceListener(listener: ((String) -> Unit)?) {
        onPriceReady = listener
        // Si ya estaba cacheado, avisar de inmediato
        formattedProPrice?.let { listener?.invoke(it) }
    }

    // ── Conexión ──────────────────────────────────────────────────────────────

    fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    restorePurchases()
                    queryProductDetails()
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    // ── Consultar detalles del producto (precio) ──────────────────────────────

    private fun queryProductDetails() {
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

                val details = productDetailsList[0]
                proDetails = details

                val offer = details.oneTimePurchaseOfferDetails
                val price = offer?.formattedPrice
                if (price != null) {
                    formattedProPrice = price
                    proPriceMicros = offer.priceAmountMicros
                    proPriceCurrencyCode = offer.priceCurrencyCode
                    activity.runOnUiThread { onPriceReady?.invoke(price) }
                }
            }
        }
    }

    // ── Verificar compra previa (restaurar si reinstala) ──────────────────────

    fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)   // pago único
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

    // ── Lanzar flujo de compra única ──────────────────────────────────────────

    fun launchPurchase() {
        // Si ya tenemos los detalles cacheados, lanzar directo
        proDetails?.let { details ->
            launchFlow(details)
            return
        }

        // Sino, consultar y lanzar cuando llegue la respuesta
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
                val details = productDetailsList[0]
                proDetails = details
                launchFlow(details)
            }
        }
    }

    private fun launchFlow(details: ProductDetails) {
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        activity.runOnUiThread {
            billingClient.launchBillingFlow(activity, billingFlowParams)
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

    // ── Confirmar compra (obligatorio para que no se revierta) ────────────────

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
