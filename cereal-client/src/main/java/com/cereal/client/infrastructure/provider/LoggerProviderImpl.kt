package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.domain.provider.LoggerProvider
import com.cereal.client.domain.repository.LogEventRepository
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * Formats log messages and emits them to the SLF4J sink, delegating persistence of the resulting
 * [LoggingEvent] to [LogEventRepository].
 */
class LoggerProviderImpl(
    private val logEventRepository: LogEventRepository,
) : LoggerProvider {
    private val logger = LoggerFactory.getLogger(LoggerProviderImpl::class.java)

    override fun error(
        tag: String,
        message: String?,
        throwable: Throwable?,
        vararg args: Any?,
    ) {
        val logEvent =
            createLog(
                priority = LoggingPriority.ERROR,
                throwable = throwable,
                tag = tag,
                message = message,
                args = args,
            ) ?: return

        logger.error("[$tag] ${logEvent.message}")
        logEventRepository.persist(logEvent)
    }

    override fun warning(
        tag: String,
        message: String?,
        vararg args: Any?,
    ) {
        val logEvent =
            createLog(
                priority = LoggingPriority.WARNING,
                throwable = null,
                tag = tag,
                message = message,
                args = args,
            ) ?: return

        logger.warn("[$tag] ${logEvent.message}")
        logEventRepository.persist(logEvent)
    }

    override fun info(
        tag: String,
        message: String?,
        vararg args: Any?,
    ) {
        val logEvent =
            createLog(
                priority = LoggingPriority.INFO,
                throwable = null,
                tag = tag,
                message = message,
                args = args,
            ) ?: return

        logger.info("[$tag] ${logEvent.message}")
        logEventRepository.persist(logEvent)
    }

    override fun debug(
        tag: String,
        message: String?,
        vararg args: Any?,
    ) {
        val logEvent =
            createLog(
                priority = LoggingPriority.DEBUG,
                throwable = null,
                tag = tag,
                message = message,
                args = args,
            ) ?: return

        logger.debug("[$tag] ${logEvent.message}")
        logEventRepository.persist(logEvent)
    }

    private fun createLog(
        priority: LoggingPriority,
        throwable: Throwable?,
        tag: String,
        message: String?,
        vararg args: Any?,
    ): LoggingEvent? {
        var formattedMessage = message
        if (formattedMessage.isNullOrEmpty()) {
            if (throwable == null) {
                return null
            }
            formattedMessage = getStackTraceString(throwable)
        } else {
            if (args.isNotEmpty()) {
                formattedMessage = formatMessage(formattedMessage, args)
            }
            if (throwable != null) {
                formattedMessage += "\n" + getStackTraceString(throwable)
            }
        }

        return LoggingEvent(
            priority = priority,
            tag = tag,
            message = formattedMessage,
            timestamp = Date(),
        )
    }

    private fun formatMessage(
        message: String,
        args: Array<out Any?>,
    ) = message.format(*args)

    private fun getStackTraceString(t: Throwable): String {
        val sw = StringWriter(STACK_TRACE_BUFFER_SIZE)
        val pw = PrintWriter(sw, false)
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }

    private companion object {
        private const val STACK_TRACE_BUFFER_SIZE = 256
    }
}
