package com.cereal.client.presentation.tasks.model

/** One row of Recent checks, ready to show. */
data class RecentCheckUiModel(
    val traceId: String,
    val label: String,
    /** When the check was received, formatted for the row. */
    val time: String,
    val failed: Boolean,
    /** The row shows "Reported". */
    val reported: Boolean,
    /** The row offers "Report wrong result". */
    val canReport: Boolean,
    /** The longest note the user can add when reporting a wrong result. */
    val noteMaxLength: Int,
)
