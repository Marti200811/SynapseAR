package com.example.ar

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.ar.databinding.ActivityMainBinding
import com.google.android.gms.ads.AdRequest

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val sharedVm: SharedViewModel by viewModels()
    lateinit var billingManager: BillingManager

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
            if (destination.id == R.id.arFragment && !sharedVm.isPro.value!!) {
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

        // Banner AdMob
        binding.adBanner.loadAd(AdRequest.Builder().build())

        // Ocultar banner si es Pro
        sharedVm.isPro.observe(this) { isPro ->
            binding.adBanner.visibility = if (isPro) View.GONE else View.VISIBLE
        }
    }

    fun showUpgradeDialog() {
        val dialog = UpgradeDialog()
        dialog.billingManager = billingManager
        dialog.show(supportFragmentManager, "upgrade")
    }

    override fun onPause()   { super.onPause();   binding.adBanner.pause() }
    override fun onResume()  { super.onResume();  binding.adBanner.resume() }
    override fun onDestroy() { super.onDestroy(); binding.adBanner.destroy(); billingManager.disconnect() }
}
