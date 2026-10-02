package com.watube.yard.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.watube.yard.BuildConfig
import com.watube.yard.R
import com.watube.yard.databinding.ActivityAboutBinding
import com.watube.yard.databinding.SettingsCategoryRowBinding
import com.watube.yard.extensions.toastFromMainThread
import com.watube.yard.helpers.ClipboardHelper
import com.watube.yard.helpers.IntentHelper
import com.watube.yard.helpers.LibraryInfo
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AboutActivity : BaseActivity() {
    private lateinit var binding: ActivityAboutBinding

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Version comes from BuildConfig only: never a literal in the layout.
        val versionText = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        binding.aboutTitle.text = "Watube ${BuildConfig.VERSION_NAME}"
        binding.aboutBuild.text = "Build ${BuildConfig.VERSION_CODE} · GPL-3.0"
        binding.aboutTitle.setOnClickListener {
            ClipboardHelper.save(this, text = versionText, notify = true)
        }
        binding.aboutLogo.setOnClickListener { shareRepository() }
        binding.checkUpdates.setOnClickListener { checkForUpdates() }

        setupRows()
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
     * Manual update check. The checker already reports its own result (toast or
     * dialog), the button only shows that a check is running.
     */
    private fun checkForUpdates() {
        binding.checkUpdates.isEnabled = false
        binding.checkUpdates.setText(R.string.checking_for_updates)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    UpdateChecker(this@AboutActivity).checkUpdate(true)
                }
            } finally {
                binding.checkUpdates.setText(R.string.update_summary)
                binding.checkUpdates.isEnabled = true
            }
        }
    }

    private fun showLicense() {
        val licenseHtml = assets.open("gpl3.html")
            .bufferedReader()
            .use { it.readText() }
            .parseAsHtml(HtmlCompat.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH)

        MaterialAlertDialogBuilder(this)
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

        val dialog = MaterialAlertDialogBuilder(this)
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

        MaterialAlertDialogBuilder(this)
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
        MaterialAlertDialogBuilder(this)
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

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.device_info)
            .setMessage(text)
            .setNegativeButton(R.string.copy_tooltip) { _, _ ->
                ClipboardHelper.save(this@AboutActivity, text = text)
            }
            .setPositiveButton(R.string.okay, null)
            .show()
    }

    companion object {
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
