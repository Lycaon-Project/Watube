package com.watube.yard

import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.db.obj.DownloadItem
import com.watube.yard.enums.FileType
import com.watube.yard.helpers.BackupHelper
import com.watube.yard.helpers.DownloadDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Paths

/** Rebuilt from the 26.12 build after the emergency restore. */
class DownloadDestinationTest {
    private fun item(type: FileType, fileName: String, language: String? = null) = DownloadItem(
        type = type,
        videoId = "dQw4w9WgXcQ",
        fileName = fileName,
        path = Paths.get(""),
        language = language
    )

    @Test
    fun `a readable file is named after the video, with its own extension`() {
        val video = item(FileType.VIDEO, "dQw4w9WgXcQ_720p_MPEG_4.mp4")
        assertEquals(
            "Never Gonna Give You Up.mp4",
            DownloadDestination.fileNameOf("Never Gonna Give You Up", video)
        )
    }

    @Test
    fun `an audio file gets an extension Android files with the music`() {
        assertEquals(
            "Song.mka",
            DownloadDestination.fileNameOf("Song", item(FileType.AUDIO, "id_160k_WEBMA_OPUS.webm"))
        )
        assertEquals(
            "Song.m4a",
            DownloadDestination.fileNameOf("Song", item(FileType.AUDIO, "id_128k_M4A.mp4"))
        )
        assertEquals(
            "Clip.webm",
            DownloadDestination.fileNameOf("Clip", item(FileType.VIDEO, "id_720p_WEBM.webm"))
        )
    }

    @Test
    fun `a subtitle carries its language before the extension so players pick it up`() {
        val subtitle = item(FileType.SUBTITLE, "dQw4w9WgXcQ_fr.srt", "fr")
        assertEquals("Clip.fr.srt", DownloadDestination.fileNameOf("Clip", subtitle))
    }

    @Test
    fun `characters the file systems refuse never reach the name`() {
        val audio = item(FileType.AUDIO, "id_128k_opus.webm")
        val name = DownloadDestination.fileNameOf("AC/DC: Live? <\"Best\"> |1| *x*\u0007", audio)
        assertEquals("AC_DC_ Live_ __Best__ _1_ _x__.mka", name)
    }

    @Test
    fun `a name made only of dots and blanks falls back to the download's own`() {
        val video = item(FileType.VIDEO, "abc_360p_MPEG_4.mp4")
        assertEquals("abc_360p_MPEG_4.mp4", DownloadDestination.fileNameOf(" .. ", video))
        assertEquals("abc_360p_MPEG_4.mp4", DownloadDestination.fileNameOf(null, video))
    }

    @Test
    fun `a title can not climb out of the folder`() {
        val video = item(FileType.VIDEO, "abc.mp4")
        assertEquals("_.._etc.mp4", DownloadDestination.fileNameOf("../../etc", video))
    }

    @Test
    fun `a long title fits the 255 bytes of a name, without a character cut in two`() {
        val subtitle = item(FileType.SUBTITLE, "id_ja.srt", "ja")
        val name = DownloadDestination.fileNameOf("日本語のタイトル🎌".repeat(30), subtitle)
        assert(name.toByteArray(Charsets.UTF_8).size <= 255) { name }
        assert(name.endsWith(".ja.srt")) { name }
        assertFalse(name.contains('�'))
    }

    @Test
    fun `a backup never moves the downloads folder nor drops their encryption`() {
        assertFalse(BackupHelper.isImportable(PreferenceKeys.DOWNLOAD_FOLDER))
        assertFalse(BackupHelper.isImportable(PreferenceKeys.DOWNLOAD_READABLE))
    }
}
