package com.merilo.integration.telegram

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class TelegramInitDataVerifierTest {

    private val objectMapper = jacksonObjectMapper()
    private val botToken = "123456:TEST_BOT_TOKEN"
    private val maxAgeSeconds = 60L


    private val verifier = TelegramInitDataVerifier(
        botToken = botToken,
        maxAgeSeconds = maxAgeSeconds,
        objectMapper = objectMapper
    )

    @Test
    fun `verifyAndExtract returns VerifiedInitData for valid initData`() {
        val now = 1_710_000_000L
        val authDate = now - 10

        val tgUser = TelegramUser(id = 777L, username = "alice")
        val initData = buildValidInitData(botToken, authDate, tgUser)

        val verified = verifier.verifyAndExtract(initData, nowEpochSeconds = now)

        assertThat(verified.authDate).isEqualTo(authDate)
        assertThat(verified.telegramUser.id).isEqualTo(777L)
        assertThat(verified.telegramUser.username).isEqualTo("alice")
    }

    @Test
    fun `verifyAndExtract throws when initData missing hash`() {
        val now = 1_710_000_000L
        val authDate = now - 10
        val tgUser = TelegramUser(id = 1L, username = "a")

        val initDataWithoutHash = buildInitDataRaw(
            params = mapOf(
                "auth_date" to authDate.toString(),
                "user" to objectMapper.writeValueAsString(tgUser),
                "query_id" to "q1"
            )
        )

        assertThatThrownBy { verifier.verifyAndExtract(initDataWithoutHash, nowEpochSeconds = now) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("missing hash")
    }

    @Test
    fun `verifyAndExtract throws when initData is too old`() {
        val now = 1_710_000_000L
        val authDate = now - (maxAgeSeconds + 5) // старее maxAgeSeconds
        val tgUser = TelegramUser(id = 2L, username = "b")

        val initData = buildValidInitData(botToken, authDate, tgUser)

        assertThatThrownBy { verifier.verifyAndExtract(initData, nowEpochSeconds = now) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("too old")
    }

    @Test
    fun `verifyAndExtract throws when auth_date is not a number`() {
        val now = 1_710_000_000L
        val tgUser = TelegramUser(id = 3L, username = "c")

        val params = linkedMapOf(
            "auth_date" to "not-a-number",
            "user" to objectMapper.writeValueAsString(tgUser),
            "query_id" to "q1"
        )


        val hash = computeTelegramHash(botToken, params)
        val initData = buildInitDataRaw(params + ("hash" to hash))

        assertThatThrownBy { verifier.verifyAndExtract(initData, nowEpochSeconds = now) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("auth_date is not a number")
    }

    @Test
    fun `verifyAndExtract throws when signature invalid`() {
        val now = 1_710_000_000L
        val authDate = now - 10
        val tgUser = TelegramUser(id = 4L, username = "d")

        val initData = buildValidInitData(botToken, authDate, tgUser)


        val tampered = initData.replace("query_id=q1", "query_id=q2")

        assertThatThrownBy { verifier.verifyAndExtract(tampered, nowEpochSeconds = now) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid initData signature")
    }



    private fun buildValidInitData(botToken: String, authDate: Long, tgUser: TelegramUser): String {
        val params = linkedMapOf(
            "auth_date" to authDate.toString(),
            "query_id" to "q1",
            "user" to objectMapper.writeValueAsString(tgUser)
        )

        val hash = computeTelegramHash(botToken, params)
        return buildInitDataRaw(params + ("hash" to hash))
    }

    private fun buildInitDataRaw(params: Map<String, String>): String {

        return params.entries.joinToString("&") { (k, v) ->
            val encoded = URLEncoder.encode(v, StandardCharsets.UTF_8)
            "$k=$encoded"
        }
    }

    private fun computeTelegramHash(botToken: String, params: Map<String, String>): String {
        val dataCheckString = params
            .filterKeys { it != "hash" }
            .toSortedMap()
            .entries
            .joinToString("\n") { (k, v) -> "$k=$v" }

        val secretKey = hmacSha256Bytes(
            key = "WebAppData".toByteArray(StandardCharsets.UTF_8),
            data = botToken.toByteArray(StandardCharsets.UTF_8)
        )
        return hmacSha256Hex(key = secretKey, data = dataCheckString.toByteArray(StandardCharsets.UTF_8))
    }

    private fun hmacSha256Bytes(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hmacSha256Hex(key: ByteArray, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val bytes = mac.doFinal(data)
        return bytes.joinToString("") { b -> "%02x".format(b) }
    }
}