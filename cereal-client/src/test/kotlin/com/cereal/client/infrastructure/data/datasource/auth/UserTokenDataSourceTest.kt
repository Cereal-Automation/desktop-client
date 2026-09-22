package com.cereal.client.infrastructure.data.datasource.auth

import com.cereal.client.infrastructure.data.datasource.database.KeyValueDataSource
import com.cereal.client.infrastructure.data.datasource.preference.ApplicationPreferenceKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UserTokenDataSourceTest {
    private val keyValueDataSource = mockk<KeyValueDataSource>(relaxed = true)
    private lateinit var userTokenDataSource: UserTokenDataSource

    @BeforeEach
    fun setup() {
        userTokenDataSource = UserTokenDataSource(keyValueDataSource)
    }

    @Test
    fun `saveToken stores token in KeyValueDataSource and updates memory`() =
        runTest {
            // Given
            val token = "test-token"

            // When
            userTokenDataSource.saveToken(token)

            // Then
            assertEquals(token, userTokenDataSource.getToken())
            coVerify {
                keyValueDataSource.setStringByKey(
                    ApplicationPreferenceKey.UserAuthenticationToken.key,
                    token,
                )
            }
        }

    @Test
    fun `loadToken retrieves token from KeyValueDataSource and updates memory`() =
        runTest {
            // Given
            val token = "persisted-token"
            coEvery {
                keyValueDataSource.getStringByKey(ApplicationPreferenceKey.UserAuthenticationToken.key)
            } returns flowOf(token)

            // When
            val result = userTokenDataSource.loadToken()

            // Then
            assertEquals(token, result)
            assertEquals(token, userTokenDataSource.getToken())
        }

    @Test
    fun `saveToken with null clears currentToken and deletes from KeyValueDataSource`() =
        runTest {
            // Given: a token is saved first
            userTokenDataSource.saveToken("existing-token")

            // When: logout by saving null
            userTokenDataSource.saveToken(null)

            // Then: in-memory token is cleared
            assertNull(userTokenDataSource.getToken())
            // And: the persisted key is deleted (not set to null) to clear the cache entry
            coVerify {
                keyValueDataSource.deleteValueByKey(
                    ApplicationPreferenceKey.UserAuthenticationToken.key,
                )
            }
            coVerify(exactly = 0) {
                keyValueDataSource.setStringByKey(
                    ApplicationPreferenceKey.UserAuthenticationToken.key,
                    null,
                )
            }
        }
}
