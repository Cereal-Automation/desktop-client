@file:OptIn(ExperimentalTime::class)

package com.cereal.client.domain.model.task

import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptInstance
import kotlinx.coroutines.Job
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class JobTask
    constructor(
        override val id: String,
        override val scriptInstance: ScriptInstance,
        override val configuration: ScriptConfigurationValues,
        override val statusHistory: List<TaskStatus> = emptyList(),
        override val userInteraction: UserInteraction? = null,
        override val createdAt: Instant,
        val job: Job? = null,
    ) : Task {
        override val status: TaskStatus
            get() = statusHistory.lastOrNull() ?: TaskStatus.Idle(timestamp = UNSET_STATUS_TIMESTAMP)

        /**
         * Returns a copy of this task with [status] appended to its history, applying the task
         * status policy:
         *
         * - **Dedup**: when [status] has the same message and type as the current last status, the
         *   status is considered a duplicate and this same instance is returned unchanged. Callers
         *   can use referential equality (`result === task`) to detect that nothing changed and skip
         *   persistence/notifications. This avoids unnecessary work and UI recomposition during
         *   scripts that loop rapidly (e.g. every 50 ms).
         * - **Cap**: only the most recent [MAX_STATUS_HISTORY_SIZE] entries are retained to prevent
         *   unbounded memory growth.
         */
        fun withStatus(status: TaskStatus): JobTask {
            val lastStatus = statusHistory.lastOrNull()
            if (lastStatus != null &&
                lastStatus.message == status.message &&
                lastStatus::class == status::class
            ) {
                return this
            }

            val newStatusHistory = (statusHistory + status).takeLast(MAX_STATUS_HISTORY_SIZE)
            return copy(statusHistory = newStatusHistory)
        }

        companion object {
            /**
             * Maximum number of status history entries retained per task. Older entries are dropped
             * when this limit is exceeded to prevent unbounded memory growth and continuous
             * StateFlow emissions caused by scripts that loop rapidly (e.g. every 50 ms).
             */
            const val MAX_STATUS_HISTORY_SIZE = 250

            /**
             * Placeholder timestamp for the synthetic [TaskStatus.Idle] returned when a task has no
             * status history yet. The domain must not read the wall clock, so this represents an
             * "unset" instant (epoch) rather than the current time.
             */
            private val UNSET_STATUS_TIMESTAMP = Instant.fromEpochMilliseconds(0)
        }
    }
