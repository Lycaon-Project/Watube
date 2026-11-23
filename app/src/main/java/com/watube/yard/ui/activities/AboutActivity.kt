package com.watube.yard.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.text.method.LinkMovementMethod
import android.animation.ValueAnimator
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.LayerDrawable
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.watube.yard.ui.base.SecureAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.watube.yard.ui.extensions.padBottomForSystemBars
import com.watube.yard.BuildConfig
import com.watube.yard.R
import com.watube.yard.databinding.ActivityAboutBinding
import com.watube.yard.databinding.SettingsCategoryRowBinding
import com.watube.yard.extensions.toastFromMainThread
import com.watube.yard.helpers.ClipboardHelper
import com.watube.yard.helpers.IntentHelper
import com.watube.yard.helpers.LibraryInfo
import com.watube.yard.helpers.ThemeHelper
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.views.CardGlowTrail
import com.watube.yard.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// region About
class AboutActivity : BaseActivity() {
    private lateinit var binding: ActivityAboutBinding

    /** The accent light running round the version card. */
    private lateinit var headerGlow: CardGlowTrail

    /** Quick taps on the version card so far, for the easter egg. */
    private var versionTaps = 0
    private var lastVersionTap = 0L

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.padBottomForSystemBars()

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Version comes from BuildConfig only: never a literal in the layout.
        val versionText = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        binding.aboutTitle.text = "Watube ${versionLabel()}"
        binding.aboutBuild.text = "Build ${BuildConfig.VERSION_CODE} · GPL-3.0"
        binding.aboutTitle.setOnClickListener {
            ClipboardHelper.save(this, text = versionText)
        }
        binding.aboutLogo.setOnClickListener { shareRepository() }
        binding.checkUpdates.setOnClickListener { checkForUpdates() }

        setupRows()
        setupHeaderMotion(entering = savedInstanceState == null)
        binding.aboutHeader.setOnClickListener { onVersionCardTapped() }
    }

    /**
     * Easter egg: seven quick taps on the version card bring out VireoBoo, the mascot of the
     * release, who waves hello. Nothing is stored or sent; the next taps make it wave again.
     */
    private fun onVersionCardTapped() {
        val now = SystemClock.uptimeMillis()
        versionTaps = if (now - lastVersionTap > TAP_GAP_MS) 1 else versionTaps + 1
        lastVersionTap = now
        if (versionTaps < TAPS_TO_FIND && !binding.aboutMascotRow.isVisible) return
        versionTaps = 0
        showVireoBoo()
    }

    /**
     * VireoBoo pops up on the card, raises its arm and waves, then its bubble speaks. With the
     * system animations off, it simply appears with its bubble.
     */
    private fun showVireoBoo() {
        val animate = ValueAnimator.areAnimatorsEnabled()
        val wave = binding.aboutMascot.drawable as? AnimatedVectorDrawable
        if (wave?.isRunning == true) return
        val row = binding.aboutMascotRow
        if (!row.isVisible) {
            row.isVisible = true
            row.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            if (animate) {
                val density = resources.displayMetrics.density
                binding.aboutMascot.apply {
                    alpha = 0f
                    scaleX = MASCOT_START_SCALE
                    scaleY = MASCOT_START_SCALE
                    translationY = MASCOT_RISE_DP * density
                    animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                        .setDuration(MASCOT_ENTER_MS)
                        .setInterpolator(PathInterpolator(0.16f, 1f, 0.3f, 1f))
                        .start()
                }
                binding.aboutMascotBubble.apply {
                    alpha = 0f
                    translationX = -BUBBLE_SLIDE_DP * density
                    // the words come once the arm is up
                    animate().alpha(1f).translationX(0f)
                        .setStartDelay(BUBBLE_DELAY_MS)
                        .setDuration(BUBBLE_ENTER_MS)
                        .setInterpolator(PathInterpolator(0.16f, 1f, 0.3f, 1f))
                        .start()
                }
            }
        }
        if (animate) wave?.start()
    }

    /**
     * The version card, discreet and alive: it rises into place as the screen opens, then a
     * faint light in the accent colour keeps running round its edge. The card keeps its
     * colours; nothing moves while the system animations are off.
     */
    private fun setupHeaderMotion(entering: Boolean) {
        val header = binding.aboutHeader
        headerGlow = CardGlowTrail(
            density = resources.displayMetrics.density,
            accent = ThemeHelper.getThemeColor(this, androidx.appcompat.R.attr.colorPrimary),
            cornerRadius = resources.getDimension(R.dimen.watube_card_radius),
        )
        // on the card's background, under its content
        header.background = LayerDrawable(arrayOf(header.background, headerGlow))
        // the halo stays inside the card's rounded edge
        header.clipToOutline = true
        if (!entering || !ValueAnimator.areAnimatorsEnabled()) return
        header.alpha = 0f
        header.translationY = HEADER_RISE_DP * resources.displayMetrics.density
        header.scaleX = HEADER_START_SCALE
        header.scaleY = HEADER_START_SCALE
        header.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(HEADER_ENTER_MS)
            .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
            .start()
    }

    override fun onResume() {
        super.onResume()
        headerGlow.start(powerSave = getSystemService<PowerManager>()?.isPowerSaveMode == true)
    }

    override fun onPause() {
        headerGlow.stop()
        super.onPause()
    }

    private fun setupRows() {
        // --- Mockup rows -----------------------------------------------------
        setupRow(
            binding.rowCodeSource,
            R.drawable.watube_ic_code,
            title = R.string.about_code_source,
            summary = SUMMARY_CODE_SOURCE,
            external = true,
            copyHref = GITHUB_URL
        ) { openLink(GITHUB_URL) }

        setupRow(
            binding.rowLibraries,
            R.drawable.watube_info,
            title = R.string.open_source_licenses
        ) { showLibraries() }

        setupRow(
            binding.rowPrivacy,
            R.drawable.ic_shield,
            title = R.string.about_privacy
        ) { showPrivacyPolicy() }

        setupRow(
            binding.rowReport,
            R.drawable.watube_ic_flag,
            title = R.string.about_report_issue,
            external = true,
            copyHref = REPORT_URL
        ) { openLink(REPORT_URL) }

        setupRow(
            binding.rowMission,
            R.drawable.watube_logo,
            title = R.string.about_mission
        ) { startActivity(Intent(this, MissionActivity::class.java)) }

        // --- Legacy entries, same behaviour as before ------------------------
        setupRow(
            binding.rowPiped,
            R.drawable.ic_piped,
            title = R.string.piped,
            external = true,
            copyHref = PIPED_GITHUB_URL
        ) { openLink(PIPED_GITHUB_URL) }

        setupRow(
            binding.rowTranslate,
            R.drawable.ic_weblate,
            title = R.string.translate,
            external = true,
            copyHref = WEBLATE_URL
        ) { openLink(WEBLATE_URL) }

        setupRow(binding.rowFeatures, R.drawable.ic_awesome, title = R.string.watube_features) {
            showFeatures()
        }

        setupRow(
            binding.rowLicense,
            R.drawable.ic_license,
            title = R.string.license,
            copyHref = LICENSE_URL
        ) { showLicense() }

        setupRow(binding.rowDevice, R.drawable.ic_device, title = R.string.device_info) {
            showDeviceInfo()
        }
    }

    private fun setupRow(
        row: SettingsCategoryRowBinding,
        @DrawableRes icon: Int,
        @StringRes title: Int = 0,
        titleText: String? = null,
        summary: String? = null,
        external: Boolean = false,
        copyHref: String? = null,
        onClick: () -> Unit = {}
    ) {
        row.rowIcon.setImageResource(icon)
        if (title != 0) {
            row.rowTitle.setText(title)
        } else {
            row.rowTitle.text = titleText
        }

        if (summary != null) {
            row.rowSummary.text = summary
        } else {
            row.rowSummary.visibility = View.GONE
        }

        if (external) row.rowChevron.setImageResource(R.drawable.watube_ic_ext)

        row.root.setOnClickListener { onClick() }
        if (copyHref != null) {
            row.root.setOnLongClickListener {
                onLongClick(copyHref)
                true
            }
        }
    }

    private fun openLink(link: String) {
        IntentHelper.openLinkFromHref(this, supportFragmentManager, link)
    }

    private fun shareRepository() {
        val sendIntent = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_TEXT, GITHUB_URL)
            .setType("text/plain")

        startActivity(Intent.createChooser(sendIntent, null))
    }

    private fun onLongClick(href: String) {
        // copy the link to the clipboard
        ClipboardHelper.save(this, text = href)
        // show the snackBar with open action
        Snackbar.make(
            binding.root,
            R.string.copied_to_clipboard,
            Snackbar.LENGTH_LONG
        )
            .setAction(R.string.open_copied) {
                openLink(href)
            }
            .setAnimationMode(Snackbar.ANIMATION_MODE_FADE)
            .show()
    }

    /**
     * Manual update check. The checker reports its result in a snackbar of the screen (the
     * system toast of some phones, drawn by Android, carries an odd shadow) or opens the update
     * dialog; the button only shows that a check is running.
     */
    private fun checkForUpdates() {
        binding.checkUpdates.isEnabled = false
        binding.checkUpdates.setText(R.string.checking_for_updates)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    UpdateChecker(this@AboutActivity, ::showCheckResult).checkUpdate(true)
                }
            } finally {
                binding.checkUpdates.setText(R.string.update_summary)
                binding.checkUpdates.isEnabled = true
            }
        }
    }

    private suspend fun showCheckResult(@StringRes message: Int) = withContext(Dispatchers.Main) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAnimationMode(Snackbar.ANIMATION_MODE_FADE)
            .show()
    }

    private fun showLicense() {
        val licenseHtml = assets.open("gpl3.html")
            .bufferedReader()
            .use { it.readText() }
            .parseAsHtml(HtmlCompat.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH)

        SecureAlertDialogBuilder(this)
            .setPositiveButton(getString(R.string.okay)) { _, _ -> }
            .setMessage(licenseHtml)
            .create()
            .show()
    }

    /**
     * Privacy policy: no accessor existed, so the Markdown source is shipped as an
     * asset and rendered here with a minimal converter (headings, bold, links).
     */
    private fun showPrivacyPolicy() {
        val markdown = runCatching {
            assets.open(PRIVACY_ASSET).bufferedReader().use { it.readText() }
        }.getOrElse {
            toastFromMainThread(R.string.error)
            return
        }

        val message = markdownToHtml(markdown)
            .parseAsHtml(HtmlCompat.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH)

        val dialog = SecureAlertDialogBuilder(this)
            .setTitle(R.string.about_privacy)
            .setMessage(message)
            .setPositiveButton(R.string.okay) { _, _ -> }
            .create()
        dialog.show()
        // links are clickable, they leave the app through the system browser
        dialog.findViewById<TextView>(android.R.id.message)?.movementMethod =
            LinkMovementMethod.getInstance()
    }

    private fun markdownToHtml(markdown: String): String =
        markdown.lineSequence()
            // the policy header embeds an image of the repository, absent from the APK
            .filterNot { it.trimStart().startsWith("<") }
            .joinToString("\n") { line ->
                when {
                    line.startsWith("### ") -> "<h4>${inlineMarkdown(line.substring(4))}</h4>"
                    line.startsWith("## ") -> "<h3>${inlineMarkdown(line.substring(3))}</h3>"
                    line.startsWith("* ") -> "• ${inlineMarkdown(line.substring(2))}"
                    else -> inlineMarkdown(line)
                }
            }

    private fun inlineMarkdown(text: String): String {
        var out = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        out = LINK_REGEX.replace(out) { match ->
            val label = match.groupValues[1]
            val target = match.groupValues[2]
            // in-page anchors have no target outside the dialog
            if (target.startsWith("#")) label else "<a href=\"$target\">$label</a>"
        }
        return BOLD_REGEX.replace(out) { "<b>${it.groupValues[1]}</b>" }
    }

    private fun showLibraries() {
        val entries = LibraryInfo.libraries

        SecureAlertDialogBuilder(this)
            .setTitle(R.string.open_source_licenses)
            .setMessage(R.string.open_source_licenses_message)
            .setItems(entries.map { it.label }.toTypedArray()) { _, which ->
                entries.getOrNull(which)?.let { library ->
                    openLink(library.licenseUrl)
                }
            }
            .setPositiveButton(R.string.okay) { _, _ -> }
            .create()
            .show()
    }

    private fun showFeatures() {
        SecureAlertDialogBuilder(this)
            .setTitle(R.string.watube_features)
            .setMessage(R.string.watube_features_message)
            .setPositiveButton(R.string.okay) { _, _ -> }
            .create()
            .show()
    }

    private fun showDeviceInfo() {
        val metrics = Resources.getSystem().displayMetrics

        val text = "Manufacturer: ${Build.MANUFACTURER}\n" +
                "Board: ${Build.BOARD}\n" +
                "Arch: ${Build.SUPPORTED_ABIS[0]}\n" +
                "Android SDK: ${Build.VERSION.SDK_INT}\n" +
                "OS: Android ${Build.VERSION.RELEASE}\n" +
                "Display: ${metrics.widthPixels}x${metrics.heightPixels}\n" +
                "Font scale: ${Resources.getSystem().configuration.fontScale}"

        SecureAlertDialogBuilder(this)
            .setTitle(R.string.device_info)
            .setMessage(text)
            .setNegativeButton(R.string.copy_tooltip) { _, _ ->
                ClipboardHelper.save(this@AboutActivity, text = text)
            }
            .setPositiveButton(R.string.okay, null)
            .show()
    }

    companion object {
        /** Entrance of the version card: a short rise, from a hair smaller, settling softly. */
        private const val HEADER_ENTER_MS = 520L
        private const val HEADER_RISE_DP = 10f
        private const val HEADER_START_SCALE = 0.985f

        /** Easter egg: taps needed, at most this far apart, to bring VireoBoo out. */
        private const val TAPS_TO_FIND = 7
        private const val TAP_GAP_MS = 1_000L

        /** VireoBoo's entrance, then its bubble once the arm is raised (320 ms). */
        private const val MASCOT_ENTER_MS = 420L
        private const val MASCOT_RISE_DP = 12f
        private const val MASCOT_START_SCALE = 0.7f
        private const val BUBBLE_DELAY_MS = 320L
        private const val BUBBLE_ENTER_MS = 260L
        private const val BUBBLE_SLIDE_DP = 8f

        /**
         * The version as shown to people: "26.12 · Vireo", the number then the name of the
         * release's mascot. versionName stays the number alone, for the update check.
         */
        fun versionLabel(): String = listOf(BuildConfig.VERSION_NAME, BuildConfig.RELEASE_NAME)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        const val GITHUB_URL = "https://github.com/Lycaon-Project/Watube"
        private const val PIPED_GITHUB_URL = "https://github.com/TeamPiped/Piped"
        private const val WEBLATE_URL = "https://hosted.weblate.org/projects/libretube/libretube/"
        private const val LICENSE_URL = "https://gnu.org/"
        private const val REPORT_URL = "https://github.com/Lycaon-Project/Watube/issues/new"
        private const val PRIVACY_ASSET = "privacy/PRIVACY_POLICY.md"

        private val LINK_REGEX = Regex("""\[([^\]]+)]\(([^)]+)\)""")
        private val BOLD_REGEX = Regex("""\*\*([^*]+)\*\*""")

        // Not a translated label: repository slug shown as the row summary.
        private const val SUMMARY_CODE_SOURCE = "Lycaon-Project/Watube"
    }
}
// endregion
