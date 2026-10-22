package com.yc.yclogsdk.internal

internal object CallerResolver {
    /** 宿主封装类（如 MLog）全名；匹配本类及其内部类/companion。写时复制，读无锁。 */
    @Volatile var skipped: List<String> = emptyList()

    fun resolve(frames: Array<StackTraceElement>, depth: Int): List<Caller> = frames.asSequence()
        .filterNot {
            it.className == "java.lang.Thread" || it.className == "dalvik.system.VMStack" ||
                it.className.startsWith(CallerResolver::class.java.name) ||
                it.className == "com.yc.yclogsdk.Logger" || it.className.startsWith("com.yc.yclogsdk.Logger\$") ||
                skipped.any { name -> it.className == name || it.className.startsWith("$name\$") }
        }
        .take(depth.coerceAtLeast(1))
        .map { Caller(it.fileName ?: "Unknown", it.lineNumber, it.className, it.methodName) }
        .toList().ifEmpty { listOf(Caller("Unknown", -1, "Logger", "unknown")) }
}
