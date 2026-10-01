package com.watube.yard.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.watube.yard.BuildConfig
import com.watube.yard.helpers.PreferenceHelper

class ExceptionHandler(
    private val context: Context,
    private val defaultExceptionHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, exc: Throwable) {
        // OkHttp spawns threads to parse the response headers
        // if an exception is on a thread spawned by OkHttp, there's no apparent other way to catch them
        // work around for Cronet spawning different threads with uncaught Exception when used with Coil
        // for example this catches crashes when there are invalid values in the header
        if (thread.name == OKHTTP_THREAD_NAME) return

        // save the error log
        PreferenceHelper.saveErrorLog(exc.stackTraceToString())
        if (BuildConfig.DEBUG) writeCrashDump(thread, exc)
        // throw the exception with the default exception handler to make the app crash
        defaultExceptionHandler?.uncaughtException(thread, exc)
    }

    /**
     * Debug builds only: publish the stack trace as a text file in the public Downloads
     * collection so it can be retrieved with any file transfer app, without adb access
     * to the app-private error log. Best effort: any failure here must not delay or
     * alter the crash itself.
     */
    private fun writeCrashDump(thread: Thread, exc: Throwable) {
        try {
            val text = buildString {
                appendLine("app=${runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull()}")
                appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")
                appendLine("thread=${thread.name}")
                appendLine(exc.stackTraceToString())
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Watube_crash_${System.currentTimeMillis()}.txt")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(text.toByteArray())
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val OKHTTP_THREAD_NAME = "OkHttp Dispatcher"
    }
}
