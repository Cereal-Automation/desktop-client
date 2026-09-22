package com.cereal.client.infrastructure.data.datasource.database

/**
 * Enum to specify which database implementation to use
 */
enum class DatabaseImplementation {
    ROOM,
}

/**
 * Interface for providing database-specific datasource implementations
 */
interface DatabaseProvider {
    fun getArtifactDataSource(): ArtifactDataSource

    fun getDatasetDataSource(): DatasetDataSource

    fun getKeyValueDataSource(): KeyValueDataSource

    fun getProxyDataSource(): ProxyDataSource

    fun getProxyProviderConnectorDataSource(): ProxyProviderConnectorDataSource

    fun getScriptInstanceDataSource(): ScriptInstanceDataSource

    fun getScriptPreferenceDataSource(): ScriptPreferenceDataSource

    fun getLogEventDataSource(): LogEventDataSource

    fun getNotificationHistoryDataSource(): NotificationHistoryDataSource
}
