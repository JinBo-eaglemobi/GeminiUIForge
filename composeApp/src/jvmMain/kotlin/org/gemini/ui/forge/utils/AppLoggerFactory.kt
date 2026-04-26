package org.gemini.ui.forge.utils

import org.slf4j.ILoggerFactory
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * 产生 AppLoggerWrapper 实例的工厂类
 */
class AppLoggerFactory : ILoggerFactory {
    private val loggerMap = ConcurrentHashMap<String, Logger>()

    override fun getLogger(name: String): Logger {
        return loggerMap.getOrPut(name) { AppLoggerWrapper(name) }
    }
}
