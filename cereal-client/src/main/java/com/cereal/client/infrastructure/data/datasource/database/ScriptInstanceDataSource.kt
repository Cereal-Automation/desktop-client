package com.cereal.client.infrastructure.data.datasource.database

/**
 * Aggregate persistence contract for script instances, composed of three focused concerns:
 * - [ScriptPackageGroupDataSource]: script package groups
 * - [ScriptPackageInstanceDataSource]: script package instances and their child scripts
 * - [ScriptTaskDataSource]: tasks and task status history
 */
interface ScriptInstanceDataSource :
    ScriptPackageGroupDataSource,
    ScriptPackageInstanceDataSource,
    ScriptTaskDataSource
