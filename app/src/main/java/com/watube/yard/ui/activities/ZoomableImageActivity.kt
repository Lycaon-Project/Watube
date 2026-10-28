package com.watube.yard.ui.activities

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.ActivityZoomableImageBinding
import com.watube.yard.helpers.AppLock
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.ScreenPrivacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// region Image Loading
/**
 * An activity that allows you to zoom and rotate an image
 */
class ZoomableImageActivity : AppCompatActivity() {
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // not a BaseActivity: the privacy settings of the windows are applied here
        ScreenPrivacy.applyTo(window)
        ScreenPrivacy.applyRecentsPreviewTo(this)

        val binding = ActivityZoomableImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch(Dispatchers.IO) {
            val bitmapUrl = intent.getStringExtra(IntentData.bitmapUrl)!!
            val bitmap = ImageHelper.getImage(this@ZoomableImageActivity, bitmapUrl) ?: return@launch

            withContext(Dispatchers.Main) {
                binding.imageView.setImageBitmap(bitmap)
                binding.progress.isGone = true
                binding.imageView.isVisible = true
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onStart(this)
    }

    override fun onStop() {
        AppLock.onStop(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // the settings may have changed while this screen was in the background
        ScreenPrivacy.applyTo(window)
        ScreenPrivacy.applyRecentsPreviewTo(this)
    }
}
// endregion
