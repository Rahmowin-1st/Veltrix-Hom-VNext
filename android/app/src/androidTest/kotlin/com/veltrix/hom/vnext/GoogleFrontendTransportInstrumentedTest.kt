package com.veltrix.hom.vnext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class GoogleFrontendTransportInstrumentedTest {
    @Test
    fun exchangePostsAcceptedPathAndUsesOnlyServerMintedVeltrixSession() {
        val server = ServerSocket(0)
        val received = StringBuilder()
        val served = CountDownLatch(1)
        val worker = thread(start = true, isDaemon = true) {
            server.use { socket ->
                socket.accept().use { client ->
                    val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                    received.appendLine(reader.readLine())
                    var contentLength = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) break
                        if (line.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = line.substringAfter(':').trim().toInt()
                        }
                    }
                    val body = CharArray(contentLength)
                    var offset = 0
                    while (offset < contentLength) {
                        val read = reader.read(body, offset, contentLength - offset)
                        if (read <= 0) break
                        offset += read
                    }
                    received.append(String(body, 0, offset))
                    val payload = "{\"accountId\":\"account-server-owned\",\"sessionToken\":\"veltrix-session-server-minted\"}"
                    val bytes = payload.toByteArray(Charsets.UTF_8)
                    val response = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: application/json\r\n")
                        append("Content-Length: ${bytes.size}\r\n")
                        append("Connection: close\r\n\r\n")
                        append(payload)
                    }.toByteArray(Charsets.UTF_8)
                    client.getOutputStream().use { output -> output.write(response); output.flush() }
                    served.countDown()
                }
            }
        }

        val session = VeltrixApiClient("http://127.0.0.1:${server.localPort}")
            .exchangeGoogleIdentity("synthetic-google-id-token", "synthetic-nonce")

        assertTrue("local contract server must receive request", served.await(2, TimeUnit.SECONDS))
        worker.join(2_000)
        val request = received.toString()
        assertTrue(request.startsWith("POST /v1/auth/google HTTP/1.1"))
        assertTrue(request.contains("\"idToken\":\"synthetic-google-id-token\""))
        assertTrue(request.contains("\"nonce\":\"synthetic-nonce\""))
        assertEquals("account-server-owned", session.accountId)
        assertEquals("veltrix-session-server-minted", session.token)
    }
}
