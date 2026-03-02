package com.personal.personalai.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.personal.personalai.domain.audio.AudioRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AudioRecorder] implementation backed by [MediaRecorder].
 *
 * Produces `.m4a` (AAC / MPEG-4) files in the app's [Context.getCacheDir].
 * The 16 kHz sample rate is Whisper's native rate, which reduces file size
 * without any quality loss for speech transcription.
 *
 * Bound to [AudioRecorder] via @Binds in [com.personal.personalai.di.AppModule].
 */
@Singleton
class AudioRecorderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioRecorder {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    override fun start(): File {
        // Release any stale recorder first (safety guard)
        releaseRecorder()

        val file = File(context.cacheDir, "whisper_input_${System.currentTimeMillis()}.m4a")
        currentFile = file

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mr.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(64_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        recorder = mr
        Log.d(TAG, "Recording started → ${file.name}")
        return file
    }

    override fun stop(): File? {
        val file = currentFile
        releaseRecorder()
        return file
    }

    override fun release() {
        releaseRecorder()
    }

    private fun releaseRecorder() {
        runCatching {
            recorder?.apply {
                stop()
                release()
            }
        }.onFailure { e ->
            Log.w(TAG, "MediaRecorder stop/release failed (safe to ignore): ${e.message}")
        }
        recorder = null
        currentFile = null
    }

    companion object {
        private const val TAG = "AudioRecorderImpl"
    }
}
