package com.cereal.client.application.interactor.auth

import com.cereal.client.infrastructure.provider.inmemory.InMemoryAuthProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ForgotPasswordInteractorTest {
    private lateinit var userRepository: InMemoryAuthProvider
    private lateinit var forgotPasswordInteractor: ForgotPasswordInteractor

    @BeforeEach
    fun setup() {
        userRepository = InMemoryAuthProvider()
        forgotPasswordInteractor = ForgotPasswordInteractor(userRepository)
    }

    @Test
    fun `run delegates to UserRepository forgotPassword with correct email`() =
        runTest {
            val email = "user@example.com"

            forgotPasswordInteractor(ForgotPasswordInteractor.Params(email))

            assertEquals(listOf(email), userRepository.forgotPasswordEmails)
        }
}
