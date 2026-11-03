package com.watube.yard.ui.extensions

import androidx.recyclerview.widget.RecyclerView

// region UI Components
fun RecyclerView.addOnBottomReachedListener(onBottomReached: () -> Unit) {
    viewTreeObserver.addOnScrollChangedListener {
        if (!canScrollVertically(1)) onBottomReached()
    }
}
// endregion
