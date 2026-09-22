package com.cereal.client.infrastructure.data.datasource.database.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ProxyEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ProxyGroupEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ProxyGroupWithCountEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for proxy operations.
 *
 * Large DAO interface with many query methods; grouping them here keeps related operations together.
 */
@Suppress("TooManyFunctions")
@Dao
interface ProxyDao {
    // Proxy operations
    @Query("SELECT * FROM proxy WHERE id = :id")
    suspend fun getProxyById(id: String): ProxyEntity?

    @Query("SELECT * FROM proxy WHERE group_id = :groupId")
    suspend fun getProxiesByGroupId(groupId: String): List<ProxyEntity>

    @Query("SELECT * FROM proxy WHERE group_id = :groupId")
    fun getProxiesByGroupIdFlow(groupId: String): Flow<List<ProxyEntity>>

    @Query("SELECT COUNT(*) FROM proxy WHERE group_id = :groupId")
    suspend fun getProxiesCountByGroupId(groupId: String): Long

    @Query("SELECT * FROM proxy")
    fun getAllProxiesFlow(): Flow<List<ProxyEntity>>

    @Query("DELETE FROM proxy WHERE id = :id")
    suspend fun deleteProxyById(id: String)

    @Query("DELETE FROM proxy WHERE group_id = :groupId")
    suspend fun deleteAllProxiesByGroupId(groupId: String)

    @Query("DELETE FROM proxy WHERE group_id = :groupId AND health_status = :status")
    suspend fun deleteProxiesByGroupIdAndStatus(
        groupId: String,
        status: String,
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProxy(entity: ProxyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProxies(entities: List<ProxyEntity>)

    @Update
    suspend fun updateProxy(entity: ProxyEntity)

    @Delete
    suspend fun deleteProxy(entity: ProxyEntity)

    @Query(
        """
        UPDATE proxy
        SET health_status = :status,
            last_checked_at = :lastCheckedAt,
            latency_ms = :latencyMs,
            last_error = :lastError
        WHERE id = :id
    """,
    )
    suspend fun updateProxyHealth(
        id: String,
        status: String,
        lastCheckedAt: Long,
        latencyMs: Long?,
        lastError: String?,
    )

    @Query("SELECT * FROM proxy WHERE last_checked_at IS NULL OR last_checked_at < :olderThan")
    suspend fun getStaleProxies(olderThan: Long): List<ProxyEntity>

    // Proxy Group operations
    @Query("SELECT * FROM proxy_group WHERE id = :id")
    suspend fun getProxyGroupById(id: String): ProxyGroupEntity?

    @Query("SELECT * FROM proxy_group")
    fun getAllProxyGroupsFlow(): Flow<List<ProxyGroupEntity>>

    @Query(
        """
        SELECT pg.*, COUNT(p.id) as proxy_count
        FROM proxy_group pg
        LEFT JOIN proxy p ON pg.id = p.group_id
        GROUP BY pg.id
    """,
    )
    suspend fun getProxyGroupsWithCounts(): List<ProxyGroupWithCountEntity>

    @Query(
        """
        SELECT pg.*, COUNT(p.id) as proxy_count
        FROM proxy_group pg
        LEFT JOIN proxy p ON pg.id = p.group_id
        GROUP BY pg.id
    """,
    )
    fun getProxyGroupsWithCountsFlow(): Flow<List<ProxyGroupWithCountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProxyGroup(entity: ProxyGroupEntity)

    @Update
    suspend fun updateProxyGroup(entity: ProxyGroupEntity)

    @Delete
    suspend fun deleteProxyGroup(entity: ProxyGroupEntity)
}
