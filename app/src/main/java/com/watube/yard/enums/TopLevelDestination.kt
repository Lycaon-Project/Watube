package com.watube.yard.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.watube.yard.R

enum class TopLevelDestination(
    val route: String,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int
) {
    Home("home", R.string.startpage, R.drawable.ic_home),
    Trends("trends", R.string.trends, R.drawable.ic_trending),
    Subscriptions("subscriptions", R.string.subscriptions, R.drawable.ic_subscriptions),
    Library("library", R.string.library, R.drawable.ic_library)
}