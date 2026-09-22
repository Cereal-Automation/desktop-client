package com.cereal.client.domain.model.app

import java.io.File

sealed class DownloadStatus {
    data class Downloading(
        val progress: Int,
    ) : DownloadStatus()

    data class Finished(
        val file: File,
        /**
         * Expected lowercase hex SHA-256 of [file] from the release metadata
         * ([Version.downloadSha256]), carried along so the installer can be re-verified
         * immediately before it is executed. Null when the metadata carried no digest.
         */
        val sha256: String? = null,
    ) : DownloadStatus()
}
