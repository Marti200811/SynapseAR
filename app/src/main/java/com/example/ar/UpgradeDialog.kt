package com.example.ar

import android.app.Dialog
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.view.LayoutInflater
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.example.ar.access.AccessManager
import com.example.ar.access.Feature
import com.example.ar.access.RewardedAdManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Diálogo de venta de Pro, con la opción de desbloquear una función mirando un anuncio.
 *
 * Este diálogo NO recibe nada por propiedades: las busca él. Antes la Activity le empujaba
 * `billingManager`, `rewardedAdManager`, `source`, `feature` y `onRewardEarned` justo después
 * de construirlo. Eso funcionaba la primera vez y se rompía en silencio en la segunda: al
 * cambiar el tamaño de letra, el tema o el idioma, Android recrea la Activity y vuelve a
 * instanciar el diálogo por su constructor vacío, sin volver a pasar por `showUpgradeDialog`.
 * Las cinco propiedades volvían a su valor inicial y el diálogo reaparecía **con el botón de
 * compra muerto**: `billingManager?.launchPurchase()` no hacía nada, el precio real no se
 * mostraba y la opción del anuncio desaparecía. Peor todavía, `Analytics.upgradeButtonTapped`
 * sí se registraba, así que el embudo contaba una intención de compra que nunca se lanzó.
 *
 * El arreglo es distinto al de los otros seis diálogos, y por una razón concreta: dos de las
 * cinco cosas que necesitaba son **objetos vivos** (`BillingManager`, `RewardedAdManager`) y
 * no caben en un `Bundle`. Así que cada cosa entra por donde le corresponde:
 *
 * - los dos managers: se leen de [MainActivity] en cada acceso. La Activity los recrea en su
 *   `onCreate`, así que después de la recreación hay instancias vivas — el diálogo solo tenía
 *   que preguntar en vez de esperar que se las dieran.
 * - `source` y `feature`: escalares, van en `arguments` vía [newInstance].
 * - `onRewardEarned`: era una lambda, y una lambda no sobrevive. Pasa a ser un resultado de
 *   fragment ([resultKeyFor]), el mismo mecanismo que usan los otros seis.
 */
class UpgradeDialog : DialogFragment() {

    companion object {
        private const val ARG_SOURCE = "source"
        private const val ARG_FEATURE = "feature"

        /**
         * Canal por el que se avisa que [feature] quedó desbloqueada con un anuncio.
         *
         * Hay una clave por función en vez de una sola compartida, por dos motivos. Uno: todos
         * los que escuchan lo hacen en el `FragmentManager` de la Activity —que es donde se
         * muestra este diálogo, no en el del fragment que lo pidió— y ahí
         * `setFragmentResultListener` admite **un solo listener por clave**, así que una clave
         * compartida haría que el último en registrarse pisara a los demás. Dos: derivar la
         * clave de la función hace imposible que el lado que avisa y el que escucha usen
         * strings distintos.
         */
        fun resultKeyFor(feature: Feature) = "upgrade_unlocked_${feature.name}"

        /**
         * @param source cuál de las funciones bloqueadas trajo al usuario acá (va a Analytics).
         * @param feature qué se desbloquea si mira un anuncio. Null = no se ofrece anuncio y
         *                este diálogo nunca emite un resultado.
         */
        fun newInstance(source: String, feature: Feature? = null) = UpgradeDialog().apply {
            arguments = Bundle().apply {
                putString(ARG_SOURCE, source)
                putString(ARG_FEATURE, feature?.name)
            }
        }
    }

    private val source: String
        get() = arguments?.getString(ARG_SOURCE) ?: "unknown"

    /**
     * El enum viaja por `name`, no por `ordinal`: reordenar [Feature] no puede cambiar en
     * silencio qué función se desbloquea. Si el nombre guardado ya no existe (un `arguments`
     * viejo restaurado tras actualizar la app), vale más no ofrecer el anuncio que crashear.
     */
    private val feature: Feature?
        get() = arguments?.getString(ARG_FEATURE)
            ?.let { nombre -> Feature.entries.firstOrNull { it.name == nombre } }

    private val billing: BillingManager?
        get() = (activity as? MainActivity)?.billingOrNull

    private val ads: RewardedAdManager?
        get() = (activity as? MainActivity)?.rewardedAdOrNull

    private var btnBuy: MaterialButton? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_upgrade, null)

        btnBuy = view.findViewById<MaterialButton>(R.id.btnBuyPro).apply {
            setOnClickListener {
                // Se registra ANTES de lanzar la compra: mide intención aunque
                // el flujo de Google Play falle (ej. perfil de pagos incompleto).
                Analytics.upgradeButtonTapped(requireContext(), source)
                billing?.launchPurchase()
                dismiss()
            }
        }

        val ads = ads
        val feat = feature
        val btnAd = view.findViewById<MaterialButton>(R.id.btnWatchAd)

        // Si no hay anuncio listo, se intenta cargar uno para la próxima vez que
        // se abra el diálogo. Sin esto, si la precarga inicial falló (sin red al
        // arrancar, por ejemplo), la opción no volvería a aparecer nunca.
        if (ads != null && !ads.isAdReady()) ads.preload()

        // Un anuncio por función y por día. Si ya lo usó, en vez del botón se muestra una
        // línea que lo explica: que el botón desaparezca sin más deja al usuario sin
        // entender qué pasó.
        val quedaAnuncioHoy = feat == null ||
            AccessManager.canWatchAdFor(requireContext(), feat)
        if (!quedaAnuncioHoy) {
            view.findViewById<TextView>(R.id.tvAdUsedToday).visibility = android.view.View.VISIBLE
        }

        // Solo se ofrece si HAY un anuncio cargado ahora. Nunca se muestra un botón
        // deshabilitado con "Cargando…": ese fue un bug real en el proyecto hermano
        // Oráculo, donde el usuario veía un botón muerto sin saber por qué.
        if (ads != null && feat != null && ads.isAdReady() && quedaAnuncioHoy) {
            btnAd.visibility = android.view.View.VISIBLE
            Analytics.rewardedOffered(requireContext(), source)
            btnAd.setOnClickListener {
                // El applicationContext sobrevive al fragment: si este muere mientras
                // corre el anuncio, la recompensa igual se otorga. Sería injusto que
                // el usuario mire el anuncio completo y no reciba el desbloqueo.
                val appCtx = requireContext().applicationContext
                Analytics.rewardedStarted(appCtx, source)
                ads.show(
                    onRewardEarned = {
                        // Corre con el anuncio todavía en pantalla, a propósito: si el
                        // sistema mata el proceso en el end card, esto ya quedó escrito.
                        AccessManager.grantTemporary(appCtx, feat)
                        Analytics.rewardedEarned(appCtx, source)
                    },
                    onClosedAfterReward = {
                        // La UI sí depende de que el fragment siga vivo.
                        if (isAdded) {
                            setFragmentResult(resultKeyFor(feat), Bundle())
                            dismiss()
                        }
                    },
                    onClosedWithoutReward = {
                        // Cerró el anuncio a propósito. No es un error: el diálogo
                        // queda abierto con la opción de comprar Pro. Sin Toast.
                    },
                    onFailedToShow = {
                        if (isAdded) {
                            android.widget.Toast.makeText(
                                requireContext(),
                                getString(R.string.rewarded_failed),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }

        // Mostrar el precio real de Google Play (localizado, refleja promos)
        applyPrice(billing?.formattedProPrice)
        billing?.setPriceListener { price ->
            if (isAdded) applyPrice(price)
        }

        view.findViewById<TextView>(R.id.btnRestore).setOnClickListener {
            billing?.restorePurchases(userInitiated = true)
            dismiss()
        }

        view.findViewById<TextView>(R.id.btnDismiss).setOnClickListener {
            dismiss()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
    }

    /**
     * Muestra el precio en el botón. Si hay una promo de lanzamiento activa
     * (Remote Config), antepone el precio "normal" tachado (precio ancla),
     * un clásico de la estrategia de ventas.
     */
    private fun applyPrice(price: String?) {
        val btn = btnBuy ?: return

        if (price.isNullOrBlank()) {
            btn.text = getString(R.string.upgrade_btn_buy)   // fallback sin precio
            return
        }

        val anchor = billing?.let {
            PromoManager.anchorPrice(it.proPriceMicros, it.proPriceCurrencyCode)
        }

        if (anchor != null) {
            // "✨ Desbloquear Pro — $9.98  $4.99"  (con $9.98 tachado y más chico)
            val sb = SpannableStringBuilder(getString(R.string.upgrade_btn_buy)).append(" — ")
            val start = sb.length
            sb.append(anchor)
            sb.setSpan(StrikethroughSpan(), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.85f), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("  ").append(price)
            btn.text = sb
        } else {
            btn.text = getString(R.string.upgrade_btn_buy_price, price)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Evitar fugas: el listener captura la vista del botón
        billing?.setPriceListener(null)
        btnBuy = null
    }
}
