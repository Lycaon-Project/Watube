package com.watube.yard.extensions

import android.util.Log

fun query(block: () -> Unit) {
    Thread {
        try {
            block.invoke()
        } catch (e: Exception) {
            Log.e("Query", "Background query failed", e)
        }
    }.start()
}
