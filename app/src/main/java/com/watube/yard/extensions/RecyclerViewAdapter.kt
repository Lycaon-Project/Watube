package com.watube.yard.extensions

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.R
import com.watube.yard.helpers.ThemeHelper

// region UI Components
fun RecyclerView.setOnDismissListener(onDismissedListener: (position: Int) -> Unit) {
    setActionListener(
        allowSwipe = true,
        onDismissedListener = onDismissedListener
    )
}

/** @param isMovable whether the item at a position can be dragged, or have another one dropped on it */
fun RecyclerView.setOnDraggedListener(
    isMovable: (position: Int) -> Boolean = { true },
    onDragListener: (from: Int, to: Int) -> Unit
) {
    setActionListener(
        allowDrag = true,
        isMovable = isMovable,
        onDragListener = onDragListener
    )
}

fun RecyclerView.setActionListener(
    allowSwipe: Boolean = false,
    allowDrag: Boolean = false,
    isMovable: (position: Int) -> Boolean = { true },
    onDragListener: (from: Int, to: Int) -> Unit = { _, _ -> },
    onDismissedListener: (position: Int) -> Unit = {}
) {
    val itemTouchCallback =
        object : ItemTouchHelper.SimpleCallback(
            if (allowDrag) ItemTouchHelper.UP or ItemTouchHelper.DOWN else 0,
            if (allowSwipe) ItemTouchHelper.LEFT else 0
        ) {
            override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) =
                if (isMovable(viewHolder.absoluteAdapterPosition)) super.getDragDirs(recyclerView, viewHolder) else 0

            override fun canDropOver(
                recyclerView: RecyclerView,
                current: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = isMovable(target.absoluteAdapterPosition)

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                if (!allowDrag) return false

                onDragListener.invoke(viewHolder.absoluteAdapterPosition, target.absoluteAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                onDismissedListener.invoke(viewHolder.absoluteAdapterPosition)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView

                if (dX == 0f && !isCurrentlyActive) {
                    clearCanvas(c, itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, false)
                    return
                }

                // Get the background colors from the theme (should be a tone of red for most themes)
                val backgroundColor = ThemeHelper.getThemeColor(context, androidx.appcompat.R.attr.colorError)
                val onBackgroundColor = ThemeHelper.getThemeColor(context, com.google.android.material.R.attr.colorOnError)

                val itemViewEndWithOffset = itemView.right + dX.toInt()
                // Draw the red delete background
                val background = ColorDrawable().apply {
                    color = backgroundColor
                    setBounds(itemViewEndWithOffset, itemView.top, itemView.right, itemView.bottom)
                }
                background.draw(c)

                val deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_delete)!!.apply {
                    setTint(onBackgroundColor)
                }

                // Calculate position of the delete icon
                val itemHeight = itemView.bottom - itemView.top
                val deleteIconTop = itemView.top + (itemHeight - deleteIcon.intrinsicHeight) / 2
                val deleteIconMargin = (itemHeight - deleteIcon.intrinsicHeight) / 2
                val deleteIconLeft = itemView.right - deleteIconMargin - deleteIcon.intrinsicWidth
                val deleteIconRight = itemView.right - deleteIconMargin
                val deleteIconBottom = deleteIconTop + deleteIcon.intrinsicHeight

                // Draw the delete icon if it wouldn't overlap the recycler view item
                if (itemViewEndWithOffset < deleteIconLeft) {
                    deleteIcon.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom)
                    deleteIcon.draw(c)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            private fun clearCanvas(c: Canvas?, left: Float, top: Float, right: Float, bottom: Float) {
                val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
                c?.drawRect(left, top, right, bottom, clearPaint)
            }
        }

    ItemTouchHelper(itemTouchCallback).attachToRecyclerView(this)
    offerGesturesAsActions(allowSwipe, allowDrag, isMovable, onDragListener, onDismissedListener)
}

/**
 * Swiping a row away and dragging it up or down are touch gestures that a screen reader, a
 * switch or a keyboard cannot make: every row also offers them as actions (TalkBack's actions
 * menu), which do exactly the same. A list set up again replaces its previous actions.
 */
private fun RecyclerView.offerGesturesAsActions(
    allowSwipe: Boolean,
    allowDrag: Boolean,
    isMovable: (position: Int) -> Boolean,
    onDragListener: (from: Int, to: Int) -> Unit,
    onDismissedListener: (position: Int) -> Unit
) {
    if (!allowSwipe && !allowDrag) return
    (getTag(R.id.gesture_actions_listener) as? RecyclerView.OnChildAttachStateChangeListener)
        ?.let(::removeOnChildAttachStateChangeListener)

    val list = this
    fun positionOf(row: View) =
        list.findContainingViewHolder(row)?.absoluteAdapterPosition ?: RecyclerView.NO_POSITION

    /** Where the row at [from] lands when moved [by] one place, if it can move there. */
    fun moveTarget(from: Int, by: Int): Int? {
        val to = from + by
        val count = list.adapter?.itemCount ?: 0
        return to.takeIf {
            from != RecyclerView.NO_POSITION && to in 0 until count && isMovable(from) && isMovable(to)
        }
    }

    val listener = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) = offer(view)

        override fun onChildViewDetachedFromWindow(view: View) = clear(view)

        private fun clear(row: View) {
            (row.getTag(R.id.gesture_action_ids) as? IntArray)
                ?.forEach { ViewCompat.removeAccessibilityAction(row, it) }
            row.setTag(R.id.gesture_action_ids, null)
        }

        /** Offers the actions that apply to [row] where it stands now. */
        fun offer(row: View) {
            clear(row)
            val position = positionOf(row)
            if (position == RecyclerView.NO_POSITION) return
            val ids = buildList<Int> {
                if (allowSwipe) add(addAction(row, R.string.a11y_remove) { removeRow(it) })
                if (allowDrag) {
                    if (moveTarget(position, -1) != null) add(addAction(row, R.string.a11y_move_up) { moveRow(it, -1) })
                    if (moveTarget(position, 1) != null) add(addAction(row, R.string.a11y_move_down) { moveRow(it, 1) })
                }
            }.filter { it != View.NO_ID }
            row.setTag(R.id.gesture_action_ids, ids.toIntArray())
        }

        private fun addAction(row: View, @StringRes label: Int, perform: (View) -> Boolean) =
            ViewCompat.addAccessibilityAction(row, row.context.getString(label)) { view, _ -> perform(view) }

        private fun removeRow(row: View): Boolean {
            val position = positionOf(row)
            if (position == RecyclerView.NO_POSITION) return false
            onDismissedListener(position)
            refreshAll()
            return true
        }

        private fun moveRow(row: View, by: Int): Boolean {
            val from = positionOf(row)
            val to = moveTarget(from, by) ?: return false
            onDragListener(from, to)
            refreshAll()
            return true
        }

        /** The rows around a removed or moved one have other neighbours now. */
        private fun refreshAll() = list.post { list.children.forEach(::offer) }
    }
    addOnChildAttachStateChangeListener(listener)
    setTag(R.id.gesture_actions_listener, listener)
}
// endregion
