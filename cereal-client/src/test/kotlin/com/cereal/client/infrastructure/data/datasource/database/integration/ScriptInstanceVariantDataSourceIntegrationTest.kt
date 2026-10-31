package com.cereal.client.infrastructure.data.datasource.database.integration

import com.cereal.client.application.script.ScriptInstanceFactory
import com.cereal.client.application.task.JobTaskFactory
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.infrastructure.data.datasource.database.DatasetDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.RoomProxyDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.RoomScriptInstanceDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.RoomScriptPackageGroupDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.RoomScriptPackageInstanceDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.RoomScriptTaskDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.KeyValueRoomMapper
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.ScriptInstanceMapper
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.ScriptNotificationOverrideMapper
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.ScriptPackageInstanceMapper
import com.cereal.client.infrastructure.di.KoinScopeLinker
import fixtures.aScriptPackage
import fixtures.aScriptPackageInstance
import fixtures.aScriptPackageWithVariant
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Clock

private const val PACKAGE = "com.test.variants"

/** Variant persistence of the Room [RoomScriptInstanceDataSource]. */
class ScriptInstanceVariantDataSourceIntegrationTest : RoomUserDatabaseTestBase() {
    private val scriptInstanceDataSource by lazy {
        val keyValueMapper = KeyValueRoomMapper(RoomProxyDataSource(roomDatabases), mockk<DatasetDataSource>(relaxed = true))
        val scopeLinker = KoinScopeLinker()
        val scriptInstanceMapper = ScriptInstanceMapper(keyValueMapper, ScriptInstanceFactory(scopeLinker), JobTaskFactory(scopeLinker))
        RoomScriptInstanceDataSource(
            RoomScriptPackageGroupDataSource(roomDatabases, scriptInstanceMapper),
            RoomScriptPackageInstanceDataSource(
                roomDatabases,
                scriptInstanceMapper,
                ScriptPackageInstanceMapper(keyValueMapper),
                ScriptNotificationOverrideMapper(),
            ),
            RoomScriptTaskDataSource(roomDatabases, scriptInstanceMapper),
        )
    }

    @Test
    fun `test variant id round-trips`() =
        runTest {
            val (group, instance) = addVariantInstance(variantId = "checkout")

            val retrieved = scriptInstanceDataSource.getScriptPackageInstance(testUser, instance.id, instance.definition)

            assertEquals("checkout", retrieved!!.variantId)
            assertEquals(
                listOf("checkout"),
                scriptInstanceDataSource
                    .getScriptPackageInstancesInGroup(testUser, group.id, mapOf(PACKAGE to instance.definition))
                    .map { it.variantId },
            )
        }

    @Test
    fun `test instance of a removed variant fails to load`() =
        runTest {
            val (_, instance) = addVariantInstance(variantId = "checkout")
            val newerJar = aScriptPackageWithVariant(PACKAGE, variantId = "other")

            assertNull(scriptInstanceDataSource.getScriptPackageInstance(testUser, instance.id, newerJar))
            assertEquals(
                emptyList<ScriptPackageInstance>(),
                scriptInstanceDataSource.getScriptPackageInstances(testUser, mapOf(PACKAGE to newerJar)),
            )
        }

    @Test
    fun `test default-entry instance of a package that now has variants fails to load`() =
        runTest {
            val (_, instance) = addVariantInstance(variantId = null)
            val newerJar = aScriptPackageWithVariant(PACKAGE, variantId = "checkout")

            assertNull(scriptInstanceDataSource.getScriptPackageInstance(testUser, instance.id, newerJar))
            assertEquals(
                emptyList<ScriptPackageInstance>(),
                scriptInstanceDataSource.getScriptPackageInstances(testUser, mapOf(PACKAGE to newerJar)),
            )
        }

    /** Stores an instance of [variantId], or of the default entry of a package without variants when null. */
    private suspend fun addVariantInstance(variantId: String?): Pair<ScriptPackageGroup, ScriptPackageInstance> {
        val group = ScriptPackageGroup(id = UUID.randomUUID().toString(), name = "Test Script Group", totalScriptPackages = 0)
        scriptInstanceDataSource.createScriptInstanceGroup(testUser, group)
        val instance =
            aScriptPackageInstance(
                id = UUID.randomUUID().toString(),
                packageName = PACKAGE,
                definition = variantId?.let { aScriptPackageWithVariant(PACKAGE, variantId = it) } ?: aScriptPackage(PACKAGE),
                variantId = variantId,
            )
        val mainScriptInstance =
            MainScriptInstance(
                id = UUID.randomUUID().toString(),
                definition = instance.definition.mainScript,
                configuration = emptyMap(),
                createdAt = Clock.System.now(),
                packageInstance = instance,
            )
        scriptInstanceDataSource.addScriptPackageInstance(testUser, instance, mainScriptInstance, group.id)
        return group to instance
    }
}
