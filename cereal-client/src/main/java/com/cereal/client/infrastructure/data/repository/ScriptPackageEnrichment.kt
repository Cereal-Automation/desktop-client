package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.infrastructure.data.datasource.filesystem.ScriptPackageDefinition
import com.cereal.client.infrastructure.data.datasource.filesystem.toScriptPackage
import com.cereal.client.infrastructure.data.datasource.network.SubscriptionDataSource
import com.cereal.client.infrastructure.data.datasource.network.getSupportUrl
import com.cereal.client.infrastructure.data.datasource.network.getSupportUrlByPackageName

// Enriches script package definitions (which live in the filesystem data source) with the support
// URL that only the subscription back end knows, producing domain ScriptPackages.
//
// This composition spans two data sources, so it belongs in the data layer rather than the domain.
// It is centralised here so ScriptRepositoryImpl and ScriptInstanceRepositoryImpl agree on how the
// overlay is applied instead of repeating the toScriptPackage(supportUrl) lookup inline.

/** Enriches a single definition, fetching the support URL for its package. */
internal suspend fun SubscriptionDataSource.enrich(definition: ScriptPackageDefinition): ScriptPackage = definition.toScriptPackage(getSupportUrl(definition.manifest.packageName))

/** Enriches a list of definitions, fetching the support-URL map once. */
internal suspend fun SubscriptionDataSource.enrich(definitions: List<ScriptPackageDefinition>): List<ScriptPackage> = definitions.toScriptPackages(getSupportUrlByPackageName())

/**
 * Enriches definitions and keys them by package name, fetching the support-URL map once.
 * Used when a downstream data source needs to look definitions up by package name.
 */
internal suspend fun SubscriptionDataSource.enrichByPackageName(
    definitions: List<ScriptPackageDefinition>,
): Map<String, ScriptPackage> = definitions.toScriptPackagesByPackageName(getSupportUrlByPackageName())

/**
 * Pure overlay of an already-fetched support-URL map onto a list of definitions. Use this (rather
 * than [enrich]) inside a [kotlinx.coroutines.flow.Flow] mapping so the map is fetched once and
 * reused across emissions instead of re-querying the back end per emission.
 */
internal fun List<ScriptPackageDefinition>.toScriptPackages(
    supportUrlByPackageName: Map<String, String?>,
): List<ScriptPackage> = map { it.toScriptPackage(supportUrlByPackageName[it.manifest.packageName]) }

/** Pure overlay keyed by package name. See [toScriptPackages] for the fetch-once rationale. */
internal fun List<ScriptPackageDefinition>.toScriptPackagesByPackageName(
    supportUrlByPackageName: Map<String, String?>,
): Map<String, ScriptPackage> =
    associateBy { it.manifest.packageName }
        .mapValues { it.value.toScriptPackage(supportUrlByPackageName[it.key]) }
