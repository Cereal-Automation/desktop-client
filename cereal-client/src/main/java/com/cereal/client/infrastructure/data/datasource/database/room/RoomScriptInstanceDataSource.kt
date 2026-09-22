package com.cereal.client.infrastructure.data.datasource.database.room

import com.cereal.client.infrastructure.data.datasource.database.ScriptInstanceDataSource
import com.cereal.client.infrastructure.data.datasource.database.ScriptPackageGroupDataSource
import com.cereal.client.infrastructure.data.datasource.database.ScriptPackageInstanceDataSource
import com.cereal.client.infrastructure.data.datasource.database.ScriptTaskDataSource

/**
 * Room implementation of [ScriptInstanceDataSource].
 *
 * Composes the three focused Room data sources (groups, package instances, tasks) and exposes
 * them through the single aggregate contract via Kotlin delegation.
 */
class RoomScriptInstanceDataSource(
    groupDataSource: ScriptPackageGroupDataSource,
    instanceDataSource: ScriptPackageInstanceDataSource,
    taskDataSource: ScriptTaskDataSource,
) : ScriptInstanceDataSource,
    ScriptPackageGroupDataSource by groupDataSource,
    ScriptPackageInstanceDataSource by instanceDataSource,
    ScriptTaskDataSource by taskDataSource
