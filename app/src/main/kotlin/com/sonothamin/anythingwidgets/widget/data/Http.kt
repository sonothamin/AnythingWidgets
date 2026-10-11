package com.sonothamin.anythingwidgets.widget.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTPS-only client: timeouts, size cap, conditional GET. */
internal object Http {
    private const val MAX_BYTES = 256 * 1024

    class Response(val code: Int, val bytes: ByteArray, val etag: String?)

    fun get(url: String, etag: String? = null): Response {
        require(url.startsWith("https://")) { "HTTPS required" }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/json")
            etag?.let { conn.setRequestProperty("If-None-Match", it) }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return Response(code, ByteArray(0), etag)
            if (code !in 200..299) throw IOException("HTTP $code")
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) throw IOException("Response too large")
                    out.write(buf, 0, n)
                }
            }
            return Response(code, out.toByteArray(), conn.getHeaderField("ETag"))
        } finally {
            conn.disconnect()
        }
    }
}
