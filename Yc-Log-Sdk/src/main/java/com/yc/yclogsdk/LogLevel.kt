package com.yc.yclogsdk

/** 同时过滤 Logcat 和文件；ASSERT 使用 println，避免 wtf 触发宿主崩溃策略。 */
enum class LogLevel(val priority: Int, val letter: String) {
    VERBOSE(2, "V"), DEBUG(3, "D"), INFO(4, "I"), WARN(5, "W"), ERROR(6, "E"), ASSERT(7, "A")
}
