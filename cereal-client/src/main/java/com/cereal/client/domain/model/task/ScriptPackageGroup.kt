package com.cereal.client.domain.model.task

import com.cereal.client.domain.model.exception.InvalidScriptPackageGroupException

data class ScriptPackageGroup(
    val id: String,
    val name: String,
    val totalScriptPackages: Int = 0,
) {
    init {
        if (id.isBlank()) throw InvalidScriptPackageGroupException("Id cannot be blank")
        if (name.isBlank()) throw InvalidScriptPackageGroupException("Name cannot be blank")
        if (name.length > MAX_NAME_LENGTH) throw InvalidScriptPackageGroupException("Name cannot be longer than $MAX_NAME_LENGTH characters")
        if (!name.matches(Regex("^[a-zA-Z0-9 _@.-]+$"))) throw InvalidScriptPackageGroupException("Name can only contain letters, numbers, spaces, hyphens, underscores, periods, and at signs")
        if (totalScriptPackages < 0) throw InvalidScriptPackageGroupException("TotalScriptPackages cannot be negative")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScriptPackageGroup) return false

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        const val DEFAULT_GROUP_NAME = "Default"
        private const val MAX_NAME_LENGTH = 50

        fun createDefault() =
            ScriptPackageGroup(
                id =
                    java.util.UUID
                        .randomUUID()
                        .toString(),
                name = DEFAULT_GROUP_NAME,
            )
    }
}
