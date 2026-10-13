package com.yc.yclogsdk.internal

internal object CallerResolver {
    fun resolve(frames: Array<StackTraceElement>, depth: Int): List<Caller> = frames.asSequence()
        .filterNot {
            it.className == "java.lang.Thread" || it.className == "dalvik.system.VMStack" ||
                it.className.startsWith(CallerResolver::class.java.name) ||
                it.className == "com.yc.yclogsdk.Logger" || it.className.startsWith("com.yc.yclogsdk.Logger\$")
        }
        .take(depth.coerceAtLeast(1))
        .map { Caller(it.fileName ?: "Unknown", it.lineNumber, it.className, it.methodName) }
        .toList().ifEmpty { listOf(Caller("Unknown", -1, "Logger", "unknown")) }
}
