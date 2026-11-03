package com.watube.yard.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.watube.yard.R
import com.watube.yard.databinding.ViewAppLockBinding
import com.watube.yard.helpers.AppLock
import com.watube.yard.helpers.ScreenPrivacy
import com.watube.yard.helpers.ThemeHelper

// region Privacy
/**
 * The lock screen of [AppLock]: a screen of its own on top of the app, so that nothing below
 * can be reached (back, dialogs, accessibility) until the user unlocks. Back sends the app to
 * the background instead of revealing anything.
 */
class AppLockActivity : AppCompatActivity() {
    private lateinit var prompt: BiometricPrompt
    private var prompting = false

    /** the prompt opens by itself once per showing, the button asks again after a cancel */
    private var promptedSinceStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.updateTheme(this)
        super.onCreate(savedInstanceState)
        ScreenPrivacy.applyTo(window)
        ScreenPrivacy.applyRecentsPreviewTo(this)

        val binding = ViewAppLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.appLockUnlock.setOnClickListener { authenticate() }

        // created here, as BiometricPrompt requires, and reused for every attempt
        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
    }

    override fun onStart() {
        super.onStart()
        AppLock.onLockScreenStarted()
        promptedSinceStart = false
    }

    // brought back on top (singleTop) without being stopped: ask again by itself
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        promptedSinceStart = false
    }

    override fun onResume() {
        super.onResume()
        if (!AppLock.isLocked) {
            finishUnlocked()
        } else if (!promptedSinceStart) {
            promptedSinceStart = true
            // a prompt asked while the previous one is still closing is ignored by the
            // library: wait for it to be gone
            window.decorView.postDelayed({
                if (!isFinishing && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    authenticate()
                }
            }, PROMPT_DELAY_MS)
        }
    }

    override fun onStop() {
        // the system drops the prompt when the app leaves, without always calling back: end
        // the attempt here so that the next showing can ask again
        if (prompting) prompt.cancelAuthentication()
        prompting = false
        AppLock.onLockScreenStopped(this)
        super.onStop()
    }

    private val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            prompting = false
            AppLock.onUnlocked()
            finishUnlocked()
        }

        // cancelled or failed for good: the lock screen stays, its button asks again
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            prompting = false
        }
    }

    private fun authenticate() {
        if (prompting) return
        prompting = true
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.app_lock_prompt_title))
                .setSubtitle(getString(R.string.app_lock_prompt_subtitle))
                .setAllowedAuthenticators(AppLock.AUTHENTICATORS)
                .build()
        )
    }

    private fun finishUnlocked() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private companion object {
        const val PROMPT_DELAY_MS = 300L
    }
}
// endregion
