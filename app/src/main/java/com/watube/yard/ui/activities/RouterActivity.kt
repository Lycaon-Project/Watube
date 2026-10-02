package com.watube.yard.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.watube.yard.BuildConfig
import com.watube.yard.extensions.TAG
import com.watube.yard.helpers.IntentHelper
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.ui.base.BaseActivity

class RouterActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // shared text often wraps the link ("Title https://youtu.be/..."): keep the link only
        val uri = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?.let(IntentHelper::sharedTextToUri) ?: intent.data
        if (uri != null) {
            // Start processing the given text, if available. otherwise use the link shared as text
            // to the app.
            handleSendText(uri)
        } else {
            // start app as normal if unknown action, shouldn't be reachable
            NavigationHelper.restartMainActivity(this)
        }
    }

    private fun handleSendText(uri: Uri) {
        // debug only: the shared link can be private (channel, playlist, account...)
        // and must not end up in logcat on a user device
        if (BuildConfig.DEBUG) {
            Log.d(TAG(), uri.toString())
        }

        // the launcher entry is an activity-alias that the icon picker swaps: never assume it
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val intent = IntentHelper.resolveType(launchIntent, uri)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finishAndRemoveTask()
    }
}
