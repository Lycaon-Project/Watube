package com.watube.yard.ui.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import com.watube.yard.databinding.ActivityMissionBinding
import com.watube.yard.helpers.IntentHelper
import com.watube.yard.helpers.PrivacyHelper
import com.watube.yard.ui.base.BaseActivity

/**
 * "Pourquoi Watube": renders the local `mission/Mission_Watube.html` page.
 *
 * The page needs JavaScript: every translated block is empty in the source and
 * filled at runtime (`data-i18n` / `data-i18n-list`), and the language / theme
 * switchers are scripts. Nothing else leaves the APK: http(s) links are handed
 * to [IntentHelper] and every other navigation is blocked.
 */
class MissionActivity : BaseActivity() {
    private lateinit var binding: ActivityMissionBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = TITLE
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupWebView()
        binding.missionWebview.loadUrl(MISSION_URL)

        onBackPressedDispatcher.addCallback(this) {
            if (binding.missionWebview.canGoBack()) {
                binding.missionWebview.goBack()
            } else {
                finish()
            }
        }
    }

    private fun setupWebView() {
        binding.missionWebview.apply {
            settings.javaScriptEnabled = true
            // keeps the page theme / language between two openings
            settings.domStorageEnabled = true
            // local asset only: no remote resource may be fetched
            settings.blockNetworkLoads = true
            settings.blockNetworkImage = true
            // Hardening: the page only needs bundled assets, so deny every other data
            // source a file:// document could otherwise reach (arbitrary files, content://
            // providers and cross-origin file access are all turned off).
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = false

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean = handleUrl(request.url.toString())
            }

            PrivacyHelper.applyWebViewAntiFingerprinting(this)
        }
    }

    /**
     * @return true when the navigation is handled here (opened outside or blocked).
     */
    private fun handleUrl(url: String): Boolean {
        // same document: in page anchors (table of contents)
        if (url.startsWith(MISSION_URL)) return false

        if (IntentHelper.isAllowedLink(url)) {
            IntentHelper.openLinkFromHref(this, supportFragmentManager, url)
        }
        // anything else (javascript:, intent:, unknown hosts) never reaches the WebView
        return true
    }

    companion object {
        private const val MISSION_URL = "file:///android_asset/mission/Mission_Watube.html"
        private const val TITLE = "Pourquoi Watube"
    }
}
