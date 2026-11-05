package com.wm.iptv.ui

import androidx.compose.runtime.mutableStateListOf
import com.wm.iptv.data.Kind

sealed class Route(val key: String, val isTab: Boolean = false) {
    object Home : Route("home", true)
    object Live : Route("live", true)
    object Movies : Route("movies", true)
    object SeriesList : Route("series", true)
    object Search : Route("search", true)
    object Settings : Route("settings", true)
    data class MovieDetail(val id: Int) : Route("movie_$id")
    data class SeriesDetail(val id: Int) : Route("series_$id")
    data class HiddenCats(val kind: Kind) : Route("hidden_${kind.name}")
    object Accounts : Route("accounts")
    object AddAccount : Route("add_account")
}

class Navigator {
    val stack = mutableStateListOf<Route>(Route.Home)
    val top: Route get() = stack.last()

    fun go(r: Route) {
        if (r.isTab) { stack.clear(); stack.add(r) } else stack.add(r)
    }

    /** يرجع false إذا كنا في الرئيسية (يعني نطلع من التطبيق) */
    fun back(): Boolean {
        if (stack.size > 1) { stack.removeAt(stack.size - 1); return true }
        if (top != Route.Home) { stack.clear(); stack.add(Route.Home); return true }
        return false
    }
}
