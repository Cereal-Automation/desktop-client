package com.cereal.client.application.interactor.exception

import com.cereal.client.application.exception.CerealException

class ScriptSyncException(
    val exceptions: Map<String, Exception>,
) : CerealException("Failed to sync packages.") {
    override fun toString(): String {
        val stringBuilder = StringBuilder("Failed to sync packages:")
        exceptions.forEach {
            stringBuilder.append("\n- ${it.key}")
        }
        return stringBuilder.toString()
    }
}
