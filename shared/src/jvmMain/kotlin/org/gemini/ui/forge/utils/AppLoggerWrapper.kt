package org.gemini.ui.forge.utils

import org.slf4j.helpers.MarkerIgnoringBase
import org.slf4j.helpers.MessageFormatter

/**
 * SLF4J Logger 的包装器，将所有日志调用转发给项目的 AppLogger
 */
@Suppress("DEPRECATION")
class AppLoggerWrapper(private val loggerName: String) : MarkerIgnoringBase() {
    
    override fun isTraceEnabled(): Boolean = true
    override fun trace(msg: String?) {
        if (msg != null) AppLogger.d(loggerName, msg)
    }
    override fun trace(format: String?, arg: Any?) = AppLogger.d(loggerName, formatMessage(format, arg))
    override fun trace(format: String?, arg1: Any?, arg2: Any?) = AppLogger.d(loggerName, formatMessage(format, arg1, arg2))
    override fun trace(format: String?, vararg arguments: Any?) = AppLogger.d(loggerName, formatMessage(format, *arguments))
    override fun trace(msg: String?, t: Throwable?) = AppLogger.d(loggerName, msg ?: "", t)

    override fun isDebugEnabled(): Boolean = true
    override fun debug(msg: String?) {
        if (msg != null) AppLogger.d(loggerName, msg)
    }
    override fun debug(format: String?, arg: Any?) = AppLogger.d(loggerName, formatMessage(format, arg))
    override fun debug(format: String?, arg1: Any?, arg2: Any?) = AppLogger.d(loggerName, formatMessage(format, arg1, arg2))
    override fun debug(format: String?, vararg arguments: Any?) = AppLogger.d(loggerName, formatMessage(format, *arguments))
    override fun debug(msg: String?, t: Throwable?) = AppLogger.d(loggerName, msg ?: "", t)

    override fun isInfoEnabled(): Boolean = true
    override fun info(msg: String?) {
        if (msg != null) AppLogger.i(loggerName, msg)
    }
    override fun info(format: String?, arg: Any?) = AppLogger.i(loggerName, formatMessage(format, arg))
    override fun info(format: String?, arg1: Any?, arg2: Any?) = AppLogger.i(loggerName, formatMessage(format, arg1, arg2))
    override fun info(format: String?, vararg arguments: Any?) = AppLogger.i(loggerName, formatMessage(format, *arguments))
    override fun info(msg: String?, t: Throwable?) = AppLogger.i(loggerName, msg ?: "", t)

    override fun isWarnEnabled(): Boolean = true
    override fun warn(msg: String?) {
        if (msg != null) AppLogger.i(loggerName, "WARN: $msg")
    }
    override fun warn(format: String?, arg: Any?) = AppLogger.i(loggerName, "WARN: ${formatMessage(format, arg)}")
    override fun warn(format: String?, arg1: Any?, arg2: Any?) = AppLogger.i(loggerName, "WARN: ${formatMessage(format, arg1, arg2)}")
    override fun warn(format: String?, vararg arguments: Any?) = AppLogger.i(loggerName, "WARN: ${formatMessage(format, *arguments)}")
    override fun warn(msg: String?, t: Throwable?) = AppLogger.i(loggerName, "WARN: $msg", t)

    override fun isErrorEnabled(): Boolean = true
    override fun error(msg: String?) {
        if (msg != null) AppLogger.e(loggerName, msg)
    }
    override fun error(format: String?, arg: Any?) = AppLogger.e(loggerName, formatMessage(format, arg))
    override fun error(format: String?, arg1: Any?, arg2: Any?) = AppLogger.e(loggerName, formatMessage(format, arg1, arg2))
    override fun error(format: String?, vararg arguments: Any?) = AppLogger.e(loggerName, formatMessage(format, *arguments))
    override fun error(msg: String?, t: Throwable?) = AppLogger.e(loggerName, msg ?: "", t)

    private fun formatMessage(format: String?, vararg args: Any?): String {
        return MessageFormatter.arrayFormat(format, args).message ?: ""
    }
}
