package com.yc.yclogsdk.internal

import com.yc.yclogsdk.*
import java.io.File
import java.util.concurrent.ExecutorService

internal class LogEngine(private val worker: ExecutorService, private val printer: (LogEvent) -> Unit) {
    private class State(val config: LogConfig, debug: Boolean) {
        @Volatile var level = LogLevel.VERBOSE
        @Volatile var logcatEnabled = debug
        var manager: LogFileManager? = null // 仅在 worker 访问。
        var error: Throwable? = null
    }
    private val gate = Any()
    @Volatile private var state: State? = null
    val isInitialized: Boolean get() = state != null
    var level: LogLevel
        get() = state?.level ?: LogLevel.VERBOSE
        set(value) { state?.level = value }
    var logcatEnabled: Boolean
        get() = state?.logcatEnabled ?: false
        set(value) { state?.logcatEnabled = value }

    fun init(config: LogConfig, debug: Boolean, factory: (LogConfig) -> LogFileManager) = synchronized(gate) {
        if (state != null) return@synchronized
        val selected = State(config, debug)
        state = selected
        try {
            worker.execute {
                try { selected.manager = factory(config) }
                catch (error: Throwable) {
                    selected.error = error
                    synchronized(gate) { if (state === selected) state = null }
                }
            }
        } catch (_: Throwable) { state = null }
    }

    fun log(level: LogLevel, tag: String?, args: Array<out Any?>, frames: Array<StackTraceElement>,
            formatter: (Array<out Any?>) -> String = LogFormatter::message) {
        val millis = System.currentTimeMillis()
        val selected = state ?: return
        if (level.priority < selected.level.priority) return
        val print = selected.logcatEnabled
        val callers = CallerResolver.resolve(frames, selected.config.stackDeep)
        val event = LogEvent(millis, level, Thread.currentThread().name,
            tag?.takeIf { it.isNotEmpty() } ?: callers.first().tag, callers, formatter(args))
        synchronized(gate) {
            if (state !== selected) return
            submit(selected) { it.append(event.date, LogFormatter.file(event)) }
        }
        if (print) runCatching { printer(event) }
    }

    fun addHeaders(values: Map<String, String>) {
        val copy = LinkedHashMap(values)
        synchronized(gate) { state?.let { selected -> submit(selected) { it.updateHeaders(copy) } } }
    }

    fun clear() = synchronized(gate) { state?.let { selected -> submit(selected) { it.clearHistory() } }; Unit }

    fun export(cache: () -> File, callback: (LogExportResult) -> Unit) = synchronized(gate) {
        val selected = state
        val job = Runnable {
            val result = try {
                if (selected == null) throw IllegalStateException("Logger is not initialized")
                // 不把之前失败的已提交日志伪装成完整成功的导出。
                selected.error?.let { throw it }
                val manager = selected.manager ?: throw IllegalStateException("Logger initialization failed")
                LogExportResult(manager.export(cache()), null)
            } catch (error: Throwable) { LogExportResult(null, error) }
            runCatching { callback(result) }
        }
        try { worker.execute(job) }
        catch (error: Throwable) { runCatching { callback(LogExportResult(null, error)) } }
    }

    private fun submit(selected: State, action: (LogFileManager) -> Unit) {
        try {
            worker.execute {
                try {
                    val manager = selected.manager ?: return@execute
                    action(manager)
                } catch (error: Throwable) { selected.error = error }
            }
        } catch (error: Throwable) { selected.error = error }
    }
}
