package com.watube.yard.enums

import androidx.annotation.StringRes
import com.watube.yard.R

enum class ImportFormat(@param:StringRes val value: Int, val fileExtension: String) {
    NEWPIPE(R.string.import_format_newpipe, "json"),
    FREETUBE(R.string.import_format_freetube, "db"),
    YOUTUBECSV(R.string.import_format_youtube_csv, "csv"),
    YOUTUBEJSON(R.string.youtube, "json"),
    PIPED(R.string.import_format_piped, "json"),
    URLSORIDS(R.string.import_format_list_of_urls, "txt")
}
