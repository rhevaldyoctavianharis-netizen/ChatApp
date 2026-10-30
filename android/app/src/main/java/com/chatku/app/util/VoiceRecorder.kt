package com.chatku.app.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder(private val ctx: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()

    fun start(): Boolean {
        return try {
            val f = File(ctx.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            val r = newRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = f
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            false
        }
    }

    /** Berhenti merekam. Mengembalikan file, atau null bila dibatalkan / terlalu singkat (<1 dtk). */
    fun stop(cancel: Boolean): File? {
        val r = recorder ?: return null
        recorder = null
        val f = file
        val duration = System.currentTimeMillis() - startedAt
        try {
            r.stop()
        } catch (_: Exception) {
        }
        r.release()
        if (cancel || duration < 1000) {
            f?.delete()
            return null
        }
        return f
    }
}
