package com.example.ar

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.ar.databinding.ActivityMainBinding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val sharedVm: SharedViewModel by viewModels()
    lateinit var billingManager: BillingManager

    private lateinit var consentInformation: ConsentInformation
    private var isMobileAdsInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        binding.bottomNav.setupWithNavController(navController)

        // Bloquear tab AR si no es Pro
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.arFragment && sharedVm.isPro.value != true) {
                navController.popBackStack()
                showUpgradeDialog()
            }
        }

        // Billing
        billingManager = BillingManager(this) { isPro ->
            sharedVm.isPro.postValue(isPro)
        }
        billingManager.connect()

        // Inicializar isPro desde caché local
        sharedVm.isPro.value = ProManager.isPro(this)

        // Ocultar banner si es Pro
        sharedVm.isPro.observe(this) { isPro ->
            binding.adBanner.visibility = if (isPro) View.GONE else View.VISIBLE
        }

        // ── Consentimiento UMP → inicializar AdMob ────────────────────────
        initAdsWithConsent()
    }

    // ── UMP: flujo de consentimiento GDPR (Europa) ────────────────────────

    private fun initAdsWithConsent() {
        consentInformation = UserMessagingPlatform.getConsentInformation(this)

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            this, params,
            {
                // Éxito: mostrar formulario si corresponde (solo usuarios UE)
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                    // Formulario cerrado (o no era necesario mostrar ninguno)
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAds()
                    }
                }
            },
            { _ ->
                // Error al obtener estado de consentimiento → cargar anuncios de todas formas
                // (comportamiento recomendado por Google para países fuera de la UE)
                initializeMobileAds()
            }
        )

        // Si ya tiene consentimiento de una sesión anterior, cargar de inmediato
        if (consentInformation.canRequestAds()) {
            initializeMobileAds()
        }
    }

    private fun initializeMobileAds() {
        if (isMobileAdsInitialized) return
        isMobileAdsInitialized = true

        MobileAds.initialize(this) {
            // Cargar banner solo si el usuario no es Pro
            if (sharedVm.isPro.value != true) {
                binding.adBanner.loadAd(AdRequest.Builder().build())
            }
        }
    }

    // ── Upgrade dialog ────────────────────────────────────────────────────

    fun showUpgradeDialog() {
        val dialog = UpgradeDialog()
        dialog.billingManager = billingManager
        dialog.show(supportFragmentManager, "upgrade")
    }

    // ── Ciclo de vida AdMob ───────────────────────────────────────────────

    override fun onPause()   { super.onPause();   binding.adBanner.pause() }
    override fun onResume()  { super.onResume();  binding.adBanner.resume() }
    override fun onDestroy() {
        super.onDestroy()
        binding.adBanner.destroy()
        billingManager.disconnect()
    }
}
