package com.personal.personalai.domain.audio

import java.io.File

/**
 * Domain-layer abstraction for audio recording.
 * Uses only [java.io.File] — no Android platform imports — so the presentation
 * layer (ViewModel) can depend on this interface without coupling to the data layer.
 *
 * The implementation ([com.personal.personalai.data.audio.AudioRecorderImpl]) is
 * bound via Hilt in AppModule.
 */
interface AudioRecorder {
    /**
     * Starts recording and returns the [File] that will hold the audio data.
     * Calling [start] while already recording first stops the previous recording.
     * @throws Exception if the underlying [android.media.MediaRecorder] fails to prepare or start.
     */
    fun start(): File

    /**
     * Stops recording, releases resources, and returns the recorded [File].
     * @return the audio file, or null if recording was not active.
     */
    fun stop(): File?

    /**
     * Safe cleanup — call from [androidx.lifecycle.ViewModel.onCleared].
     * Equivalent to [stop] but the returned file is ignored.
     */
    fun release()
}
