package com.yc.yclogsdk

/** 单个日志文件的硬上限，包含文件头；单位为 MiB。 */
enum class LogFileSize(val bytes: Int) {
    MB_5(5 * 1024 * 1024), MB_10(10 * 1024 * 1024),
    MB_15(15 * 1024 * 1024), MB_20(20 * 1024 * 1024)
}
