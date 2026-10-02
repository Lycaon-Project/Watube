package com.watube.yard.extensions

import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import android.util.Size
import android.util.SizeF
import androidx.core.os.BundleCompat
import java.io.Serializable

inline fun <reified T : Parcelable> Bundle.parcelable(key: String?): T? {
    return BundleCompat.getParcelable(this, key, T::class.java)
}

inline fun <reified T : Parcelable> Bundle.parcelableList(key: String?): ArrayList<T>? {
    return BundleCompat.getParcelableArrayList(this, key, T::class.java)
}

inline fun <reified T : Parcelable> Bundle.parcelableArrayList(key: String?): ArrayList<T>? {
    return BundleCompat.getParcelableArrayList(this, key, T::class.java)
}

inline fun <reified T : Serializable> Bundle.serializable(key: String?): T? {
    return BundleCompat.getSerializable(this, key, T::class.java)
}

/**
 * Crée un [Bundle] à partir de paires clé/valeur.
 *
 * `androidx.core.os.bundleOf` est désormais déprécié (absence de vérification de type à la
 * compilation). Ré-écrire les ~60 appels de l'application en `Bundle().apply { putX() }` aurait
 * alourdi le code et — surtout — risqué de casser l'appariement put/get au runtime pour les
 * types à la fois `Parcelable` et `Serializable` (ex. [com.watube.yard.api.obj.StreamItem]) :
 * un mauvais choix de `putX` compile mais plante à la lecture.
 *
 * Ce remplaçant local reproduit **exactement** la logique d'aiguillage par type d'androidx, donc
 * le comportement reste identique au runtime. L'ordre des branches est important : `Parcelable`
 * est testé avant `Serializable` (un type qui implémente les deux est stocké comme Parcelable),
 * et `Array<*>` avant `Serializable` (tous les tableaux étant sérialisables).
 *
 * minSdk = 28 : `putBinder`, `putSize` et `putSizeF` sont disponibles sans garde de version.
 */
fun bundleOf(vararg pairs: Pair<String, Any?>): Bundle = Bundle(pairs.size).apply {
    for ((key, value) in pairs) {
        when (value) {
            null -> putString(key, null) // n'importe quel type nullable convient

            // Scalaires
            is Boolean -> putBoolean(key, value)
            is Byte -> putByte(key, value)
            is Char -> putChar(key, value)
            is Double -> putDouble(key, value)
            is Float -> putFloat(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Short -> putShort(key, value)

            // Références
            is Bundle -> putBundle(key, value)
            is CharSequence -> putCharSequence(key, value)
            is Parcelable -> putParcelable(key, value)

            // Tableaux de scalaires
            is BooleanArray -> putBooleanArray(key, value)
            is ByteArray -> putByteArray(key, value)
            is CharArray -> putCharArray(key, value)
            is DoubleArray -> putDoubleArray(key, value)
            is FloatArray -> putFloatArray(key, value)
            is IntArray -> putIntArray(key, value)
            is LongArray -> putLongArray(key, value)
            is ShortArray -> putShortArray(key, value)

            // Tableaux de références
            is Array<*> -> {
                val componentType = value::class.java.componentType!!
                @Suppress("UNCHECKED_CAST") // vérifié par réflexion
                when {
                    Parcelable::class.java.isAssignableFrom(componentType) -> {
                        putParcelableArray(key, value as Array<Parcelable>)
                    }

                    String::class.java.isAssignableFrom(componentType) -> {
                        putStringArray(key, value as Array<String>)
                    }

                    CharSequence::class.java.isAssignableFrom(componentType) -> {
                        putCharSequenceArray(key, value as Array<CharSequence>)
                    }

                    Serializable::class.java.isAssignableFrom(componentType) -> {
                        putSerializable(key, value)
                    }

                    else -> {
                        val valueType = componentType.canonicalName
                        throw IllegalArgumentException(
                            "Illegal value array type $valueType for key \"$key\""
                        )
                    }
                }
            }

            // Dernier recours : tester après Array<*> car tous les tableaux sont sérialisables
            is Serializable -> putSerializable(key, value)

            is IBinder -> putBinder(key, value)
            is Size -> putSize(key, value)
            is SizeF -> putSizeF(key, value)

            else -> {
                val valueType = value.javaClass.canonicalName
                throw IllegalArgumentException("Illegal value type $valueType for key \"$key\"")
            }
        }
    }
}
