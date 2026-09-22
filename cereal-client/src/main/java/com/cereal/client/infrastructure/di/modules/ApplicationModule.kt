package com.cereal.client.infrastructure.di.modules

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Environment
import com.cereal.client.application.auth.UserAuthManager
import com.cereal.client.application.script.ScriptInstanceFactory
import com.cereal.client.application.script.ScriptInstanceManager
import com.cereal.client.application.script.ScriptLicenseChecker
import com.cereal.client.application.script.ScriptManager
import com.cereal.client.application.script.ScriptSyncManager
import com.cereal.client.application.task.JobTaskFactory
import com.cereal.client.application.task.TaskConfigurationBuilder
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.ScopeLinker
import com.cereal.client.infrastructure.CerealConfiguration
import com.cereal.client.infrastructure.data.datasource.discord.DiscordSettings
import com.cereal.client.infrastructure.di.KoinScopeLinker
import com.cereal_automation.cereal_client.BuildConfig
import org.koin.dsl.module

object ApplicationModule {
    val modules =
        module {
            single<ApplicationConfig> {
                when (BuildConfig.ENVIRONMENT) {
                    Environment.ACCEPTANCE -> {
                        CerealConfiguration(
                            "https://marketplace.acceptance.cereal-automation.com/",
                            """-----BEGIN PUBLIC KEY-----
      MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAt3Aie1e/cjMeqDo0Xtam
      2jJpdu2NwT8375qzL40bbOZ2GtmvTQNl9/CWNQWNllBIRLlTFzK9dEHd65tYirpV
      dq18D8G5nCCLpVQ40XJ+FghM5IpMOgH4U1E2ZdsSrWpX1YAQfRtLRstXWoN25Odn
      d0d5MpVrCaUbp+uE0C35nqWVoWPVRROz2lrrd6imlKkVG/NXTfBeedSXgfKCXFpT
      g//fbPNyTcOIJNQKsmqDV5DiYsTEttyqBKcCcgCqKlT1AcTG0m5uzu4txrIYlUTf
      HNU7sCz4lvnVkx1N0ktLRXnNN3eBmOJTGg6+ASu3rEuxOewznLL1iJwwnATTcm4g
      NxWvN+Is6F0M72vLv2JEFdy+NNv78MjQVHuNIj0amkcoXC24XQGGzVQgBx+oHP4a
      7QMH9eilwVZAytBn/GGXKajQMSGjL6mNY29X3rSGGRTOWdjCO9v6gHE35QtE/7U6
      JmmFwQZHEe+MfxId25P0nRE11WyxBwD0JgOHUwfru0UigxQH6eq0Twa9WLa4FErv
      +jr0hebi4EvvjKDw2jh78LCibjjfB00x/0nH4ulWCDYnWO2GFLtdMCqY4c5tUweF
      yW564b/z51mlBmAk4asH4Ik1tm/+LWTMEr7hTFHA2PyznKc4x3qpJozBW+ToDsNN
      iAL/0dx0fgR/TkdfYCqnp4kCAwEAAQ==
      -----END PUBLIC KEY-----""",
                        )
                    }

                    Environment.LOCAL -> {
                        CerealConfiguration(
                            "http://localhost/",
                            """-----BEGIN PUBLIC KEY-----
    MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA7adgo9a2yvRXksyqiQBi
    3xh8BdtW2+S9XucsSv/yy4+NOoOO4TJqEPnVD5cVCnKPVb+JkbBTUD8gedceup4j
    UD4YsLWMbDIgTtSF4IQSo+J4tKcBhLNT/PndZHrEggjSgEN3IRQ/DYqfEP0YoGTO
    L+g4GNAlVjqCDwk8C8ax225ybAE5XgIOrm/78cOw8jG/sIzlZOVJQJF40k9LZgTQ
    z2A9Teki+hwE7hWc/ebAoKTfh4wuPXrP3AR2AiBp+2hQpDnykmjywOhKMxPT/gh5
    B8KenA4aOm/BhwYSJ+vEYU8n3/BCqHjcWxRL8Nm3hLmTDDXPNYevxttlQcoKhLEr
    za1Kg8gloy/seCkom2R7ydXen128crm4bhgFy2zJZcazR3/FDk6uCgYnZr/fARGj
    S5R7pqYc74fGftt35tZw/ee7h/LVkS0UiHFTQPe7jb53ybyV55ZpGk8kcSvp9oO2
    aAkEALEM2tZNCnF2FJAittugI6MrK4lLzZR5aVqEswAcN3DdqPqV6+sioEoA5eNn
    MDfcxStbP3TzvcNFpH1UZjw1zrKa6mD/0IntOiVYnsxZIEZRaZWJlgYqEwuNIRM1
    7GvuEbNAYm0E5OvpWbE51WVR+0uovaTmgWIEPrH+HbkBKcFdZXsx7sZy0wMePjz+
    ijsShofX3wx6qkeKeA0PSgUCAwEAAQ==
    -----END PUBLIC KEY-----""",
                        )
                    }

                    else -> {
                        CerealConfiguration()
                    }
                }
            }
            single<DiscordSettings> { DiscordSettings(get(), get()) }
            factory { CoroutinesDispatcherProvider() }
            factory { ScriptSyncManager(get(), get(), get(), get(), get(), get()) }
            factory { ScriptInstanceManager(get(), get(), get()) }
            factory { UserAuthManager(get(), get(), get(), get(), get(), get()) }
            single { TaskManager(get(), get(), get(), get(), get(), get()) }
            single { ScriptManager(get(), get(), get(), get()) }
            single { TaskConfigurationBuilder(get(), get(), get()) }
            single { ScriptLicenseChecker(get(), get()) }
            single<ScopeLinker> { KoinScopeLinker() }
            single { ScriptInstanceFactory(get()) }
            single { JobTaskFactory(get()) }
        }
}
