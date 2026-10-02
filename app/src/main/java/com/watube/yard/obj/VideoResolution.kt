package com.watube.yard.obj

import kotlin.math.abs

data class VideoResolution(
    val name: String,
    val resolution: Int
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

        /**
         * Libellé de qualité façon YouTube : palier standard + annotation 4K/HD
         * (ex. « 2160p 4K », « 1080p HD », « 720p »).
         */
        fun qualityLabel(height: Int): String {
            val tier = snapToStandardHeight(height)
            val suffix = when {
                tier >= 2160 -> " 4K"
                tier >= 1080 -> " HD"
                else -> ""
            }
            return "${tier}p$suffix"
        }
    }
}
