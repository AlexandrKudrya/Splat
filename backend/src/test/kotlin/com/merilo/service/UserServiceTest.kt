package com.merilo.service

import com.merilo.model.UserEntity
import com.merilo.repository.UserRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import io.mockk.mockk
import io.mockk.every
import org.assertj.core.api.Assertions.assertThatThrownBy


class UserServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        userService = UserService(userRepository)
    }

    @Test
    fun `getMe returns dto when user exists`() {
        val userId = 10L
        val entity = UserEntity(
            id = 10L,
            telegramId = 777L,
            username = "alice",
            paymentMethods = mapOf("card" to "1111")
        )

        every { userRepository.findById(userId) } returns Optional.of(entity)

        val dto = userService.getMe(userId)

        Assertions.assertThat(dto.id).isEqualTo(userId)
        Assertions.assertThat(dto.telegram_id).isEqualTo(777L)
        Assertions.assertThat(dto.username).isEqualTo("alice")
        Assertions.assertThat(dto.payment_methods).isEqualTo(mapOf("card" to "1111"))
    }

    @Test
    fun `getMe throws 404 when user not found`() {
        val userId = 404L
        every { userRepository.findById(userId) } returns Optional.empty()

        assertThatThrownBy { userService.getMe(userId) }
            .isInstanceOf(ResponseStatusException::class.java)
            .extracting("statusCode")
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `mergePaymentMethods merges patch into existing and overwrites same keys`() {
        val userId = 1L
        val entity = UserEntity(
            id = 10L,
            telegramId = 777L,
            username = "bob",
            paymentMethods = mapOf("card" to "1111", "paypal" to "bob@pay")
        )


        every { userRepository.findById(userId) } returns Optional.of(entity)

        val patch = mapOf(
            "card" to "2222",          // overwrite
            "applepay" to "bob-apple"  // add new
        )

        val dto = userService.mergePaymentMethods(userId, patch)


        Assertions.assertThat(entity.paymentMethods).isEqualTo(
            mapOf(
                "card" to "2222",
                "paypal" to "bob@pay",
                "applepay" to "bob-apple"
            )
        )

        Assertions.assertThat(dto.payment_methods).isEqualTo(entity.paymentMethods)
    }

    @Test
    fun `mergePaymentMethods with empty patch keeps paymentMethods unchanged`() {
        val userId = 2L
        val initial = mapOf("card" to "1111")
        val entity = UserEntity(
            id = userId,
            telegramId = 200L,
            username = "carol",
            paymentMethods = initial
        )

        every { userRepository.findById(userId) } returns Optional.of(entity)

        val dto = userService.mergePaymentMethods(userId, emptyMap())

        Assertions.assertThat(entity.paymentMethods).isEqualTo(initial)
        Assertions.assertThat(dto.payment_methods).isEqualTo(initial)
    }

    @Test
    fun `mergePaymentMethods throws 404 when user not found`() {
        val userId = 404L
        every { userRepository.findById(userId) } returns Optional.empty()

        assertThatThrownBy { userService.mergePaymentMethods(userId, mapOf("card" to "1234")) }
            .isInstanceOf(ResponseStatusException::class.java)
            .extracting("statusCode")
            .isEqualTo(HttpStatus.NOT_FOUND)
    }
}