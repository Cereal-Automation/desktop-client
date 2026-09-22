package com.cereal.client.infrastructure.data.datasource.database.room

import com.cereal.client.infrastructure.data.datasource.database.ArtifactDataSource
import com.cereal.client.infrastructure.data.datasource.database.DatabaseProvider
import com.cereal.client.infrastructure.data.datasource.database.DatasetDataSource
import com.cereal.client.infrastructure.data.datasource.database.KeyValueDataSource
import com.cereal.client.infrastructure.data.datasource.database.LogEventDataSource
import com.cereal.client.infrastructure.data.datasource.database.NotificationHistoryDataSource
import com.cereal.client.infrastructure.data.datasource.database.ProxyDataSource
import com.cereal.client.infrastructure.data.datasource.database.ProxyProviderConnectorDataSource
import com.cereal.client.infrastructure.data.datasource.database.ScriptInstanceDataSource
import com.cereal.client.infrastructure.data.datasource.database.ScriptPreferenceDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.DatasetMapper
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.KeyValueRoomMapper
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.ScriptInstanceMapper
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.ScriptNotificationOverrideMapper
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.ScriptPackageInstanceMapper
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Room implementation of DatabaseProvider
 */
class RoomDatabaseProvider(
    private val roomDatabases: RoomDatabases,
) : DatabaseProvider,
    KoinComponent {
    override fun getArtifactDataSource(): ArtifactDataSource = RoomArtifactDataSource(roomDatabases)

    override fun getDatasetDataSource(): DatasetDataSource {
        val datasetMapper = get<DatasetMapper>()
        return RoomDatasetDataSource(roomDatabases, datasetMapper)
    }

    override fun getKeyValueDataSource(): KeyValueDataSource = RoomKeyValueDataSource(roomDatabases)

    override fun getProxyDataSource(): ProxyDataSource = RoomProxyDataSource(roomDatabases)

    override fun getProxyProviderConnectorDataSource(): ProxyProviderConnectorDataSource = RoomProxyProviderConnectorDataSource(roomDatabases)

    override fun getScriptInstanceDataSource(): ScriptInstanceDataSource {
        // Create the required mappers
        val keyValueMapper =
            KeyValueRoomMapper(
                proxyDataSource = getProxyDataSource(),
                datasetDataSource = getDatasetDataSource(),
            )
        val scriptInstanceMapper = ScriptInstanceMapper(keyValueMapper, get(), get())
        val scriptPackageInstanceMapper = ScriptPackageInstanceMapper(keyValueMapper)
        val notificationOverrideMapper = ScriptNotificationOverrideMapper()

        return RoomScriptInstanceDataSource(
            RoomScriptPackageGroupDataSource(roomDatabases, scriptInstanceMapper),
            RoomScriptPackageInstanceDataSource(
                roomDatabases,
                scriptInstanceMapper,
                scriptPackageInstanceMapper,
                notificationOverrideMapper,
            ),
            RoomScriptTaskDataSource(roomDatabases, scriptInstanceMapper),
        )
    }

    override fun getScriptPreferenceDataSource(): ScriptPreferenceDataSource = RoomScriptPreferenceDataSource(roomDatabases)

    override fun getLogEventDataSource(): LogEventDataSource = RoomLogEventDataSource(roomDatabases)

    override fun getNotificationHistoryDataSource(): NotificationHistoryDataSource = RoomNotificationHistoryDataSource(roomDatabases)
}
