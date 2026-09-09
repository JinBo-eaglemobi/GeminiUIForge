package org.gemini.ui.forge.extend

import kotlin.contracts.*

/**
 * 如果集合非空，则调用默认值提供函数
 *
 * 此函数提供了一种简洁的方式来检查集合是否非空，并在非空的情况下执行一段代码
 * 它使用了Kotlin的内联函数和SAM转换来确保性能和可读性
 *
 * @param T 集合中元素的类型
 * @param defaultValue 当集合非空时调用的函数，返回单位类型Unit
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> Collection<T>.ifNotEmpty(defaultValue: () -> Unit) {
    // 告诉Kotlin编译器，defaultValue函数最多会被调用一次
    contract {
        callsInPlace(defaultValue, InvocationKind.AT_MOST_ONCE)
    }
    // 检查集合是否非空，如果是，则调用defaultValue函数
    if (isNotEmpty()) defaultValue()
}
