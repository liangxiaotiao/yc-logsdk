package com.yc.yclogsdk

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.yc.yclogsdk.internal.*
import java.util.concurrent.Executors

/**
 * 进程内共享日志入口。初始化前调用忽略；第一次有效且磁盘准备成功的初始化生效。
 * 文件操作在单线程后台执行；重复 init 不覆盖 App 已设置的名称/目录/保留策略。
 * Kotlin object API 通过 @JvmStatic / @JvmOverloads 同时支持 Java 静态调用。
 */
object Logger {
    private val worker = Executors.newSingleThreadExecutor { job ->
        Thread(job, "YcLog-Writer").apply { isDaemon = true }
    }
    private val engine = LogEngine(worker, LogcatPrinter::print)

    /** 同时过滤文件和 Logcat，默认 VERBOSE。初始化前设置忽略。 */
    @get:JvmStatic @set:JvmStatic
    var level: LogLevel
        get() = engine.level
        set(value) { engine.level = value }

    /** 默认依据宿主 debuggable；运行时修改不影响文件写入。 */
    @get:JvmStatic @set:JvmStatic
    var logcatEnabled: Boolean
        get() = engine.logcatEnabled
        set(value) { engine.logcatEnabled = value }

    /** 无效参数或运行环境异常安全忽略，后续允许再次初始化；不在调用线程访问磁盘。 */
    @JvmStatic @JvmOverloads
    fun init(context: Context, name: String = "sdk", dirName: String = "logs", saveDays: Int = 7,
             maxFileSize: LogFileSize = LogFileSize.MB_20, stackDeep: Int = 1) = safely {
        if (engine.isInitialized) return@safely
        val config = LogConfig(name, dirName, saveDays, maxFileSize, stackDeep)
        val app = context.applicationContext ?: context
        val debug = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        engine.init(config, debug) {
            LogFileManager(app.filesDir, it, AndroidFileHeader.create(app),
                isSymbolicLink = AndroidPathSafety::isSymbolicLink)
        }
    }

    @JvmStatic fun init(context: Context, config: LogConfig) =
        init(context, config.name, config.dirName, config.saveDays, config.maxFileSize, config.stackDeep)

    @JvmStatic fun v(vararg args: Any?) = write(LogLevel.VERBOSE, null, args)
    @JvmStatic fun d(vararg args: Any?) = write(LogLevel.DEBUG, null, args)
    @JvmStatic fun i(vararg args: Any?) = write(LogLevel.INFO, null, args)
    @JvmStatic fun w(vararg args: Any?) = write(LogLevel.WARN, null, args)
    @JvmStatic fun e(vararg args: Any?) = write(LogLevel.ERROR, null, args)
    @JvmStatic fun a(vararg args: Any?) = write(LogLevel.ASSERT, null, args)
    @JvmStatic fun wtf(vararg args: Any?) = write(LogLevel.ASSERT, null, args)

    @JvmStatic fun vTag(tag: String, vararg args: Any?) = write(LogLevel.VERBOSE, tag, args)
    @JvmStatic fun dTag(tag: String, vararg args: Any?) = write(LogLevel.DEBUG, tag, args)
    @JvmStatic fun iTag(tag: String, vararg args: Any?) = write(LogLevel.INFO, tag, args)
    @JvmStatic fun wTag(tag: String, vararg args: Any?) = write(LogLevel.WARN, tag, args)
    @JvmStatic fun eTag(tag: String, vararg args: Any?) = write(LogLevel.ERROR, tag, args)
    @JvmStatic fun aTag(tag: String, vararg args: Any?) = write(LogLevel.ASSERT, tag, args)
    @JvmStatic fun wtfTag(tag: String, vararg args: Any?) = write(LogLevel.ASSERT, tag, args)

    /** JSON/XML 格式异常回退原文，只格式化字符串，不序列化对象。默认 DEBUG 等级。 */
    @JvmStatic fun json(value: String) = jsonTag("", value)
    @JvmStatic fun json(tag: String, value: String) = jsonTag(tag, value)
    @JvmStatic fun jsonTag(tag: String, value: String) = write(LogLevel.DEBUG, tag, arrayOf(value)) {
        LogFormatter.json(value)
    }
    @JvmStatic fun xml(value: String) = xmlTag("", value)
    @JvmStatic fun xml(tag: String, value: String) = xmlTag(tag, value)
    @JvmStatic fun xmlTag(tag: String, value: String) = write(LogLevel.DEBUG, tag, arrayOf(value)) {
        LogFormatter.xml(value)
    }

    /** 可一次添加任意数量的键值对；覆盖同名键但保留其顺序，当天头在后台安全替换。 */
    @JvmStatic fun addFileHeader(vararg values: Pair<String, Any?>) =
        addFileHeader(linkedMapOf(*values))

    /** Java 使用 Map<String, ?>；跨天继承。过大头更新不会破坏旧文件。 */
    @JvmStatic fun addFileHeader(values: Map<String, Any?>) = safely {
        if (!engine.isInitialized) return@safely
        val headers = LinkedHashMap<String, String>()
        values.forEach { (key, value) -> headers[key] = LogFormatter.message(arrayOf(value)) }
        engine.addHeaders(headers)
    }

    /** 异步删除本 Logger 管理的今天之前的日志，当天及非托管文件保留。 */
    @JvmStatic fun clearLogs() = safely { engine.clear() }

    /**
     * 在之前已提交的写入完成后导出保留日志到 cacheDir 下的唯一 ZIP。
     * 回调在文件线程执行；不分享、不上传。任意前序文件操作失败时返回 error，避免伪报完整成功。
     */
    @JvmStatic fun export(context: Context, callback: LogExportCallback) {
        engine.export({ (context.applicationContext ?: context).cacheDir }) { callback.onResult(it) }
    }

    private fun write(level: LogLevel, tag: String?, args: Array<out Any?>,
                      formatter: (Array<out Any?>) -> String = LogFormatter::message) = safely {
        if (!engine.isInitialized) return@safely
        engine.log(level, tag, args, Thread.currentThread().stackTrace, formatter)
    }

    private inline fun safely(action: () -> Unit) {
        try { action() } catch (error: Throwable) {
            // 错误诊断不递归经过 Logger；平台 Log 自身异常也不得影响宿主。
            runCatching { Log.w("YcLogger", "Logger operation failed", error) }
        }
    }
}
