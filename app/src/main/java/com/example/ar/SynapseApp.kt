package com.example.ar

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

class SynapseApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase Crashlytics
        FirebaseApp.initializeApp(this)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

        // AdMob
        MobileAds.initialize(this)
    }
}
