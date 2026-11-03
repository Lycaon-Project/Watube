package com.watube.yard.extensions

import android.net.Uri
import androidx.core.net.toUri
import java.nio.file.Path

// region Common Utilities
fun Path.toAndroidUri(): Uri {
    return toFile().toUri()
}
// endregion
