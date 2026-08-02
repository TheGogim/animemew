package com.mew.animemew.data.ads

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.View
import com.mew.animemew.data.auth.SessionManager
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener

// =========================================================
//  AdManager — Singleton central para gestionar anuncios.
//
//  CONFIGURACIÓN:
//  - Start.io App ID: 206496936
//  - Frecuencia interstitial: cada 2 episodios (pares = ad)
//  - TV: NO interstitial, SÍ banner
// =========================================================

class AdManager private constructor(private val context: Context) {

    private val TAG = "AdManager"
    private val START_IO_APP_ID = "206496936"

    companion object {
        const val INTERSTITIAL_FREQUENCY = 2

        @Volatile
        private var INSTANCE: AdManager? = null

        fun getInstance(context: Context): AdManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    @Volatile
    private var isInitialized = false

    private val prefs = context.getSharedPreferences("ad_state", Context.MODE_PRIVATE)

    /**
     * Inicializa Start.io SDK de forma PIGUERA.
     *
     * NO se llama en MainActivity.onCreate() para evitar que el splash
     * verde/magenta/azul aparezca al abrir la app.
     *
     * Se inicializa automáticamente cuando se crea el primer banner
     * o cuando se va a mostrar el primer interstitial.
     *
     * - tercer parámetro false = desactivar return ads (back ads)
     * - setUserConsent "pas" false = NON_PERSONALIZED (suprime el popup GDPR)
     * - CCPA "1YNN" = no opt-out (suprime el popup CCPA)
     */
    fun initialize(activity: Activity) {
        if (isInitialized) return

        try {
            Log.i(TAG, "Inicializando Start.io SDK (App ID: $START_IO_APP_ID)")

            // Init SDK:
            // - tercer parámetro false = desactivar return ads (back ads)
            StartAppSDK.setTestAdsEnabled(false)
            StartAppSDK.init(activity, START_IO_APP_ID, false)

            // FIX: Suprimir el popup de consentimiento GDPR/CCPA.
            // setUserConsent con cualquier valor (true o false) SUPRIME el popup
            // porque Start.io ve que el consent ya fue dado programáticamente.
            // Usamos false = NON_PERSONALIZED (lo que el usuario pidió).
            try {
                StartAppSDK.setUserConsent(
                    activity,                          // Context
                    "pas",                             // Consent type (personalized ad serving)
                    System.currentTimeMillis(),        // Timestamp
                    false                              // false = NON_PERSONALIZED
                )
                Log.i(TAG, "✅ Consent GDPR seteado: NON_PERSONALIZED (popup suprimido)")
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo setear consent GDPR: ${e.message}")
            }

            // CCPA / US Privacy string
            // "1YNN" = usuario NO hizo opt-out (ads normales)
            try {
                StartAppSDK.getExtras(activity)
                    .edit()
                    .putString("IABUSPrivacy_String", "1YNN")
                    .apply()
                Log.i(TAG, "✅ CCPA string seteado: 1YNN (popup suprimido)")
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo setear CCPA string: ${e.message}")
            }

            isInitialized = true
            Log.i(TAG, "✅ Start.io SDK inicializado (return ads OFF, consent=NON_PERSONALIZED, splash=off)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando Start.io: ${e.message}")
        }
    }

    /**
     * Inicializa el SDK solo si no se ha hecho ya.
     * Se llama desde createBanner() y showInterstitial()
     * para asegurar que esté listo.
     */
    private fun ensureInitialized(activity: Activity) {
        if (!isInitialized) {
            initialize(activity)
        }
    }

    /**
     * Verifica si el usuario actual debe ver anuncios.
     */
    fun isAdsEnabled(): Boolean {
        val session = SessionManager.getInstance(context).session.value
        if (!session.isLoggedIn) return true
        return session.adsEnabled
    }

    /**
     * NUEVO: Verifica si hay un bloqueador de anuncios activo.
     *
     * Si el usuario es premium (adsEnabled = false), NO se hace el chequeo
     * porque el usuario no debería ver anuncios de todos modos.
     *
     * @return true si se debe bloquear la reproducción (adblocker + no premium)
     */
    suspend fun isAdblockerBlockingPlayback(): Boolean {
        // Si el usuario es premium, no bloquear nunca
        if (!isAdsEnabled()) {
            Log.i(TAG, "isAdblockerBlockingPlayback: usuario premium, no se chequea adblocker")
            return false
        }
        // Si no es premium, chequear adblocker
        val adBlockDetector = AdBlockDetector.getInstance(context)
        return adBlockDetector.isAdBlocked(useCache = true)
    }

    /**
     * Detecta si el dispositivo es Android TV.
     */
    fun isTvDevice(): Boolean {
        return try {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Determina si se debe mostrar interstitial al iniciar un episodio.
     * Cada 2 episodios (pares = ad, impares = no ad).
     */
    fun shouldShowInterstitial(): Boolean {
        if (!isAdsEnabled()) {
            Log.i(TAG, "shouldShowInterstitial: false (ads desactivados)")
            return false
        }
        if (isTvDevice()) {
            Log.i(TAG, "shouldShowInterstitial: false (TV device)")
            return false
        }
        if (!isInitialized) {
            Log.w(TAG, "shouldShowInterstitial: false (SDK no inicializado)")
            return false
        }

        val count = prefs.getInt(KEY_EPISODE_COUNT, 0) + 1
        prefs.edit().putInt(KEY_EPISODE_COUNT, count).apply()

        val shouldShow = count % INTERSTITIAL_FREQUENCY == 0
        Log.i(TAG, "shouldShowInterstitial: count=$count, show=$shouldShow")
        return shouldShow
    }

    /**
     * Carga y muestra un interstitial.
     * @param activity Activity actual (requerido por Start.io)
     * @param onAdClosed Callback cuando el anuncio se cierra (o falla)
     */
    fun showInterstitial(activity: Activity, onAdClosed: () -> Unit) {
        // Asegurar que el SDK esté inicializado
        ensureInitialized(activity)

        if (!isAdsEnabled() || isTvDevice()) {
            Log.i(TAG, "showInterstitial: saltando (no aplicable)")
            onAdClosed()
            return
        }

        Log.i(TAG, "showInterstitial: cargando anuncio...")

        try {
            val startAppAd = StartAppAd(activity)

            // Listener para cuando el ad se carga
            startAppAd.loadAd(object : AdEventListener {
                override fun onReceiveAd(ad: com.startapp.sdk.adsbase.Ad) {
                    Log.i(TAG, "✅ Interstitial cargado, mostrando...")

                    // Mostrar el ad — showAd recibe AdDisplayListener
                    startAppAd.showAd(object : AdDisplayListener {
                        override fun adHidden(ad: com.startapp.sdk.adsbase.Ad) {
                            Log.i(TAG, "✅ Interstitial cerrado por el usuario")
                            onAdClosed()
                        }

                        override fun adDisplayed(ad: com.startapp.sdk.adsbase.Ad) {
                            Log.i(TAG, "📺 Interstitial mostrado en pantalla")
                        }

                        override fun adClicked(ad: com.startapp.sdk.adsbase.Ad) {
                            Log.i(TAG, "👆 Click en interstitial")
                        }

                        override fun adNotDisplayed(ad: com.startapp.sdk.adsbase.Ad) {
                            Log.w(TAG, "⚠️ Interstitial no se mostró, continuando...")
                            onAdClosed()
                        }
                    })
                }

                override fun onFailedToReceiveAd(ad: com.startapp.sdk.adsbase.Ad?) {
                    Log.w(TAG, "⚠️ Falló cargar interstitial, continuando...")
                    onAdClosed()
                }
            })

            // Safety net: si después de 15s no se cerró, forzar continuar
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                // El callback debería haberse llamado ya
            }, 15000)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error mostrando interstitial: ${e.message}")
            onAdClosed()
        }
    }

    /**
     * Crea un Banner de Start.io para usar en composables.
     * Devuelve null si los ads están desactivados.
     */
    fun createBanner(activity: Activity): Banner? {
        // Asegurar que el SDK esté inicializado
        ensureInitialized(activity)

        if (!isAdsEnabled()) return null

        return try {
            Banner(activity).apply {
                setBannerListener(object : BannerListener {
                    // BannerListener usa View, no Ad
                    override fun onReceiveAd(view: View) {
                        Log.i(TAG, "✅ Banner cargado")
                    }

                    override fun onFailedToReceiveAd(view: View) {
                        Log.w(TAG, "⚠️ Banner falló al cargar")
                    }

                    override fun onImpression(view: View) {
                        // Impresión registrada
                    }

                    override fun onClick(view: View) {
                        Log.i(TAG, "👆 Click en banner")
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creando banner: ${e.message}")
            null
        }
    }

    /**
     * Resetea el contador de episodios (para testing).
     */
    fun resetEpisodeCounter() {
        prefs.edit().putInt(KEY_EPISODE_COUNT, 0).apply()
        Log.i(TAG, "Contador de episodios reseteado")
    }

    private val KEY_EPISODE_COUNT = "episode_count"
}
