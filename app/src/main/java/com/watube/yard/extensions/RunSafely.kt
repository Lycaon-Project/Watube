package com.watube.yard.extensions

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// region Common Utilities
suspend fun <T> runSafely(
    onSuccess: (List<T>) -> Unit = { },
    ioBlock: suspend () -> List<T>
) {
    withContext(Dispatchers.IO) {
        // a failed section only stays hidden; the cause goes to the developer log (stripped from
        // release builds by R8)
        val result = runCatching { ioBlock.invoke() }
            .onFailure { Log.w("runSafely", "section failed to load", it) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: return@withContext

        withContext(Dispatchers.Main) {
            if (result.isNotEmpty()) {
                onSuccess.invoke(result)
            }
        }
    }
}
// endregion
