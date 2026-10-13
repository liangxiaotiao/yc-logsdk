package com.yc.yclogsdk

import java.io.File

/** 导出失败时 file 为 null；error 保留原始异常，不抛给宿主。 */
class LogExportResult internal constructor(val file: File?, val error: Throwable?) {
    val isSuccess: Boolean get() = file != null && error == null
}

/** 回调在文件线程执行。更新 UI 时使用 runOnUiThread；回调异常不会终止日志队列。 */
fun interface LogExportCallback {
    fun onResult(result: LogExportResult)
}
