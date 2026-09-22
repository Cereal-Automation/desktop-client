package com.cereal.client.infrastructure.data.datasource.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ProxyProviderConnectorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyProviderConnectorDao {
    /** Inserts or overwrites the connector record for a provider (replace-token re-uses this). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProxyProviderConnectorEntity)

    @Query("SELECT * FROM proxy_provider_connector WHERE provider = :provider")
    fun observeByProvider(provider: String): Flow<ProxyProviderConnectorEntity?>

    @Query("DELETE FROM proxy_provider_connector WHERE provider = :provider")
    suspend fun deleteByProvider(provider: String)
}
