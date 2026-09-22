package com.cereal.client.infrastructure.data.datasource.csv

import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import java.io.File
import kotlin.reflect.full.memberProperties

class CsvWriter {
    inline fun <reified T : Any> write(file: File) {
        val headers = T::class.memberProperties.map { it.name }

        val rows = listOf(headers)
        csvWriter().writeAll(rows, file)
    }

    fun write(
        file: File,
        data: List<List<Any>>,
    ) {
        csvWriter().writeAll(data, file)
    }
}
