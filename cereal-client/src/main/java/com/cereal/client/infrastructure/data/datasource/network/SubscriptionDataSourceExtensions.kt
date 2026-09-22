package com.cereal.client.infrastructure.data.datasource.network

/**
 * Returns a map of script package name (public identifier) to its support URL for every
 * subscription. Used to enrich script definitions, which live in a different data source, with
 * the support URL that is only known to the subscription back end.
 */
suspend fun SubscriptionDataSource.getSupportUrlByPackageName(): Map<String, String?> = getSubscriptions().associate { it.entitlement.publicIdentifier to it.entitlement.supportUrl }

/**
 * Returns the support URL for a single script package name (public identifier), or `null` when
 * there is no matching subscription or the subscription has no support URL.
 */
suspend fun SubscriptionDataSource.getSupportUrl(packageName: String): String? = getSubscriptions().find { it.entitlement.publicIdentifier == packageName }?.entitlement?.supportUrl
