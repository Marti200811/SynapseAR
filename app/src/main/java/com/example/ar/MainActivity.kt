package com.example.ar

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.ar.access.AccessManager
import com.example.ar.access.Feature
import com.example.ar.access.RewardedAdManager
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
    lateinit var rewardedAdManager: RewardedAdManager

    private lateinit var consentInformation: ConsentInformation
    private var isMobileAdsInitialized = false

    // ── In-App Updates ────────────────────────────────────────────────────
    private val updateManager by lazy { UpdateManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge por la vía moderna. Reemplaza a setDecorFitsSystemWindows(false) y a
        // statusBarColor/navigationBarColor del tema, que Android 15 ignora y Play marca como
        // obsoletos. dark(TRANSPARENT) en ambas barras = íconos claros sobre fondo transparente,
        // que es como se veía antes con bg_deep detrás. Va antes de setContentView.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Mantener pantalla activa durante toda la sesión (app de trabajo profesional)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // El sistema dibuja detrás de las barras; aplicamos los insets a mano para que el
        // contenido no quede tapado.
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

        rewardedAdManager = RewardedAdManager(this)

        // Nueva sesión: habilita que se vuelva a contar una alineación exitosa.
        // Sin esto, el contador de la sesión anterior quedaría marcado y no se
        // registraría ningún éxito nuevo nunca más.
        RatingPrompt.resetSession(this)

        // Bloquear tab AR si no tiene acceso.
        // AccessManager resuelve "es Pro O tiene un desbloqueo temporal vigente",
        // y ProManager sigue respetando el toggle DEBUG_FORCE_FREE en builds de desarrollo.
        // OJO: este listener despacha el destino actual apenas se registra, y
        // showUpgradeDialog() usa billingManager y rewardedAdManager. Tiene que
        // quedar DESPUÉS de que ambos estén inicializados.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.arFragment
                && !AccessManager.canUse(this, Feature.AR)) {
                navController.popBackStack()
                showUpgradeDialog(
                    source = Analytics.SRC_AR_TAB,
                    feature = Feature.AR,
                    onUnlocked = { navController.navigate(R.id.arFragment) }
                )
            }
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
                // Recién ahora UpgradeDialog puede reintentar la precarga por su cuenta
                rewardedAdManager.adsInitialized = true
                if (sharedVm.isPro.value != true) {
                    binding.adBanner.loadAd(AdRequest.Builder().build())
                    rewardedAdManager.preload()
                }
            }
        }
    }

    // ── Upgrade dialog ────────────────────────────────────────────────────

    /**
     * @param source cuál de las funciones bloqueadas trajo al usuario acá.
     * @param feature qué se desbloquea si mira un anuncio. Null = no se ofrece anuncio.
     * @param onUnlocked qué ejecutar si gana la recompensa.
     */
    fun showUpgradeDialog(
        source: String,
        feature: Feature? = null,
        onUnlocked: (() -> Unit)? = null
    ) {
        Analytics.upgradeDialogShown(this, source)
        val dialog = UpgradeDialog()
        dialog.billingManager = billingManager
        dialog.source = source
        dialog.rewardedAdManager = rewardedAdManager
        dialog.feature = feature
        dialog.onRewardEarned = onUnlocked
        dialog.show(supportFragmentManager, "upgrade")
    }

    // ── Ciclo de vida AdMob + Updates ────────────────────────────────────

    override fun onPause()  { super.onPause();  binding.adBanner.pause() }
    override fun onResume() {
        super.onResume()
        binding.adBanner.resume()
        // Chequea updates cada vez que la app vuelve al frente
        updateManager.checkForUpdates()

        // Pide calificación si el usuario ya alineó antenas con éxito varias veces.
        // Con retraso a propósito: si hay una actualización forzada, que se lleve
        // ella la pantalla primero y no se apilen dos diálogos.
        binding.root.postDelayed({
            if (!isFinishing && !isDestroyed) RatingPrompt.maybeAsk(this)
        }, 2_500L)
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
