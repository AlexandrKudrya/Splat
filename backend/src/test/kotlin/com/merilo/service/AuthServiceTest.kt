package com.merilo.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import com.merilo.config.JwtService
import com.merilo.integration.telegram.TelegramUser
import com.merilo.model.UserEntity
import com.merilo.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var jwtService: JwtService
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        jwtService = mockk()
        authService = AuthService(userRepository, jwtService)
    }

    @Test
    fun `loginOrRegister returns token for existing user and does not call save`() {
        val tgUser = TelegramUser(id = 111L, username = "alice")

        val existing = UserEntity(
            id = 10L,
            telegramId = 111L,
            username = "alice"
        )

        every { userRepository.findByTelegramId(111L) } returns existing
        every { jwtService.generateToken(10L) } returns "token-10"

        val res = authService.loginOrRegister(tgUser)

        assertThat(res.access_token).isEqualTo("token-10")
        verify(exactly = 0) { userRepository.save(any()) }
        verify(exactly = 1) { jwtService.generateToken(10L) }
    }

    @Test
    fun `loginOrRegister updates username when changed for existing user`() {
        val tgUser = TelegramUser(id = 222L, username = "new_name")

        val existing = UserEntity(
            id = 20L,
            telegramId = 222L,
            username = "old_name"
        )

        every { userRepository.findByTelegramId(222L) } returns existing
        every { jwtService.generateToken(20L) } returns "token-20"

        val res = authService.loginOrRegister(tgUser)

        assertThat(existing.username).isEqualTo("new_name")
        assertThat(res.access_token).isEqualTo("token-20")

        // в твоей реализации save() не вызывается для existing — и это норм в транзакции
        verify(exactly = 0) { userRepository.save(any()) }
        verify(exactly = 1) { jwtService.generateToken(20L) }
    }

    @Test
    fun `loginOrRegister does not overwrite username when tg username is null`() {
        val tgUser = TelegramUser(id = 333L, username = null)

        val existing = UserEntity(
            id = 30L,
            telegramId = 333L,
            username = "keep_me"
        )

        every { userRepository.findByTelegramId(333L) } returns existing
        every { jwtService.generateToken(30L) } returns "token-30"

        val res = authService.loginOrRegister(tgUser)

        assertThat(existing.username).isEqualTo("keep_me")
        assertThat(res.access_token).isEqualTo("token-30")

        verify(exactly = 0) { userRepository.save(any()) }
        verify(exactly = 1) { jwtService.generateToken(30L) }
    }

    @Test
    fun `loginOrRegister creates user when not exists and returns token`() {
        val tgUser = TelegramUser(id = 444L, username = "bob")

        every { userRepository.findByTelegramId(444L) } returns null

        val captured = slot<UserEntity>()
        val saved = UserEntity(
            id = 40L,
            telegramId = 444L,
            username = "bob",
            paymentMethods = emptyMap()
        )

        every { userRepository.save(capture(captured)) } returns saved
        every { jwtService.generateToken(40L) } returns "token-40"

        val res = authService.loginOrRegister(tgUser)


        assertThat(captured.captured.telegramId).isEqualTo(444L)
        assertThat(captured.captured.username).isEqualTo("bob")
        assertThat(captured.captured.paymentMethods).isEmpty()

        assertThat(res.access_token).isEqualTo("token-40")

        verify(exactly = 1) { userRepository.save(any()) }
        verify(exactly = 1) { jwtService.generateToken(40L) }
    }
}