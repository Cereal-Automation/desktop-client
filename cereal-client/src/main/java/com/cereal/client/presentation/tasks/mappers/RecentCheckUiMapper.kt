package com.cereal.client.presentation.tasks.mappers

import com.cereal.client.application.agent.FailureReporter
import com.cereal.client.application.agent.RecentCheck
import com.cereal.client.presentation.tasks.model.RecentCheckUiModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

private val checkTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

@OptIn(ExperimentalTime::class)
fun RecentCheck.toUiModel(zone: ZoneId = ZoneId.systemDefault()): RecentCheckUiModel =
    RecentCheckUiModel(
        traceId = trace.id,
        label = trace.label,
        time = checkTimeFormatter.withZone(zone).format(trace.receivedAt.toJavaInstant()),
        failed = trace.failed,
        reported = reported,
        canReport = canReport,
        noteMaxLength = FailureReporter.MAX_NOTE_LENGTH,
    )
