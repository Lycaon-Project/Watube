package com.watube.yard.ui.activities

import android.content.Intent
import android.os.Bundle
import android.transition.TransitionManager
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.watube.yard.R
import com.watube.yard.databinding.ActivityHelpBinding
import com.watube.yard.ui.base.BaseActivity

/**
 * Watube help page: a self-contained, account-free guide. It lists the most common
 * questions as expandable cards, a few getting-started tips, and a shortcut to the
 * "Why Watube" page. All content is localised through string resources.
 */
class HelpActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        FAQ.forEach { (question, answer) -> addFaq(binding.faqContainer, question, answer) }
        TIPS.forEach { addTip(binding.tipsContainer, it) }

        binding.whyWatube.setOnClickListener {
            startActivity(Intent(this, MissionActivity::class.java))
        }
    }

    private fun addFaq(container: LinearLayout, @StringRes question: Int, @StringRes answer: Int) {
        val view = layoutInflater.inflate(R.layout.item_faq, container, false)
        val answerView = view.findViewById<TextView>(R.id.faq_answer)
        val chevron = view.findViewById<ImageView>(R.id.faq_chevron)
        view.findViewById<TextView>(R.id.faq_question).setText(question)
        answerView.setText(answer)

        view.setOnClickListener {
            val expand = answerView.visibility != View.VISIBLE
            TransitionManager.beginDelayedTransition(container)
            answerView.visibility = if (expand) View.VISIBLE else View.GONE
            chevron.animate().rotation(if (expand) 180f else 0f).setDuration(200).start()
        }
        container.addView(view)
    }

    private fun addTip(container: LinearLayout, tip: Tip) {
        val view = layoutInflater.inflate(R.layout.item_help_tip, container, false)
        view.findViewById<ImageView>(R.id.tip_icon).setImageResource(tip.icon)
        view.findViewById<TextView>(R.id.tip_title).setText(tip.title)
        view.findViewById<TextView>(R.id.tip_desc).setText(tip.desc)
        container.addView(view)
    }

    private data class Tip(
        @param:DrawableRes val icon: Int,
        @param:StringRes val title: Int,
        @param:StringRes val desc: Int
    )

    companion object {
        private val FAQ = listOf(
            R.string.help_q_account to R.string.help_a_account,
            R.string.help_q_local to R.string.help_a_local,
            R.string.help_q_download to R.string.help_a_download,
            R.string.help_q_sponsorblock to R.string.help_a_sponsorblock,
            R.string.help_q_privacy to R.string.help_a_privacy,
            R.string.help_q_backup to R.string.help_a_backup
        )

        private val TIPS = listOf(
            Tip(R.drawable.ic_search, R.string.help_tip_search_title, R.string.help_tip_search),
            Tip(R.drawable.ic_play, R.string.help_tip_player_title, R.string.help_tip_player),
            Tip(R.drawable.ic_subscriptions, R.string.help_tip_subs_title, R.string.help_tip_subs),
            Tip(R.drawable.ic_library, R.string.help_tip_library_title, R.string.help_tip_library)
        )
    }
}
