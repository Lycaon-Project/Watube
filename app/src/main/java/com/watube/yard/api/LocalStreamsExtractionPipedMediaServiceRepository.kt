package com.watube.yard.api

// region Local Extraction
class LocalStreamsExtractionPipedMediaServiceRepository: PipedMediaServiceRepository() {
    private val newPipeDelegate = NewPipeMediaServiceRepository()

    override suspend fun getStreams(videoId: String) = newPipeDelegate.getStreams(videoId)

    override suspend fun getStreamsForDownload(videoId: String) =
        newPipeDelegate.getStreamsForDownload(videoId)
}
// endregion
