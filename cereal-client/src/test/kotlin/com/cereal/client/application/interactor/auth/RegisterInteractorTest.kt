package com.cereal.client.application.interactor.auth

import com.cereal.client.application.auth.UserAuthManager
import com.cereal.client.domain.model.user.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Mock-based by design: this interactor's only collaborator is [UserAuthManager], an application
 * service (not a repository), whose real construction pulls in six further collaborators. The
 * in-memory-repository preference applies to repository dependencies, of which this interactor has
 * none.
 */
class RegisterInteractorTest {
    private val userAuthManager = mockk<UserAuthManager>()
    private lateinit var registerInteractor: RegisterInteractor

    @BeforeEach
    fun setup() {
        registerInteractor = RegisterInteractor(userAuthManager)
    }

    @Test
    fun `run delegates to UserAuthManager register with the provided params`() =
        runTest {
            // Given
            val name = "Jane Doe"
            val email = "jane@example.com"
            val password = "s3cret"
            coEvery { userAuthManager.register(name, email, password) } returns
                User(
                    id = "user-1",
                    name = name,
                    email = email,
                    encryptionKey = "key",
                    accessToken = "token",
                )

            // When
            registerInteractor.run(RegisterInteractor.Params(name, email, password))

            // Then
            coVerify { userAuthManager.register(name, email, password) }
        }

    @Test
    fun `run propagates exceptions thrown by UserAuthManager`() =
        runTest {
            // Given
            val name = "Jane Doe"
            val email = "jane@example.com"
            val password = "s3cret"
            coEvery { userAuthManager.register(name, email, password) } throws
                IllegalArgumentException("email taken")

            // When / Then
            assertThrows<IllegalArgumentException> {
                registerInteractor.run(RegisterInteractor.Params(name, email, password))
            }
        }
}
