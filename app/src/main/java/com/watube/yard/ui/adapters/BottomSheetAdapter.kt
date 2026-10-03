package com.watube.yard.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.databinding.BottomSheetItemBinding
import com.watube.yard.obj.BottomSheetItem
import com.watube.yard.ui.extensions.setDrawables
import com.watube.yard.ui.viewholders.BottomSheetViewHolder
import com.watube.yard.ui.views.BadgeSpan

class BottomSheetAdapter(
    private val items: List<BottomSheetItem>,
    private val listener: (index: Int) -> Unit
) : RecyclerView.Adapter<BottomSheetViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BottomSheetViewHolder {
        val binding = BottomSheetItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BottomSheetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BottomSheetViewHolder, position: Int) {
        val item = items[position]
        holder.binding.root.apply {
            val current = item.getCurrent()
            text = buildSpannedString {
                append(if (current != null) "${item.title} ($current)" else item.title)
                item.badge?.let { inSpans(BadgeSpan(context)) { append(it) } }
            }
            isSelected = item.isSelected
            setDrawables(start = item.drawable)

            setOnClickListener {
                item.onClick.invoke()
                listener.invoke(position)
            }
        }
    }

    override fun getItemCount() = items.size
}
