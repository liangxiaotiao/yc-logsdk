package com.yc.yclogsdk.internal

import android.util.Log

internal object LogcatPrinter {
    private val lock = Any()
    fun print(event: LogEvent) = synchronized(lock) {
        // Android 7.0+ 支持长 TAG；保持用户 TAG，不使用可能触发 fatal handler 的 Log.wtf。
        LogFormatter.logcat(event).forEach { Log.println(event.level.priority, event.tag, it) }
    }
}
