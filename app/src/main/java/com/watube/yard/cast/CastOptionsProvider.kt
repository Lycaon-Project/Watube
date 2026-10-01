package com.watube.yard.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.watube.yard.ui.activities.MainActivity

/**
 * Configuration unique du SDK Cast, lue par Google Play services au demarrage
 * (meta-data OPTIONS_PROVIDER_CLASS_NAME du AndroidManifest).
 *
 * Choix documentes :
 * - receiver : "styled media receiver" public (CC1AD845, constante SDK), aucune cle,
 *   aucun recepteur lie a un compte Google ; l'application diffuse des URL https
 *   directes et non des identifiants YouTube.
 * - notification Cast conservee (delais par defaut) avec cible explicite : la
 *   lecture restee en pause cote telephone est controlee depuis le lock screen.
 * - reconnexion et reprise automatique de session desactives : pas de service
 *   au premier plan de reconnexion, pas de reprise de lecture inattendue.
 * - l'application n'accepte pas d'etre cible de transfert (remote to local) et
 *   n'utilise pas le system output switcher : la selection de route se fait
 *   exclusivement via le route picker interne.
 * - sortie du recepteur quand l'utilisateur arrete le cast : pas d'etat
 *   residuel affiche sur l'ecran du recepteur.
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setCastMediaOptions(
                CastMediaOptions.Builder()
                    .setNotificationOptions(
                        NotificationOptions.Builder()
                            .setTargetActivityClassName(MainActivity::class.java.name)
                            .build()
                    )
                    .build()
            )
            .setEnableReconnectionService(false)
            .setResumeSavedSession(false)
            .setStopReceiverApplicationWhenEndingSession(true)
            .setRemoteToLocalEnabled(false)
            .setShowSystemOutputSwitcherOnCastIconClick(false)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
