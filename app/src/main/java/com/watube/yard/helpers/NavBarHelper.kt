package com.watube.yard.helpers

import android.content.Context
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.PopupMenu
import androidx.core.view.get
import androidx.core.view.isGone
import androidx.core.view.iterator
import androidx.core.view.size
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.ui.dialogs.NavBarItem
import com.google.android.material.navigation.NavigationBarView

object NavBarHelper {

    private const val TAG = "NavBarHelper"
    private const val SEPARATOR = ","

    fun hasTabs(): Boolean {
        val prefsItems = try {
            getNavBarPrefs()
        } catch (e: Exception) {
            Log.e(TAG, "fail to parse nav items", e)
            return true
        }

        val tabsUnchanged = prefsItems.isEmpty()
        val allTabsHidden = prefsItems.isNotEmpty() && prefsItems.all { it.contains("-") }

        return tabsUnchanged || !allTabsHidden
    }

    // contains "-" -> invisible menu item, else -> visible menu item
    fun getNavBarItemPreference(context: Context): List<Pair<Int, Boolean>> {
        val prefItems = try {
            getNavBarPrefs()
        } catch (e: Exception) {
            Log.e(TAG, "fail to parse nav items", e)
            return getDefaultNavBarItems(context).map { it.itemId to it.isVisible }
        }
        val p = PopupMenu(context, null)
        MenuInflater(context).inflate(R.menu.bottom_menu, p.menu)

        if (prefItems.size == p.menu.size) {
            // a corrupted preference (out of range, not a number) must never crash a dialog
            return try {
                prefItems.map {
                    val menuItemId = p.menu[it.replace("-", "").toInt()].itemId
                    val isVisible = !it.contains("-")
                    menuItemId to isVisible
                }
            } catch (e: Exception) {
                Log.e(TAG, "fail to parse nav items", e)
                getDefaultNavBarItems(context).map { it.itemId to it.isVisible }
            }
        }
        return getDefaultNavBarItems(context).map { it.itemId to it.isVisible }
    }

    private fun getDefaultNavBarItems(context: Context): List<MenuItem> {
        val p = PopupMenu(context, null)
        MenuInflater(context).inflate(R.menu.bottom_menu, p.menu)
        return p.menu.iterator().asSequence().toList()
    }

    fun setNavBarItemsPreference(context: Context, items: List<NavBarItem>) {
        val prefString = mutableListOf<String>()
        val defaultNavBarItems = getDefaultNavBarItems(context)
        items.forEach { newItem ->
            val index = defaultNavBarItems.indexOfFirst { newItem.itemId == it.itemId }
            // an unknown item would be stored as "-1" and silently re-read as "item #1 hidden"
            if (index < 0) return@forEach
            prefString.add(if (newItem.isVisible) index.toString() else "-$index")
        }
        PreferenceHelper.putString(
            PreferenceKeys.NAVBAR_ITEMS,
            prefString.joinToString(SEPARATOR)
        )
    }

    /**
     * Apply the bottom navigation style configured in the preferences.
     *
     * Accepts any [NavigationBarView] (the bottom bar on phones, the navigation rail on
     * tablets / unfolded foldables) so both bars always show the very same tabs.
     *
     * @return Id of the start fragment
     */
    fun applyNavBarStyle(bottomNav: NavigationBarView): Int {
        val navBarItems = getNavBarItemPreference(bottomNav.context)
        val startFragmentId = getStartFragmentId(bottomNav.context)

        // copy menu items 1:1, but add them in user-preferred order
        navBarItems.forEach { (menuItemId, isVisible) ->
            val oldMenuItem = bottomNav.menu.findItem(menuItemId) ?: return@forEach
            bottomNav.menu.removeItem(oldMenuItem.itemId)

            // we re-add all items, even if they're hidden, otherwise the nav graph breaks
            val newMenuItem = bottomNav.menu.add(oldMenuItem.groupId, oldMenuItem.itemId, Menu.NONE, oldMenuItem.title)
            newMenuItem.icon = oldMenuItem.icon
            newMenuItem.isVisible = isVisible
        }
        if (navBarItems.none { (_, isVisible) -> isVisible }) bottomNav.isGone = true

        return startFragmentId
    }

    fun getStartFragmentId(context: Context): Int {
        val pref = PreferenceHelper.getInt(PreferenceKeys.START_FRAGMENT, Int.MAX_VALUE)
        val defaultNavItems = getDefaultNavBarItems(context)
        return if (pref == Int.MAX_VALUE) {
            getNavBarItemPreference(context).firstOrNull { (_, isVisible) -> isVisible }?.first
                ?: R.id.homeFragment
        } else {
            // -1 (unknown item) or a stale index must not throw IndexOutOfBounds
            defaultNavItems.getOrNull(pref)?.itemId ?: R.id.homeFragment
        }
    }

    fun setStartFragment(context: Context, itemId: Int) {
        val index = getDefaultNavBarItems(context).indexOfFirst { it.itemId == itemId }
        if (index < 0) return
        PreferenceHelper.putInt(PreferenceKeys.START_FRAGMENT, index)
    }

    fun getNavBarItemTitle(context: Context, menuItemId: Int): String? {
        val p = PopupMenu(context, null)
        MenuInflater(context).inflate(R.menu.bottom_menu, p.menu)
        return p.menu.findItem(menuItemId)?.title?.toString()
    }

    private fun getNavBarPrefs(): List<String> {
        return PreferenceHelper
            .getString(PreferenceKeys.NAVBAR_ITEMS, "")
            .split(SEPARATOR)
    }
}
