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

class GoogleAuthContractUnitTest {
    @Test
    fun exchangeGoogleIdentityUsesAcceptedBackendContractAndServerSession() {
        val server = ServerSocket(0)
        val received = StringBuilder()
        val served = CountDownLatch(1)
        val worker = thread(start = true, isDaemon = true) {
            server.use { socket ->
                val client = socket.accept()
                client.use {
                    val reader = BufferedReader(InputStreamReader(it.getInputStream(), Charsets.UTF_8))
                    val requestLine = reader.readLine()
                    received.appendLine(requestLine)
                    var contentLength = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) break
                        received.appendLine(line)
                        if (line.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = line.substringAfter(':').trim().toInt()
                        }
                    }
                    val body = CharArray(contentLength)
                    if (contentLength > 0) reader.read(body, 0, contentLength)
                    received.append(String(body))
                    val payload = "{\"accountId\":\"account-server-owned\",\"sessionToken\":\"veltrix-session-server-minted\"}"
                    val response = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: application/json\r\n")
                        append("Content-Length: ${payload.toByteArray().size}\r\n")
                        append("Connection: close\r\n\r\n")
                        append(payload)
                    }
                    it.getOutputStream().write(response.toByteArray())
                    it.getOutputStream().flush()
                    served.countDown()
                }
            }
        }

        val api = VeltrixApiClient("http://127.0.0.1:${server.localPort}")
        val session = api.exchangeGoogleIdentity("google-id-token", "nonce-123")
        assertTrue(served.await(2, TimeUnit.SECONDS))
        worker.join(2_000)

        val text = received.toString()
        assertTrue(text.startsWith("POST /v1/auth/google HTTP/1.1"))
        assertTrue(text.contains("\"idToken\":\"google-id-token\""))
        assertTrue(text.contains("\"nonce\":\"nonce-123\""))
        assertEquals("account-server-owned", session.accountId)
        assertEquals("veltrix-session-server-minted", session.token)
    }

    @Test
    fun typedStoreAvailabilityNeverNeedsRawRuleJsonForUserCopy() {
        val item = StoreItemUiModel(
            itemId = "internal-avatar-id",
            itemType = "AVATAR",
            priceCoins = 350,
            owned = false,
            available = false,
            requirements = "{\"minLevel\":5}",
            metadata = "{\"internal\":true}",
            displayName = "Scholar Orbit",
            availability = StoreAvailabilityUiModel(
                state = "LOCKED",
                reasonCode = "LEVEL_REQUIRED",
                requiredLevel = 5,
            ),
        )
        assertEquals("Unlocks at Level 5", item.userFacingAvailability(balance = 1_000))
        assertTrue(item.userFacingAvailability(1_000)?.contains('{') == false)
        assertTrue(item.userFacingAvailability(1_000)?.contains("minLevel") == false)
    }
    @Test
    fun skuLikeAvatarNamesAreNeverExposedAsProductIdentity() {
        val avatar = AvatarCatalogUiModel(
            avatarId = "noob-002",
            name = "Noob 002",
            assetKey = "avatar-noob-002",
            tier = "NOOB",
            owned = true,
            equipped = false,
            storePrice = null,
            catalogVersion = "test",
            identityMetadata = "{}",
        )
        val display = avatarDisplayName70(avatar)
        assertEquals("Noob identity", display)
        assertTrue(!display.contains("002"))
        assertTrue(!display.equals(avatar.avatarId, ignoreCase = true))
    }

}
