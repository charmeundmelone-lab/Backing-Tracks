package de.livegigplayer.pro.data

sealed class TrackMode {
    data class Legacy(val filePath: String) : TrackMode()
    data class Multitrack(
        val drums: String?,
        val bass: String?,
        val keys: String?,
        val vocals: String?,
        val click: String?,
        val cue: String?
    ) : TrackMode()
}
