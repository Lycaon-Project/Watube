package com.watube.yard.ui.activities

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.watube.yard.R
import com.watube.yard.databinding.ActivitySettingsBinding
import com.watube.yard.ui.base.BaseActivity

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        val navController = binding.settings.getFragment<NavHostFragment>().navController
        setSupportActionBar(binding.toolbar)
        setContentView(binding.root)

        // ensure that the toolbar's back button is always visible
        val appBarConfiguration = AppBarConfiguration.Builder()
            .setFallbackOnNavigateUpListener {
                finish()
                true
            }
            .build()
        binding.toolbar.setupWithNavController(navController, appBarConfiguration)

        // the hub wears the mockup .shd h1, a settings pane wears .phead h2
        val titleAppearanceListener =
            NavController.OnDestinationChangedListener { _, destination, _ ->
            val titleAppearance = if (destination.id == R.id.mainSettings) {
                R.style.WatubeScreenTitle
            } else {
                R.style.WatubePaneTitle
            }
            binding.toolbar.setTitleTextAppearance(this, titleAppearance)
        }
        navController.addOnDestinationChangedListener(titleAppearanceListener)

        if (intent.extras?.getString(REDIRECT_KEY) == REDIRECT_TO_INTENT_SETTINGS) {
            navController.navigate(R.id.action_global_instanceSettings)
        }
    }

    companion object {
        const val REDIRECT_KEY = "redirect"
        const val REDIRECT_TO_INTENT_SETTINGS = "intent_settings"
    }
}
