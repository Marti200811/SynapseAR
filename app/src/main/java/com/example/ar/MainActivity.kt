package com.example.ar

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.ar.databinding.ActivityMainBinding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.material.snackbar.Snackbar
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val sharedVm: SharedViewModel by viewModels()
    lateinit var billingManager: BillingManager

    private lateinit var consentInformation: ConsentInformation
    private var isMobileAdsInitialized = false

    // ── In-App Updates ────────────────────────────────────────────────────
    private val updateManager by lazy { UpdateManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Mantener pantalla activa durante toda la sesión (app de trabajo profesional)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // ── Edge-to-edge: el sistema dibuja detrás de status y nav bar ───────
        // Aplicamos insets manualmente para que el contenido no quede tapado
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.navHostFragment) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, 0)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        binding.bottomNav.setupWithNavController(navController)
        // Material3 ignora itemIconTint="@null" del XML — hay que forzarlo en código
        // y recargar cada ícono para que use su propio color
        binding.bottomNav.itemIconTintList = null
        binding.bottomNav.menu.findItem(R.id.compassFragment)?.icon =
            androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_nav_compass)
        binding.bottomNav.menu.findItem(R.id.mapFragment)?.icon =
            androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_nav_map)
        binding.bottomNav.menu.findItem(R.id.arFragment)?.icon =
            androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_nav_ar)

        // Bloquear tab AR si no es Pro.
        // Usa sharedVm.isPro como única fuente de verdad: así respeta
        // también el toggle DEBUG_FORCE_FREE en builds de desarrollo.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.arFragment
                && sharedVm.isPro.value != true) {
                navController.popBackStack()
                showUpgradeDialog()
            }
        }

        // Billing: en release actualiza el estado Pro desde Google Play.
        // En debug, ProManager.isPro() ya maneja DEBUG_FORCE_FREE → no pisamos.
        billingManager = BillingManager(this) { isPro ->
            if (!BuildConfig.DEBUG) {
                // Si billing devuelve false, la verificación local (TESTING_MODE) tiene prioridad
                if (isPro) sharedVm.isPro.postValue(true)
                else sharedVm.isPro.postValue(ProManager.isPro(this))
            }
        }
        billingManager.connect()

        // Inicializar isPro desde caché local (respeta DEBUG_FORCE_FREE en debug)
        sharedVm.isPro.value = ProManager.isPro(this)

        // Ocultar banner si es Pro
        sharedVm.isPro.observe(this) { isPro ->
            binding.adBanner.visibility = if (isPro) View.GONE else View.VISIBLE
        }

        // En debug con DEBUG_FORCE_FREE activo: mostrar snackbar indicador
        // para confirmar que el modo "usuario gratis" está funcionando.
        if (BuildConfig.DEBUG && !ProManager.isPro(this)) {
            Snackbar.make(binding.root, "🆓 FREE MODE activo (DEBUG_FORCE_FREE=true)", Snackbar.LENGTH_LONG).show()
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

        // En debug: registrar emulador y dispositivo físico de desarrollo como
        // test devices para que AdMob sirva anuncios de prueba reales.
        if (BuildConfig.DEBUG) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(
                        AdRequest.DEVICE_ID_EMULATOR,
                        "2DBF6E008A7DA07D85064E66C5BBF4D5"  // Moto G15
                    ))
                    .build()
            )
        }

        MobileAds.initialize(this) {
            // M01: el callback puede llegar desde un background thread — postear a UI
            runOnUiThread {
                if (sharedVm.isPro.value != true) {
                    binding.adBanner.loadAd(AdRequest.Builder().build())
                }
            }
        }
    }

    // ── Upgrade dialog ────────────────────────────────────────────────────

    fun showUpgradeDialog() {
        val dialog = UpgradeDialog()
        dialog.billingManager = billingManager
        dialog.show(supportFragmentManager, "upgrade")
    }

    // ── Ciclo de vida AdMob + Updates ────────────────────────────────────

    override fun onPause()  { super.onPause();  binding.adBanner.pause() }
    override fun onResume() {
        super.onResume()
        binding.adBanner.resume()
        // Chequea updates cada vez que la app vuelve al frente
        updateManager.checkForUpdates()
    }
    override fun onDestroy() {
        super.onDestroy()
        binding.adBanner.destroy()
        billingManager.disconnect()
        updateManager.unregister()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updateManager.onActivityResult(requestCode, resultCode)
    }
}
