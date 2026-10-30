package com.chatku.app.util

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/** Mengunggah isi Uri (galeri, dokumen, file lokal) secara streaming tanpa memuat semuanya ke memori. */
class UriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mime: String?,
    private val length: Long,
) : RequestBody() {
    override fun contentType(): MediaType? = mime?.toMediaTypeOrNull()
    override fun contentLength(): Long = length
    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: throw java.io.IOException("Tidak bisa membaca file")
        input.use { sink.writeAll(it.source()) }
    }
}
