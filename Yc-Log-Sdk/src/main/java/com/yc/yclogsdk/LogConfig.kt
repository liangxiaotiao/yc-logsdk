package com.yc.yclogsdk

/** 初始化后不可变的配置。路径名称只能包含字母、数字、下划线、连字符。 */
data class LogConfig @JvmOverloads constructor(
    val name: String = "sdk",
    val dirName: String = "logs",
    val saveDays: Int = 7,
    val maxFileSize: LogFileSize = LogFileSize.MB_20,
    val stackDeep: Int = 1
) {
    init {
        require(name.matches(Regex("[\\p{L}\\p{N}_-]{1,80}"))) { "Invalid log name" }
        require(dirName.matches(Regex("[\\p{L}\\p{N}_-]{1,80}"))) { "Invalid directory name" }
        require(saveDays > 0) { "saveDays must be positive" }
        require(stackDeep > 0) { "stackDeep must be positive" }
    }
}
