package com.watube.yard.db.obj

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.watube.yard.enums.FileType
import com.watube.yard.helpers.DownloadEncryption
import java.nio.file.Path
import kotlin.io.path.fileSize

// region Downloads
@Entity(
    tableName = "downloadItem",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["videoId"], name = "index_downloadItem_videoId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = Download::class,
            parentColumns = ["videoId"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,
    val type: FileType,
    val videoId: String,
    val fileName: String,
    var path: Path,
    var format: String? = null,
    var quality: String? = null,
    var language: String? = null,
    /**
     * Total size of the item in bytes (not the current download progress!).
     */
    var downloadSize: Long = -1L,
    /**
     * Current download progress of the video in milliseconds. Only used for SABR downloads.
     */
    var currentDownloadPositionMillis: Long? = null
) {
    /**
     * Only a complete download is encrypted (see DownloadEncryption): an encrypted file is
     * finished whatever its size says, which is unknown for subtitles.
     */
    val isFinished get() = DownloadEncryption.isEncrypted(path) ||
        downloadSize > 0L && runCatching { path.fileSize() }.getOrDefault(0L) >= downloadSize
}
// endregion
