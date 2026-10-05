package com.watube.yard

import android.app.Application
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.watube.yard.db.DatabaseHelper
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.NewPipeExtractorInstance
import com.watube.yard.helpers.NotificationHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.helpers.ProxyHelper
import com.watube.yard.helpers.ShortcutHelper
import com.watube.yard.ui.tools.RestMode
import com.watube.yard.util.ExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class WatubeApp : Application(), SingletonImageLoader.Factory {

    // Scope global de l'application qui survit aux changements de configuration
    // Utilise Dispatchers.IO pour ne pas bloquer le thread principal
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Deferred pour les initialisations critiques qui peuvent être attendues au premier besoin
    // Permet au player d'attendre si nécessaire sans bloquer le démarrage
    lateinit var newPipeInit: Deferred<Unit>
        private set
    lateinit var proxyInit: Deferred<Unit>
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // ✅ PHASE 1 : Initialisations synchrones ULTRA-RAPIDES (<10ms)
        // Ces opérations sont nécessaires immédiatement et très rapides
        PreferenceHelper.initialize(applicationContext)
        RestMode.schedule()
        setupExceptionHandler()
        initializeNotificationChannels()

        // ✅ PHASE 2 : Initialisations asynchrones non-bloquantes
        // Ne bloquent PAS le premier frame rendu (critique pour le 120 Hz)
        appScope.launch {
            // analytics the Cast library stored on the device before its telemetry was removed
            deleteDatabase("com.google.android.datatransport.events")
            deleteSharedPreferences("$packageName.client_cast_analytics_data")
            PreferenceHelper.migrate()
            // tourist mode promises an empty device, so anything written while it was off
            // (a restored backup for instance) is dropped, then the retention is applied
            if (PreferenceHelper.isTouristModeEnabled()) DatabaseHelper.clearBrowsingData()
            DatabaseHelper.applyWatchHistoryRetention()
            ImageHelper.initializeImageLoader(this@WatubeApp)
            // preloads the watch positions so that list rows don't need to query Room on bind
            DatabaseHelper.primeWatchPositionCache()
            NotificationHelper.enqueueWork(
                context = this@WatubeApp,
                existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
            )
            ShortcutHelper.createShortcuts(this@WatubeApp)
        }

        // ✅ PHASE 3 : Initialisations deferred pour composants critiques
        // S'exécutent en arrière-plan, prêtes quand le player en aura besoin
        newPipeInit = appScope.async {
            NewPipeExtractorInstance.init()
        }

        proxyInit = appScope.async {
            ProxyHelper.fetchProxyUrl()
        }
    }

    /**
     * Coil singleton factory: guarantees that images, the data saver checks and the manual
     * cache clearing all operate on one single set of caches.
     */
    override fun newImageLoader(context: Context): ImageLoader = ImageHelper.buildImageLoader(context)

    /**
     * Configure le gestionnaire d'exceptions
     * Opération très rapide, peut rester synchrone
     */
    private fun setupExceptionHandler() {
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        val exceptionHandler = ExceptionHandler(applicationContext, defaultExceptionHandler)
        Thread.setDefaultUncaughtExceptionHandler(exceptionHandler)
    }

    /**
     * Initialise les channels de notification uniquement s'ils n'existent pas
     * Opération rapide, peut rester synchrone
     */
    private fun initializeNotificationChannels() {
        val notificationManager = NotificationManagerCompat.from(this)
        val existingChannels = notificationManager.notificationChannels.map { it.id }
        val channelsToCreate = mutableListOf<NotificationChannelCompat>()

        if (DOWNLOAD_CHANNEL_NAME !in existingChannels) {
            channelsToCreate.add(
                NotificationChannelCompat.Builder(
                    DOWNLOAD_CHANNEL_NAME,
                    NotificationManagerCompat.IMPORTANCE_LOW
                )
                    .setName(getString(R.string.download_channel_name))
                    .setDescription(getString(R.string.download_channel_description))
                    .build()
            )
        }

        if (PLAYLIST_DOWNLOAD_ENQUEUE_CHANNEL_NAME !in existingChannels) {
            channelsToCreate.add(
                NotificationChannelCompat.Builder(
                    PLAYLIST_DOWNLOAD_ENQUEUE_CHANNEL_NAME,
                    NotificationManagerCompat.IMPORTANCE_LOW
                )
                    .setName(getString(R.string.download_playlist))
                    .setDescription(getString(R.string.enqueue_playlist_description))
                    .build()
            )
        }

        if (PLAYER_CHANNEL_NAME !in existingChannels) {
            channelsToCreate.add(
                NotificationChannelCompat.Builder(
                    PLAYER_CHANNEL_NAME,
                    NotificationManagerCompat.IMPORTANCE_LOW
                )
                    .setName(getString(R.string.player_channel_name))
                    .setDescription(getString(R.string.player_channel_description))
                    .build()
            )
        }

        if (PUSH_CHANNEL_NAME !in existingChannels) {
            channelsToCreate.add(
                NotificationChannelCompat.Builder(
                    PUSH_CHANNEL_NAME,
                    NotificationManagerCompat.IMPORTANCE_DEFAULT
                )
                    .setName(getString(R.string.push_channel_name))
                    .setDescription(getString(R.string.push_channel_description))
                    .build()
            )
        }

        if (channelsToCreate.isNotEmpty()) {
            notificationManager.createNotificationChannelsCompat(channelsToCreate)
        }
    }

    companion object {
        @Volatile
        lateinit var instance: WatubeApp
            private set

        const val DOWNLOAD_CHANNEL_NAME = "download_service"
        const val PLAYLIST_DOWNLOAD_ENQUEUE_CHANNEL_NAME = "playlist_download_enqueue"
        const val PLAYER_CHANNEL_NAME = "player_mode"
        const val PUSH_CHANNEL_NAME = "notification_worker"
    }
}