package com.watube.yard.extensions

import android.net.Uri
import androidx.core.net.toUri
import java.nio.file.Path

fun Path.toAndroidUri(): Uri {
    return toFile().toUri()
}
