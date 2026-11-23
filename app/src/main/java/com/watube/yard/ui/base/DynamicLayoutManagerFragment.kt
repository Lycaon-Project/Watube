package com.watube.yard.ui.base

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.watube.yard.helpers.GridColumns

// region UI Components
abstract class DynamicLayoutManagerFragment(@LayoutRes layoutResId: Int) : Fragment(layoutResId) {
    abstract fun setLayoutManagers(gridItems: Int)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        setLayoutManagers(GridColumns.of(newConfig))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setLayoutManagers(GridColumns.of(resources.configuration))
    }
}
// endregion
