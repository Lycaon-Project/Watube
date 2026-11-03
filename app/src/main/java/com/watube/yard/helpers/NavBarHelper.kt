package com.watube.yard.helpers

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.PopupMenu
import androidx.core.view.get
import androidx.core.view.iterator
import androidx.core.view.size
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.ui.dialogs.NavBarItem
import com.google.android.material.navigation.NavigationBarView

// region Navigation
object NavBarHelper {

    private const val TAG = "NavBarHelper"
    private const val SEPARATOR = ","

    /**
     * Copy of a menu item taken before the menu is rebuilt, so the live menu is never read
     * while it is being modified.
     */
    private data class NavItemSnapshot(
        val groupId: Int,
        val itemId: Int,
        val title: CharSequence?,
        val icon: Drawable?,
        val isVisible: Boolean
    )

    /**
     * Privacy then settings close the bar, always visible and never moved: once hidden, nothing
     * led to the settings anymore. This also repairs older preferences.
     */
    private val PINNED_TABS = listOf(R.id.privacyFragment, R.id.settingsFragment)

    /**
     * Tabs the user may show at once: the bottom bar holds six items (hidden ones included,
     * Material throws beyond) and two of them are pinned.
     */
    const val MAX_VISIBLE_USER_TABS = 4

    fun isPinned(itemId: Int) = itemId in PINNED_TABS

    fun getNavBarItemPreference(context: Context): List<Pair<Int, Boolean>> {
        val (pinned, items) = readNavBarItemPreference(context).partition { (itemId, _) -> isPinned(itemId) }
        // more visible tabs than the bar holds (older preference): the last ones are hidden
        var visibleLeft = MAX_VISIBLE_USER_TABS
        val userTabs = items.map { (itemId, isVisible) ->
            itemId to (isVisible && visibleLeft-- > 0)
        }
        return userTabs + pinned
            .sortedBy { (itemId, _) -> PINNED_TABS.indexOf(itemId) }
            .map { (itemId, _) -> itemId to true }
    }

    // contains "-" -> invisible menu item, else -> visible menu item
    private fun readNavBarItemPreference(context: Context): List<Pair<Int, Boolean>> {
        val prefItems = try {
            getNavBarPrefs()
        } catch (e: Exception) {
            Log.e(TAG, "fail to parse nav items", e)
            return getDefaultNavBarItems(context).map { it.itemId to it.isVisible }
        }
        val p = PopupMenu(context, null)
        MenuInflater(context).inflate(R.menu.bottom_menu, p.menu)

        // a tab appended to bottom_menu.xml must not wipe a customized order: the stored
        // list is migrated by appending the id of the item that was added
        val items = if (prefItems.size == p.menu.size - 1 && prefItems.all { token ->
                val index = token.replace("-", "").toIntOrNull()
                index != null && index in 0 until p.menu.size - 1
            }
        ) {
            (prefItems + (p.menu.size - 1).toString()).also {
                PreferenceHelper.putString(PreferenceKeys.NAVBAR_ITEMS, it.joinToString(SEPARATOR))
            }
        } else {
            prefItems
        }

        if (items.size == p.menu.size) {
            // a corrupted preference (out of range, not a number) must never crash a dialog
            return try {
                items.map {
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
     * The menu is rebuilt in a single pass (snapshot -> clear -> re-add) instead of
     * removing and re-adding items one by one on the live menu: removing the checked item
     * made Material re-check it while rebuilding its views, which re-triggered the
     * rebuild endlessly (StackOverflowError in MainActivity.onCreate).
     *
     * @return Id of the start fragment
     */
    fun applyNavBarStyle(bottomNav: NavigationBarView): Int {
        val context = bottomNav.context
        val navBarItems = getNavBarItemPreference(context)
        val startFragmentId = getStartFragmentId(context)
        val menu = bottomNav.menu

        // 1. read everything BEFORE touching the menu, in the user-preferred order. The items
        // come from the menu resource: the bottom bar is not inflated with it, it could not
        // hold every tab
        val template = PopupMenu(context, null).menu
        MenuInflater(context).inflate(R.menu.bottom_menu, template)
        val snapshots = navBarItems.mapNotNull { (menuItemId, isVisible) ->
            template.findItem(menuItemId)?.let { oldMenuItem ->
                NavItemSnapshot(
                    groupId = oldMenuItem.groupId,
                    itemId = oldMenuItem.itemId,
                    title = oldMenuItem.title,
                    icon = oldMenuItem.icon,
                    isVisible = isVisible
                )
            }
        }

        // 2. empty menu: no item is checked anymore, so Material has nothing to re-check
        menu.clear()

        // 3. hidden items are added too while the bar has room: the selection sync relies on
        // them to light no tab on their screens. Beyond the bar capacity a hidden screen
        // lights the tab it was opened from (downloads -> library) instead.
        var hiddenRoom = bottomNav.maxItemCount - snapshots.count { it.isVisible }
        snapshots.forEach { snapshot ->
            if (!snapshot.isVisible && hiddenRoom-- <= 0) return@forEach
            menu.add(snapshot.groupId, snapshot.itemId, Menu.NONE, snapshot.title).apply {
                icon = snapshot.icon
                isVisible = snapshot.isVisible
            }
        }

        // the new items lost the exclusive checkable flag of the previous generation, and
        // without it a check (NavigationUI only ever checks its new destination item)
        // leaves the old tab lit as well
        menu.setGroupCheckable(0, true, true)

        return startFragmentId
    }

    fun getStartFragmentId(context: Context): Int {
        val pref = PreferenceHelper.getInt(PreferenceKeys.START_FRAGMENT, Int.MAX_VALUE)
        val defaultNavItems = getDefaultNavBarItems(context)
        return if (pref == Int.MAX_VALUE) {
            // the pinned tabs (dashboard, settings) are never where the app opens
            getNavBarItemPreference(context)
                .firstOrNull { (itemId, isVisible) -> isVisible && !isPinned(itemId) }?.first
                ?: R.id.homeFragment
        } else {
            // -1 (unknown item) or a stale index must not throw IndexOutOfBounds; settings can no
            // longer be the start tab, an older choice of it falls back to home
            defaultNavItems.getOrNull(pref)?.itemId?.takeUnless(::isPinned) ?: R.id.homeFragment
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
// endregion
