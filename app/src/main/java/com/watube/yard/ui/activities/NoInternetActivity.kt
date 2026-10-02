package com.watube.yard.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import com.watube.yard.R
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.ActivityNointernetBinding
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.ui.extensions.onSystemInsets

class NoInternetActivity : AbstractPlayerHostActivity() {
    private lateinit var binding: ActivityNointernetBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityNointernetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        // same chrome as MainActivity: the activity label is not a toolbar title, the
        // offline message already sits in fragment_nointernet.xml
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // add padding to fragment containers to prevent overlap with edge-to-edge status bars
        binding.root.onSystemInsets { _, systemBarInsets ->
            with (binding.mainLayout) {
                setPadding(paddingLeft, systemBarInsets.top, paddingRight, systemBarInsets.bottom)
            }
            with (binding.container) {
                setPadding(paddingLeft, paddingTop, paddingRight, systemBarInsets.bottom)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.getBooleanExtra(IntentData.maximizePlayer, false)) {
            NavigationHelper.openAudioPlayerFragment(this, offlinePlayer = true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Only the kebab is offered here: the offline screen used to host a toolbar
        // search that filtered the downloads list, and it has no text input anymore.
        menuInflater.inflate(R.menu.action_bar, menu)

        return super.onCreateOptionsMenu(menu)
    }

    // all these actions are no-ops for now because we don't have a navigation bar here
    override fun minimizePlayerContainerLayout() {}

    override fun maximizePlayerContainerLayout() {}

    override fun setPlayerContainerProgress(progress: Float) {}

    // no text input lives on this screen, so there is never a focus to clear
    override fun clearSearchViewFocus(): Boolean = false
}
