package com.watube.yard.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.databinding.ItemDownloadQualityBinding
import com.watube.yard.ui.views.DownloadMotion

// region Downloads
/** One card of the download sheets: the quality, its pill (HD, FHD...), then the rest. */
data class QualityOption(val title: String, val badge: String?, val detail: String?)

/**
 * The quality cards of the download sheets, two per row, exactly one of them chosen
 * ([selected]); [onPick] hears each new choice.
 */
class DownloadQualityAdapter(
    private val onPick: (Int) -> Unit
) : RecyclerView.Adapter<DownloadQualityAdapter.QualityViewHolder>() {
    private var options: List<QualityOption> = emptyList()

    var selected: Int = RecyclerView.NO_POSITION
        private set

    class QualityViewHolder(val binding: ItemDownloadQualityBinding) :
        RecyclerView.ViewHolder(binding.root)

    fun submit(newOptions: List<QualityOption>, selectedIndex: Int) {
        options = newOptions
        selected = selectedIndex.takeIf { it in newOptions.indices } ?: RecyclerView.NO_POSITION
        @Suppress("NotifyDataSetChanged") // a new list each time (tab, codec or track changed)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QualityViewHolder {
        val binding = ItemDownloadQualityBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return QualityViewHolder(binding).also { holder ->
            binding.root.setOnClickListener {
                val position = holder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION || position == selected) return@setOnClickListener
                val previous = selected
                selected = position
                notifyItemChanged(previous, PAYLOAD_CHECKED)
                notifyItemChanged(position, PAYLOAD_CHECKED)
                DownloadMotion.press(binding.root)
                onPick(position)
            }
        }
    }

    override fun onBindViewHolder(holder: QualityViewHolder, position: Int) {
        val option = options[position]
        with(holder.binding) {
            qualityTitle.text = option.title
            qualityBadge.text = option.badge
            qualityBadge.isVisible = option.badge != null
            qualityDetail.text = option.detail
            qualityDetail.isVisible = !option.detail.isNullOrEmpty()
            qualityCard.isChecked = position == selected
        }
    }

    override fun onBindViewHolder(holder: QualityViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.contains(PAYLOAD_CHECKED)) {
            holder.binding.qualityCard.isChecked = position == selected
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun getItemCount() = options.size

    private companion object {
        const val PAYLOAD_CHECKED = "checked"
    }
}
// endregion
