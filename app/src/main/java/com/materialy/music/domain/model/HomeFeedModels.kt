package com.materialy.music.domain.model

enum class ShelfType {
    QUICK_PICKS,
    CHARTS_ROW,
    HORIZONTAL_CAROUSEL
}

data class ShelfItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnail: String?,
    val type: ItemType = ItemType.TRACK,
    val directUrl: String? = null
) {
    enum class ItemType {
        TRACK,
        ALBUM,
        PLAYLIST,
        ARTIST
    }
}

data class HomeShelf(
    val shelfId: String,
    val title: String,
    val subtitle: String? = null,
    val shelfType: ShelfType = ShelfType.HORIZONTAL_CAROUSEL,
    val items: List<ShelfItem> = emptyList()
)

data class HomeFeedData(
    val shelves: List<HomeShelf> = emptyList()
)
