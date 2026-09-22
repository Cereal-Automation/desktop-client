package com.cereal.client.domain.model.user

import com.cereal.client.domain.model.exception.InvalidUserException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UserTest {
    private fun user(
        id: String = "user-1",
        accessToken: String = "token",
    ) = User(
        id = id,
        name = "Jane",
        email = "jane@example.com",
        encryptionKey = "key",
        accessToken = accessToken,
    )

    @Test
    fun `should create valid user`() {
        assertNotNull(user())
    }

    @Test
    fun `should allow blank email for guest-style accounts`() {
        val data =
            User(
                id = "guest-1",
                name = "",
                email = "",
                encryptionKey = "",
                accessToken = "guest-token",
                isGuest = true,
            )
        assertNotNull(data)
    }

    @Test
    fun `should fail when id is blank`() {
        val exception = assertThrows<InvalidUserException> { user(id = "  ") }
        assertEquals("Id cannot be blank", exception.message)
    }

    @Test
    fun `should fail when accessToken is blank`() {
        val exception = assertThrows<InvalidUserException> { user(accessToken = "") }
        assertEquals("AccessToken cannot be blank", exception.message)
    }
}
