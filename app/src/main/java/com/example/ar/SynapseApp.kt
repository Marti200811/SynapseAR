package com.example.ar

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

class SynapseApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase Crashlytics (no requiere consentimiento del usuario)
        FirebaseApp.initializeApp(this)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

        // MobileAds.initialize() se llama en MainActivity DESPUÉS del flujo
        // de consentimiento UMP (GDPR para usuarios europeos)
    }
}
