package com.wm.iptv.data

/** حساب Xtream محفوظ */
data class Account(
    val id: String,
    val name: String,
    val server: String,   // مثل http://host:port بدون / في الآخر
    val username: String,
    val password: String,
)

data class Category(val id: String, val name: String)

enum class Kind { LIVE, MOVIE, SERIES }

data class Channel(
    val id: Int,
    val num: Int,
    val name: String,
    val icon: String,
    val epgId: String,
    val categoryId: String,
    val archive: Boolean,
    val key: String,       // الاسم بعد التطبيع للبحث
)

data class Movie(
    val id: Int,
    val name: String,
    val icon: String,
    val rating: String,
    val added: Long,
    val categoryId: String,
    val ext: String,
    val key: String,
)

data class Series(
    val id: Int,
    val name: String,
    val cover: String,
    val rating: String,
    val updated: Long,
    val categoryId: String,
    val key: String,
)

data class Catalog(
    val liveCats: List<Category> = emptyList(),
    val movieCats: List<Category> = emptyList(),
    val seriesCats: List<Category> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
) {
    val isEmpty get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()

    val channelById: Map<Int, Channel> by lazy { channels.associateBy { it.id } }
    val movieById: Map<Int, Movie> by lazy { movies.associateBy { it.id } }
    val seriesById: Map<Int, Series> by lazy { series.associateBy { it.id } }

    val moviesByCat: Map<String, List<Movie>> by lazy { movies.groupBy { it.categoryId } }
    val seriesByCat: Map<String, List<Series>> by lazy { series.groupBy { it.categoryId } }
    val channelsByCat: Map<String, List<Channel>> by lazy { channels.groupBy { it.categoryId } }

    val recentMovies: List<Movie> by lazy { movies.sortedByDescending { it.added }.take(300) }
    val recentSeries: List<Series> by lazy { series.sortedByDescending { it.updated }.take(300) }
}

data class UserInfo(
    val status: String,
    val expDate: Long?,        // ثواني epoch، null = غير محدود
    val maxConnections: Int,
    val activeConnections: Int,
    val isTrial: Boolean,
    val createdAt: Long?,
)

data class EpgEntry(
    val title: String,
    val description: String,
    val start: Long,  // millis
    val end: Long,    // millis
)

data class VodInfo(
    val plot: String,
    val cast: String,
    val director: String,
    val genre: String,
    val year: String,
    val durationSecs: Int,
    val rating: String,
    val backdrop: String,
    val poster: String,
    val ext: String,
)

data class Episode(
    val id: Int,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String,
    val durationSecs: Int,
    val plot: String,
    val image: String,
)

data class SeriesInfo(
    val name: String,
    val plot: String,
    val cast: String,
    val genre: String,
    val year: String,
    val rating: String,
    val cover: String,
    val backdrop: String,
    val seasons: List<Int>,
    val episodes: Map<Int, List<Episode>>,
)

/** مكان التوقف في فيلم أو حلقة */
data class Progress(
    val key: String,          // m:<id> أو e:<id>
    val position: Long,       // ms
    val duration: Long,       // ms
    val updated: Long,
    val title: String,
    val subtitle: String,
    val image: String,
    val ext: String,
    val seriesId: Int = 0,
) {
    val fraction: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val finished: Boolean get() = duration > 0 && fraction >= 0.92f
}
