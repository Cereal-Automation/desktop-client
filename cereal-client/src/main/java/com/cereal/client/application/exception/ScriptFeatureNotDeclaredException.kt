package com.cereal.client.application.exception

import com.cereal.client.domain.model.script.ScriptFeature

/** A script used a Script feature without declaring it. A [CerealException], so the task ends as an expected error. */
class ScriptFeatureNotDeclaredException(
    val feature: ScriptFeature,
) : CerealException(
        "This script uses ${feature.manifestValue} but doesn't declare it. The script author must add " +
            "\"${feature.manifestValue}\" to \"features\" in manifest.json.",
    )
