package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.domain.repository.ScriptPreferenceRepository
import com.cereal.sdk.component.preference.PreferenceComponent
import kotlinx.coroutines.flow.firstOrNull

class PreferenceComponentImpl(
    private val scriptPackageInstanceId: String,
    private val scriptPreferenceRepository: ScriptPreferenceRepository,
) : PreferenceComponent {
    override suspend fun delete(key: String) {
        scriptPreferenceRepository.deleteValue(scriptPackageInstanceId, key)
    }

    override suspend fun getString(key: String): String? = scriptPreferenceRepository.getString(scriptPackageInstanceId, key).firstOrNull()

    override suspend fun setString(
        key: String,
        value: String,
    ) {
        scriptPreferenceRepository.setString(scriptPackageInstanceId, key, value)
    }

    override suspend fun getInt(key: String): Int? = scriptPreferenceRepository.getInt(scriptPackageInstanceId, key).firstOrNull()

    override suspend fun setInt(
        key: String,
        value: Int,
    ) {
        scriptPreferenceRepository.setInt(scriptPackageInstanceId, key, value)
    }

    override suspend fun getLong(key: String): Long? = scriptPreferenceRepository.getLong(scriptPackageInstanceId, key).firstOrNull()

    override suspend fun setLong(
        key: String,
        value: Long,
    ) {
        scriptPreferenceRepository.setLong(scriptPackageInstanceId, key, value)
    }

    override suspend fun getFloat(key: String): Float? = scriptPreferenceRepository.getFloat(scriptPackageInstanceId, key).firstOrNull()

    override suspend fun setFloat(
        key: String,
        value: Float,
    ) {
        scriptPreferenceRepository.setFloat(scriptPackageInstanceId, key, value)
    }

    override suspend fun getBoolean(key: String): Boolean? = scriptPreferenceRepository.getBoolean(scriptPackageInstanceId, key).firstOrNull()

    override suspend fun setBoolean(
        key: String,
        value: Boolean,
    ) {
        scriptPreferenceRepository.setBoolean(scriptPackageInstanceId, key, value)
    }
}
