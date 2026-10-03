package com.watube.yard.obj

import kotlin.math.abs

data class VideoResolution(
    val name: String,
    val resolution: Int,
    /** HD / FHD / 2K / 4K marker shown as a pill next to [name], null below HD */
    val badge: String? = null
) {
    companion object {
        // Paliers de qualité YouTube standard (hauteur en px), du plus haut au plus bas.
        private val STANDARD_TIERS = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)

        /**
         * Ramène la hauteur réelle d'une piste au palier YouTube standard le plus proche.
         * Les flux pas exactement en 16:9 renvoient parfois des hauteurs non rondes
         * (2026, 1012, …) : on les aligne sur l'échelle officielle.
         */
        fun snapToStandardHeight(height: Int): Int =
            STANDARD_TIERS.minByOrNull { abs(it - height) } ?: height

        /** Libellé de qualité façon YouTube : palier standard (ex. « 2160p », « 720p »). */
        fun qualityLabel(height: Int): String = "${snapToStandardHeight(height)}p"

        /** Pastille de définition : 4K (2160p+), 2K (1440p), FHD (1080p), HD (720p), sinon aucune. */
        fun qualityBadge(height: Int): String? = when (snapToStandardHeight(height)) {
            2160 -> "4K"
            1440 -> "2K"
            1080 -> "FHD"
            720 -> "HD"
            else -> null
        }

        fun fromHeight(height: Int) =
            VideoResolution(qualityLabel(height), height, qualityBadge(height))
    }
}
