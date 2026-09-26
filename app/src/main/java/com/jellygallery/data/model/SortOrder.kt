package com.jellygallery.data.model

enum class MediaSortOrder(val label: String) {
    DATE_DESC("新しい順"),
    DATE_ASC("古い順"),
    NAME_ASC("名前順 (A→Z)"),
    NAME_DESC("名前順 (Z→A)")
}

enum class AlbumSortOrder(val label: String) {
    COUNT_DESC("枚数が多い順"),
    NAME_ASC("アルバム名順")
}
