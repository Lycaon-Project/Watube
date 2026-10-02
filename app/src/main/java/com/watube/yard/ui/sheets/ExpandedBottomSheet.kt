package com.watube.yard.ui.sheets

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.PEEK_HEIGHT_AUTO
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.watube.yard.ui.extensions.toggleSystemBars
import com.watube.yard.ui.models.CommonPlayerViewModel

open class ExpandedBottomSheet(@LayoutRes layoutResId: Int) :
    BottomSheetDialogFragment(layoutResId) {
    private val bottomSheet: FrameLayout? get() = dialog?.findViewById(R.id.design_bottom_sheet)
    private val playerViewModel: CommonPlayerViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        // Landscape opens fully expanded. The state is set before the dialog is shown so the
        // sheet slides straight to it: expanding from a show listener started a second
        // animation right after the collapsed one, which read as the sheet shaking.
        if (resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT) {
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        return dialog
    }

    override fun onStart() {
        // Over the fullscreen player the sheet is a window of its own: unless it hides the
        // system bars as well, taking the focus brings them back, and every inset change
        // (bars shown, then hidden again by the player controller) shifts the sheet.
        if (playerViewModel.isFullscreen.value == true) {
            dialog?.window?.toggleSystemBars(WindowInsetsCompat.Type.systemBars(), showBars = false)
        }
        super.onStart()
    }

    fun show(fragmentManager: FragmentManager) = show(fragmentManager, null)

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        // ensure that the sheet doesn't hide parts of the video
        dialog?.dismiss()
    }

    fun expand(collapse: Boolean = false) {
        bottomSheet?.let { fl ->
            val bottomSheetInfoBehavior = BottomSheetBehavior.from(fl)
            if (collapse) {
                bottomSheetInfoBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                bottomSheetInfoBehavior.setPeekHeight(0, true)
            } else {
                bottomSheetInfoBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                bottomSheetInfoBehavior.setPeekHeight(PEEK_HEIGHT_AUTO, true)
            }
        }
    }
}
