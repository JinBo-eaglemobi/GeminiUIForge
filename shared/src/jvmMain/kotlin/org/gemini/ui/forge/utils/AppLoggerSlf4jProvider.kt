package org.gemini.ui.forge.utils

import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.helpers.NOPMDCAdapter
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider

/**
 * SLF4J 2.x 的服务提供者，连接 SLF4J API 和项目的 AppLogger
 */
class AppLoggerSlf4jProvider : SLF4JServiceProvider {
    private val loggerFactory: ILoggerFactory = AppLoggerFactory()
    private val markerFactory: IMarkerFactory = BasicMarkerFactory()
    private val mdcAdapter: MDCAdapter = NOPMDCAdapter()

    override fun getLoggerFactory(): ILoggerFactory = loggerFactory
    override fun getMarkerFactory(): IMarkerFactory = markerFactory
    override fun getMDCAdapter(): MDCAdapter = mdcAdapter
    override fun getRequestedApiVersion(): String = "2.0.99"

    override fun initialize() {
        // Properties are initialized eagerly during class instantiation.
    }
}
