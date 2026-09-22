package com.cereal.client.infrastructure.data.datasource.database.room

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Runs [block] inside an immediate write transaction on a writer connection.
 *
 * Replaces the repeated `useWriterConnection { transactor -> transactor.immediateTransaction { ... } }`
 * boilerplate in the Room data sources. The value returned by [block] is propagated, so callers can
 * return a result directly instead of capturing it in a mutable variable.
 */
suspend fun <R> RoomDatabase.immediateWriteTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor ->
        transactor.immediateTransaction {
            block()
        }
    }
