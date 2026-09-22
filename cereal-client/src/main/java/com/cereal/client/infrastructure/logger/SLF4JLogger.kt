package com.cereal.client.infrastructure.logger

import org.koin.core.KoinApplication
import org.koin.core.logger.KOIN_TAG
import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE
import org.slf4j.LoggerFactory

class SLF4JLogger(
    level: Level = Level.INFO,
) : Logger(level) {
    private val logger: org.slf4j.Logger = LoggerFactory.getLogger(KOIN_TAG)

    override fun display(
        level: Level,
        msg: MESSAGE,
    ) {
        if (this.level <= level) {
            logOnLevel(level, msg)
        }
    }

    private fun logOnLevel(
        level: Level,
        msg: MESSAGE,
    ) {
        when (level) {
            Level.DEBUG -> logger.debug(msg)
            Level.INFO -> logger.info(msg)
            Level.ERROR -> logger.error(msg)
            else -> logger.error(msg)
        }
    }
}

fun KoinApplication.slf4jLogger(level: Level = Level.INFO) {
    logger(SLF4JLogger(level))
}
