package com.cereal.client.presentation.tasks.mappers

import com.cereal.client.application.agent.FailureReporter
import com.cereal.client.application.agent.RecentCheck
import com.cereal.client.presentation.tasks.model.RecentCheckUiModel
import java.time.ZoneId
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

@OptIn(ExperimentalTime::class)
fun RecentCheck.toUiModel(zone: ZoneId = ZoneId.systemDefault()): RecentCheckUiModel =
    RecentCheckUiModel(
        traceId = trace.id,
        label = trace.label,
        time = dateFormatter.withZone(zone).format(trace.receivedAt.toJavaInstant()),
        failed = trace.failed,
        reported = reported,
        canReport = canReport,
        noteMaxLength = FailureReporter.MAX_NOTE_LENGTH,
    )
