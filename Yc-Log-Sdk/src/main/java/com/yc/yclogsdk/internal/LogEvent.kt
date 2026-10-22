package com.yc.yclogsdk.internal

import com.yc.yclogsdk.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class Caller(val fileName: String, val lineNumber: Int, val className: String, val methodName: String) {
    val tag: String get() = className.substringAfterLast('.').substringBefore('$').ifEmpty { "Logger" }
    val simpleName: String get() = tag
    /** 去掉编译器合成后缀：`onAdClose$lambda$3` -> `onAdClose`，Java `lambda$run$0` -> `run`。 */
    val simpleMethod: String get() = methodName.split('$').firstOrNull { it.isNotEmpty() && it != "lambda" } ?: methodName
    val location: String get() = "$fileName:$lineNumber"
}

/** 日期、格式化时间、线程和位置在调用侧捕获，异步 IO 不重新生成。 */
internal data class LogEvent(val millis: Long, val level: LogLevel, val thread: String, val tag: String,
                            val callers: List<Caller>, val message: String) {
    val date: LogDate = LogDate.fromMillis(millis)
    val timestamp: String = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date(millis))
}
