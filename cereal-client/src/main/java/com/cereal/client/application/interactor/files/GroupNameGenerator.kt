package com.cereal.client.application.interactor.files

import com.cereal.client.domain.model.script.Manifest
import java.text.SimpleDateFormat
import java.util.Date

fun createGroupName(manifest: Manifest): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
    return "Imported for ${manifest.name} at ${dateFormat.format(Date())}"
}
