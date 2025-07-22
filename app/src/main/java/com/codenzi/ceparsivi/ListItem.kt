package com.codenzi.ceparsivi

sealed class ListItem {
    data class HeaderItem(val title: String) : ListItem()
    data class FileItem(val archivedFile: ArchivedFile) : ListItem()
}