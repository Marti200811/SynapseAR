package com.example.ar

import android.app.Activity
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings

/**
 * Gestiona actualizaciones de la app mediante:
 *  - Google Play In-App Updates API  (flexible para actualizaciones normales,
 *                                     immediate para críticas)
 *  - Firebase Remote Config          (kill-switch: fuerza immediate si
 *                                     remote_config[force_update_version] > installedVersion)
 *
 * Uso en MainActivity:
 *   private val updateManager = UpdateManager(this)
 *   override fun onResume()  { updateManager.checkForUpdates() }
 *   override fun onDestroy() { updateManager.unregister() }
 *   override fun onActivityResult(requestCode, resultCode, data) {
 *       updateManager.onActivityResult(requestCode, resultCode)
 *   }
 */
class UpdateManager(private val activity: Activity) {

    companion object {
        private const val TAG = "UpdateManager"

        /** requestCode para startUpdateFlowForResult */
        const val REQ_UPDATE = 501

        /** Clave en Remote Config que guarda el versionCode mínimo aceptable */
        private const val RC_KEY_FORCE_VERSION = "force_update_version"

        /** Intervalo de fetch en producción (12 horas) */
        private const val FETCH_INTERVAL_PROD = 43_200L
    }

    private val appUpdateManager: AppUpdateManager =
        AppUpdateManagerFactory.create(activity)

    // Listener para actualizaciones flexibles: cuando se descarga, pide completar
    private val installListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            Log.d(TAG, "Update downloaded — prompting to complete")
            appUpdateManager.completeUpdate()
        }
    }

    init {
        appUpdateManager.registerListener(installListener)
    }

    // ── API pública ───────────────────────────────────────────────────────

    /**
     * Llama esto en onResume() de MainActivity.
     * 1. Fetch Remote Config → decide si forzar immediate update
     * 2. Consulta Play Store → lanza flexible o immediate según corresponda
     */
    fun checkForUpdates() {
        fetchRemoteConfig { forceVersionCode ->
            checkPlayStoreUpdate(forceVersionCode)
        }
    }

    /**
     * Llama en onDestroy() de MainActivity para liberar el listener.
     */
    fun unregister() {
        appUpdateManager.unregisterListener(installListener)
    }

    /**
     * Llama desde onActivityResult() de MainActivity para reanudar updates
     * que quedaron incompletos (flexible descargado pero no instalado).
     */
    fun onActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode == REQ_UPDATE) {
            if (resultCode != Activity.RESULT_OK) {
                Log.w(TAG, "Update flow cancelled/failed — resultCode=$resultCode")
            }
        }
    }

    // ── Internos ──────────────────────────────────────────────────────────

    private fun fetchRemoteConfig(onResult: (forceVersionCode: Long) -> Unit) {
        val remoteConfig = FirebaseRemoteConfig.getInstance()

        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings { minimumFetchIntervalInSeconds = FETCH_INTERVAL_PROD }
        )
        // Defaults desde XML (res/xml/remote_config_defaults.xml)
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            val forceVersion = if (task.isSuccessful) {
                remoteConfig.getLong(RC_KEY_FORCE_VERSION).also {
                    Log.d(TAG, "Remote Config fetched — force_update_version=$it")
                }
            } else {
                Log.w(TAG, "Remote Config fetch failed — using cached/default")
                remoteConfig.getLong(RC_KEY_FORCE_VERSION)
            }
            onResult(forceVersion)
        }
    }

    private fun checkPlayStoreUpdate(forceVersionCode: Long) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            val installedVersionCode = activity.packageManager
                .getPackageInfo(activity.packageName, 0).longVersionCode

            val isForced = forceVersionCode > 0 &&
                    installedVersionCode < forceVersionCode

            when {
                // ── Actualización crítica (Remote Config manda forzar) ──────
                isForced && info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE -> {
                    Log.i(TAG, "FORCED immediate update required (force=$forceVersionCode, installed=$installedVersionCode)")
                    launchUpdate(info, AppUpdateType.IMMEDIATE)
                }

                // ── Actualización crítica (Play Store la marca como tal) ────
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) &&
                        (info.clientVersionStalenessDays() ?: 0) >= 14 -> {
                    Log.i(TAG, "Stale update (${info.clientVersionStalenessDays()} days) — launching immediate")
                    launchUpdate(info, AppUpdateType.IMMEDIATE)
                }

                // ── Actualización normal (flexible, se descarga en background) ─
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                    Log.i(TAG, "Flexible update available")
                    launchUpdate(info, AppUpdateType.FLEXIBLE)
                }

                // ── Actualización flexible descargada pero no instalada ────
                info.installStatus() == InstallStatus.DOWNLOADED -> {
                    Log.i(TAG, "Update already downloaded — completing")
                    appUpdateManager.completeUpdate()
                }

                else -> Log.d(TAG, "No update needed (availability=${info.updateAvailability()})")
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to check for updates: ${e.message}")
        }
    }

    private fun launchUpdate(info: AppUpdateInfo, @AppUpdateType type: Int) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                info,
                activity,
                AppUpdateOptions.newBuilder(type).build(),
                REQ_UPDATE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not launch update flow: ${e.message}")
        }
    }
}
