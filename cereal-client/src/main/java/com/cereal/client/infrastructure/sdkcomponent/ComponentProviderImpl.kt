package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.sdk.component.ComponentProvider
import com.cereal.sdk.component.artifact.ArtifactComponent
import com.cereal.sdk.component.license.LicenseComponent
import com.cereal.sdk.component.logger.LoggerComponent
import com.cereal.sdk.component.notification.NotificationComponent
import com.cereal.sdk.component.preference.PreferenceComponent
import com.cereal.sdk.component.script.ScriptLauncherComponent
import com.cereal.sdk.component.userinteraction.UserInteractionComponent

class ComponentProviderImpl(
    private val loggerComponentImpl: LoggerComponent,
    private val licenseComponent: LicenseComponent,
    private val notificationComponent: NotificationComponent,
    private val preferenceComponent: PreferenceComponent,
    private val scriptLauncherComponent: ScriptLauncherComponent,
    private val userInteractionComponent: UserInteractionComponent,
    private val artifactComponent: ArtifactComponent,
) : ComponentProvider {
    override fun logger(): LoggerComponent = loggerComponentImpl

    override fun license(): LicenseComponent = licenseComponent

    override fun notification(): NotificationComponent = notificationComponent

    override fun preference(): PreferenceComponent = preferenceComponent

    override fun scriptLauncher(): ScriptLauncherComponent = scriptLauncherComponent

    override fun userInteraction(): UserInteractionComponent = userInteractionComponent

    override fun artifact(): ArtifactComponent = artifactComponent
}
