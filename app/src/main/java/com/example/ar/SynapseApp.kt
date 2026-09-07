package com.example.ar

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig

class SynapseApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase Crashlytics (no requiere consentimiento del usuario).
        // Desactivado en debug: los crashes de desarrollo ensuciaban los datos
        // de usuarios reales, justo el panel que hay que mirar cuando algo falla.
        FirebaseApp.initializeApp(this)
        FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        // Defaults de Remote Config temprano, para que cualquier lectura
        // (ej. flags de promo en UpgradeDialog) tenga valores seguros aunque
        // el fetch todavía no haya completado.
        FirebaseRemoteConfig.getInstance().setDefaultsAsync(R.xml.remote_config_defaults)

        // MobileAds.initialize() se llama en MainActivity DESPUÉS del flujo
        // de consentimiento UMP (GDPR para usuarios europeos)
    }
}
