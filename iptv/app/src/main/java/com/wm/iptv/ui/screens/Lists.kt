package com.wm.iptv.ui.screens

import com.wm.iptv.data.Catalog
import com.wm.iptv.data.Channel
import com.wm.iptv.data.Kind
import com.wm.iptv.data.Repo

/** مفاتيح القوائم الخاصة في المباشر */
object LiveKeys {
    const val FAV = "fav"
    const val RECENT = "recent"
    const val ALL = "all"
    fun cat(id: String) = "c:$id"
}

fun channelsFor(key: String, c: Catalog = Repo.catalog.value): List<Channel> {
    val hidden = Repo.store.hiddenCategories(Kind.LIVE)
    return when {
        key == LiveKeys.FAV -> Repo.store.favoriteOrder(Kind.LIVE).mapNotNull { c.channelById[it] }
        key == LiveKeys.RECENT -> Repo.store.recentChannels().mapNotNull { c.channelById[it] }
        key == LiveKeys.ALL -> c.channels.filter { it.categoryId !in hidden }
        key.startsWith("c:") -> c.channelsByCat[key.removePrefix("c:")] ?: emptyList()
        else -> c.channels
    }
}

fun liveKeyTitle(key: String, c: Catalog = Repo.catalog.value): String = when {
    key == LiveKeys.FAV -> "المفضلة"
    key == LiveKeys.RECENT -> "آخر المشاهدة"
    key == LiveKeys.ALL -> "كل القنوات"
    key.startsWith("c:") -> c.liveCats.firstOrNull { it.id == key.removePrefix("c:") }?.name ?: ""
    else -> ""
}
