package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.featureflag.FeatureFlag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FeatureFlagRepositoryImplTest {
    @Test
    fun `debug build resolves every flag to its debug default`() {
        val repository = FeatureFlagRepositoryImpl(isDebugBuild = true)

        FeatureFlag.entries.forEach { flag ->
            assertEquals(flag.debug, repository.isEnabled(flag), "Unexpected debug state for $flag")
        }
    }

    @Test
    fun `release build resolves every flag to its release default`() {
        val repository = FeatureFlagRepositoryImpl(isDebugBuild = false)

        FeatureFlag.entries.forEach { flag ->
            assertEquals(flag.release, repository.isEnabled(flag), "Unexpected release state for $flag")
        }
    }
}
