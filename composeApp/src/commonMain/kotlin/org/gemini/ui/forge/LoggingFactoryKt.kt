package org.gemini.ui.forge

import org.gemini.ui.forge.utils.AppLogger

/**
 * Logger instantiation by function. Use: `val log = logger()`. The logger will be named according to the
 * receiver of the function, which can be a class or object. An alternative for explicitly named loggers is
 * the [logger(String)] function.
 */
//inline fun <reified T : Any> T.logger() = loggerOf(T::class.java)
//
///**
// * Provides a logger named after the receiver object's class.
// *
// * @since 1.3.0
// */
//inline val <reified T> T.logger: KotlinLogger
//    get() = cachedLoggerOf(T::class.java)
//
//
///**
// * Named logger instantiation by function. Use: `val log = logger('MyLoggerName')`. Generally one should
// * prefer the `logger` function to create automatically named loggers, but this is useful outside of objects,
// * such as in top-level functions.
// */
//fun logger(name: String): KotlinLogger = KotlinLogger(LogManager.getContext(false).getLogger(name))
//
///**
// * Returns normalized context name.
// * * Execution within a class/object will return the full qualified class/object name,
// *   in case of nested classes/objects the most outer class/object is used.
// * * Execution outside of any class/object will return the full qualified file name without `.kt suffix.
// *
// * Usage: `val LOG = logger(contextName {})`
// * @param context should always be `{}`
// * @return normalized context name
// */
//fun contextName(context: () -> Unit): String = with(context::class.java.name) {
//    when {
//        contains("Kt$") -> substringBefore("Kt$")
//        contains("$") -> substringBefore("$")
//        else -> this
//    }
//}
//
///**
// * @see [logger]
// */
//@Deprecated("Replaced with logger(name)", replaceWith = ReplaceWith("logger"), level = DeprecationLevel.WARNING)
//fun namedLogger(name: String): KotlinLogger = KotlinLogger(LogManager.getContext(false).getLogger(name))
//
//fun loggerDelegateOf(ofClass: Class<*>): ExtendedLogger {
//    return LogManager.getContext(ofClass.classLoader, false).getLogger(unwrapCompanionClass(ofClass).name)
//}
//
//fun loggerOf(ofClass: Class<*>): KotlinLogger {
//    return KotlinLogger(loggerDelegateOf(ofClass))
//}
//
//fun cachedLoggerOf(ofClass: Class<*>): KotlinLogger {
//    return loggerCache.getOrPut(ofClass) { loggerOf(ofClass) }
//}
//
//// unwrap companion class to enclosing class given a Java Class
//private fun <T : Any> unwrapCompanionClass(ofClass: Class<T>): Class<*> {
//    return if (ofClass.enclosingClass?.kotlin?.companionObject?.java == ofClass) {
//        ofClass.enclosingClass
//    } else {
//        ofClass
//    }
//}
//
//private val loggerCache = Collections.synchronizedMap(SimpleLoggerLruCache(100))
//
///**
// * A very simple cache for loggers, to be used with [cachedLoggerOf].
// */
//private class SimpleLoggerLruCache(private val maxEntries: Int): LinkedHashMap<Class<*>, KotlinLogger>(maxEntries, 1f) {
//    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Class<*>, KotlinLogger>): Boolean {
//        return size > maxEntries
//    }
//}
