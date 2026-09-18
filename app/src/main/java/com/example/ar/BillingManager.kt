package com.example.ar

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Maneja el pago único Pro.
 *
 * En Play Console crear:
 *   Tipo: Producto dentro de la app (pago único)
 *   ID del producto: synapse_ar_pro   (Play Console NO permite guiones, solo _ . minúsculas y números)
 *   Precio base: US$3.99 (bajado de 9.99 el 2026-08-31)
 */
class BillingManager(
    private val activity: Activity,
    private val onProStatusChanged: (Boolean) -> Unit
) : PurchasesUpdatedListener {

    companion object {
        const val SKU_PRO = "synapse_ar_pro"
        private const val RECONNECT_DELAY_MS = 5_000L
    }

    // Scope propio — se cancela en disconnect() para no dejar coroutines huérfanas (C01)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val billingClient: BillingClient = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    /** Detalles del producto cacheados tras consultar a Google Play. */
    private var proDetails: ProductDetails? = null

    /** Precio formateado y localizado por Google (ej. "US$9.99"). Null si aún no cargó. */
    var formattedProPrice: String? = null
        private set

    /** Precio en micros (1.000.000 = 1 unidad de moneda). */
    var proPriceMicros: Long = 0L
        private set

    /** Código de moneda ISO 4217 (ej. "USD", "ARS"). */
    var proPriceCurrencyCode: String? = null
        private set

    private var onPriceReady: ((String) -> Unit)? = null

    fun setPriceListener(listener: ((String) -> Unit)?) {
        onPriceReady = listener
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
            override fun onBillingServiceDisconnected() {
                // M02: reconexión automática con delay (el servicio de Play Store puede caerse)
                mainHandler.postDelayed({ connect() }, RECONNECT_DELAY_MS)
            }
        })
    }

    // ── Consultar detalles del producto (precio) ──────────────────────────────

    private fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SKU_PRO)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )).build()

        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK &&
                queryResult.productDetailsList.isNotEmpty()) {
                val details = queryResult.productDetailsList[0]
                proDetails = details
                val offer = details.oneTimePurchaseOfferDetails
                val price = offer?.formattedPrice
                if (price != null) {
                    formattedProPrice = price
                    proPriceMicros = offer.priceAmountMicros
                    proPriceCurrencyCode = offer.priceCurrencyCode
                    // C02: verificar que la activity siga viva antes de postear a UI
                    if (!activity.isDestroyed && !activity.isFinishing) {
                        activity.runOnUiThread { onPriceReady?.invoke(price) }
                    }
                }
            }
        }
    }

    // ── Verificar compra previa (restaurar si reinstala) ──────────────────────

    /**
     * Consulta a Google Play las compras del usuario y actualiza el estado Pro.
     *
     * También confirma las compras que todavía no lo están. Una compra puede
     * terminar fuera de [onPurchasesUpdated]: un pago pendiente que se acredita
     * con la app cerrada, o un acknowledge que falló. Si nadie la confirma,
     * Google la reembolsa sola a los 3 días y el usuario pierde Pro.
     *
     * @param userInitiated true si lo pidió el usuario con "Restaurar compra":
     *   solo en ese caso se le avisa el resultado.
     */
    fun restorePurchases(userInitiated: Boolean = false) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val proPurchases = purchases.filter { it.products.contains(SKU_PRO) }
                val owned = proPurchases.filter {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                owned.forEach { acknowledgePurchase(it) }
                val isActive = owned.isNotEmpty()
                ProManager.setPro(activity, isActive)
                onProStatusChanged(isActive)
                if (userInitiated) {
                    val pending = proPurchases.any {
                        it.purchaseState == Purchase.PurchaseState.PENDING
                    }
                    toast(when {
                        isActive -> R.string.pro_restored
                        pending -> R.string.pro_purchase_pending
                        else -> R.string.pro_restore_none
                    })
                }
            } else if (userInitiated) {
                toast(R.string.pro_restore_failed)
            }
        }
    }

    // ── Lanzar flujo de compra única ──────────────────────────────────────────

    fun launchPurchase() {
        proDetails?.let { launchFlow(it); return }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SKU_PRO)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )).build()

        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK &&
                queryResult.productDetailsList.isNotEmpty()) {
                val details = queryResult.productDetailsList[0]
                proDetails = details
                launchFlow(details)
            } else {
                // El botón NUNCA debe quedar mudo (política Play "funcionalidad defectuosa"):
                // si el producto no está disponible, dar feedback visible en lugar de no hacer nada.
                toast(R.string.pro_purchase_unavailable)
            }
        }
    }

    /** Mensaje visible para el usuario. Se puede llamar desde cualquier hilo. */
    private fun toast(@androidx.annotation.StringRes message: Int) {
        if (!activity.isDestroyed && !activity.isFinishing) {
            activity.runOnUiThread {
                android.widget.Toast.makeText(
                    activity,
                    activity.getString(message),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun launchFlow(details: ProductDetails) {
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build()
            )).build()

        // C02: verificar activity viva antes de postear a UI
        if (!activity.isDestroyed && !activity.isFinishing) {
            activity.runOnUiThread {
                // El resultado no se mira acá a propósito: si el flujo no arranca,
                // la librería también se lo avisa a onPurchasesUpdated (verificado en
                // el bytecode de Billing 8.3.0). Tratarlo en los dos lados duplicaría
                // el mensaje de error.
                billingClient.launchBillingFlow(activity, billingFlowParams)
            }
        }
    }

    // ── Resultado de la compra ────────────────────────────────────────────────

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (!purchase.products.contains(SKU_PRO)) return@forEach
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> {
                            acknowledgePurchase(purchase)
                            ProManager.setPro(activity, true)
                            onProStatusChanged(true)
                        }
                        // C03: compra pendiente (pago en efectivo, etc.) — no otorgar Pro todavía.
                        // Cuando se acredite, restorePurchases() la confirma y activa Pro.
                        Purchase.PurchaseState.PENDING -> toast(R.string.pro_purchase_pending)
                        else -> Unit
                    }
                }
            }
            // C03: si ya lo compró (ej. reinstalación), restaurar en lugar de ignorar
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                toast(R.string.pro_already_owned)
                restorePurchases()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit  // no-op intencional
            // El diálogo ya se cerró: sin este aviso, un error deja al usuario sin
            // respuesta (política Play "funcionalidad defectuosa").
            else -> toast(R.string.pro_purchase_failed)
        }
    }

    // ── Confirmar compra (obligatorio para que no se revierta a los 3 días) ───

    private fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        // C01: usar el scope propio (se cancela en disconnect()) y verificar el resultado
        scope.launch {
            val result = billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            )
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                // Fallo silencioso: Google revertirá la compra a los 3 días.
                // En una versión futura: guardar el token y reintentar.
                android.util.Log.e("BillingManager",
                    "acknowledgePurchase failed: ${result.responseCode} ${result.debugMessage}")
            }
        }
    }

    fun disconnect() {
        mainHandler.removeCallbacksAndMessages(null)  // cancelar reconexión pendiente
        scope.cancel()                                 // C01: cancelar coroutines huérfanas
        billingClient.endConnection()
    }
}
