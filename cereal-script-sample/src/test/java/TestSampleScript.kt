import com.cereal.script.sample.SampleConfiguration
import com.cereal.script.sample.SampleScript
import com.cereal.script.sample.SampleTarget
import com.cereal.script.sample.SampleTargetSize
import com.cereal.script.sample.child.TestChildConfiguration
import com.cereal.script.sample.child.TestChildScript
import com.cereal.sdk.component.userinteraction.WebResourceRequest
import com.cereal.test.TestScriptRunner
import com.cereal.test.components.TestComponentProviderFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class TestSampleScript {
    @Test
    fun testSuccess() =
        runBlocking {
            // Initialize script and the test script runner.
            val script = SampleScript()
            val scriptRunner = TestScriptRunner(script)

            // Mock the configuration values
            val configuration =
                mockk<SampleConfiguration> {
                    every { keyStringNullable() } returns null
                    every { keyBooleanNullable() } returns null
                    every { keyIntegerNullable() } returns null
                    every { keyFloatNullable() } returns null
                    every { keyDoubleNullable() } returns null
                    every { dropdownOptionNullable() } returns null
                    // Complex lists have no default values, so a test supplies the rows itself.
                    every { targets() } returns
                        listOf(
                            object : SampleTarget {
                                override fun sku() = "ABC-123"

                                override fun quantity() = 2

                                override fun size() = SampleTargetSize.MEDIUM

                                override fun maxPrice() = 19.99

                                override fun notify() = true
                            },
                            object : SampleTarget {
                                override fun sku() = "XYZ-9"

                                override fun quantity() = 1

                                override fun size() = SampleTargetSize.LARGE

                                override fun maxPrice() = null

                                override fun notify() = null
                            },
                        )
                    every { randomProxyKeyCSVNullable() } returns null
                    every { keyStringCSV() } returns "foo"
                    every { keyIntegerCSV() } returns 1
                    every { keyFloatCSV() } returns 1f
                    every { keyDoubleCSV() } returns 1.0
                    every { keyStringNullableCSV() } returns null
                    every { keyIntegerNullableCSV() } returns null
                    every { keyFloatNullableCSV() } returns null
                    every { keyDoubleNullableCSV() } returns null
                    every { proxyKeyNullableCSV() } returns null
                    every { testLogger() } returns null
                    every { testPreferences() } returns null
                    every { testNotification() } returns null
                    every { testScriptLauncher() } returns null
                    every { testUserInteraction() } returns null
                    every { testLicense() } returns null
                    every { testArtifacts() } returns null
                    every { crashWithException() } returns false
                }
            val componentProviderFactory = TestComponentProviderFactory()
            val testChildScriptConfig =
                mockk<TestChildConfiguration> {
                    every { keyString() } returns "bar"
                }
            componentProviderFactory.childScriptConfigurations =
                mapOf(TestChildScript::class.java to testChildScriptConfig)

            componentProviderFactory.requestInputResults = listOf("Mocked User Input")
            componentProviderFactory.showHtmlResults =
                listOf(WebResourceRequest("GET", emptyMap(), "https://cereal.com/continue", null))
            componentProviderFactory.showUrlResults =
                listOf(WebResourceRequest("GET", emptyMap(), "https://iana.org", null))

            // Run our script
            try {
                withTimeout(10000) { scriptRunner.run(configuration, componentProviderFactory) }
            } catch (e: TimeoutCancellationException) {
                // Timeout exception is expected because else script will never return because of an infinit loop.
            }
        }
}
