package com.cereal.client.infrastructure.di.modules

import com.cereal.client.application.interactor.notification.SendNotificationFromScriptInstanceInteractor
import com.cereal.client.domain.model.notification.NotificationResolver
import com.cereal.client.domain.model.script.getScriptPackageInstance
import com.cereal.client.domain.model.task.Task
import com.cereal.client.infrastructure.sdkcomponent.ArtifactComponentImpl
import com.cereal.client.infrastructure.sdkcomponent.ComponentProviderImpl
import com.cereal.client.infrastructure.sdkcomponent.LoggerComponentImpl
import com.cereal.client.infrastructure.sdkcomponent.NotificationComponentImpl
import com.cereal.client.infrastructure.sdkcomponent.UserInteractionComponentImpl
import com.cereal.sdk.component.ComponentProvider
import com.cereal.sdk.component.artifact.ArtifactComponent
import com.cereal.sdk.component.logger.LoggerComponent
import com.cereal.sdk.component.notification.NotificationComponent
import com.cereal.sdk.component.userinteraction.UserInteractionComponent
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

object TaskScopeModule {
    val modules =
        module {
            scope<Task> {
                scoped<LoggerComponent> {
                    val task = getSource<Task>() ?: throw RuntimeException("Task is required")
                    LoggerComponentImpl(task.id, get())
                }
                scoped { SendNotificationFromScriptInstanceInteractor(get(), get(), get(), NotificationResolver()) }
                scoped<NotificationComponent> {
                    val task = getSource<Task>() ?: throw RuntimeException("Task is required")
                    val packageInstance = task.scriptInstance.getScriptPackageInstance()
                    NotificationComponentImpl(get(), packageInstance, task.id)
                }
                scoped<ComponentProvider> {
                    val task = getSource<Task>() ?: throw RuntimeException("Task is required")
                    val manifest =
                        task.scriptInstance
                            .getScriptPackageInstance()
                            .definition.manifest
                    ComponentProviderImpl(
                        get { parametersOf(manifest.name) },
                        get(),
                        get(),
                        get { (parametersOf(manifest)) },
                        get(),
                        get(),
                        get(),
                    )
                }
                scoped<UserInteractionComponent> {
                    UserInteractionComponentImpl(
                        get(),
                        getSource<Task>()?.id ?: throw RuntimeException("Task is required"),
                        get(),
                    )
                }
                scoped<ArtifactComponent> {
                    val task = getSource<Task>() ?: throw RuntimeException("Task is required")
                    ArtifactComponentImpl(get(), task.id)
                }
            }
        }
}
