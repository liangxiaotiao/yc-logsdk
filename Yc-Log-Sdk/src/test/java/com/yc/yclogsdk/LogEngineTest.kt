package com.yc.yclogsdk

import com.yc.yclogsdk.internal.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

class LogEngineTest {
    @get:Rule val temp = TemporaryFolder()
    private val frames = arrayOf(StackTraceElement("com.test.Test", "run", "Test.kt", 42))
    private fun export(engine: LogEngine, cache: File): LogExportResult {
        val done = CountDownLatch(1); var result: LogExportResult? = null
        engine.export({ cache }) { result = it; done.countDown() }
        assertTrue("export timeout", done.await(20, TimeUnit.SECONDS))
        return result!!
    }
    private fun contents(result: LogExportResult): String {
        assertTrue("${result.error}", result.isSuccess)
        return ZipFile(result.file!!).use { zip ->
            zip.entries().asSequence().joinToString("\n") { zip.getInputStream(it).reader().readText() }
        }
    }
    private fun factory(root: File): (LogConfig) -> LogFileManager = { config ->
        LogFileManager(root, config, mapOf("App Package" to "test"))
    }

    @Test fun beforeInitDoesNotEvaluateArgumentsAndExportFails() {
        val worker = Executors.newSingleThreadExecutor()
        try {
            val engine = LogEngine(worker) {}; var evaluated = false
            val value = object { override fun toString(): String { evaluated = true; return "bad" } }
            engine.log(LogLevel.DEBUG, null, arrayOf(value), frames)
            assertFalse(evaluated); assertFalse(export(engine, temp.newFolder()).isSuccess)
        } finally { worker.shutdownNow() }
    }

    @Test fun firstInitWinsAndAppAndSdkUseTheSameFile() {
        val worker = Executors.newSingleThreadExecutor()
        try {
            val root = temp.newFolder(); val engine = LogEngine(worker) {}; var attempts = 0
            engine.init(LogConfig(name = "HealthWear"), false) { attempts++; factory(root)(it) }
            engine.log(LogLevel.INFO, "App", arrayOf("app"), frames)
            engine.init(LogConfig(), true) { attempts++; factory(root)(it) }
            engine.log(LogLevel.INFO, "Bluetooth", arrayOf("sdk"), frames)
            val text = contents(export(engine, temp.newFolder()))
            assertEquals(1, attempts); assertTrue(text.contains(" App(Test.kt:42)")); assertTrue(text.contains(" Bluetooth(Test.kt:42)"))
            assertEquals(1, File(root, "logs").listFiles()!!.size)
            assertTrue(File(root, "logs").listFiles()!!.single().name.startsWith("HealthWear_"))
        } finally { worker.shutdownNow() }
    }

    @Test fun debugReleaseDefaultsAndLevelFilterAffectBothSinks() {
        for (debug in listOf(true, false)) {
            val worker = Executors.newSingleThreadExecutor()
            try {
                val printed = Collections.synchronizedList(mutableListOf<LogEvent>())
                val engine = LogEngine(worker) { printed.add(it) }
                engine.init(LogConfig(), debug, factory(temp.newFolder()))
                assertEquals(LogLevel.VERBOSE, engine.level); assertEquals(debug, engine.logcatEnabled)
                engine.log(LogLevel.VERBOSE, null, arrayOf("verbose-kept"), frames)
                engine.level = LogLevel.WARN
                engine.log(LogLevel.DEBUG, null, arrayOf("debug-filtered"), frames)
                engine.logcatEnabled = false
                engine.log(LogLevel.ERROR, null, arrayOf("error-file-only"), frames)
                val text = contents(export(engine, temp.newFolder()))
                assertTrue(text.contains("verbose-kept")); assertTrue(text.contains("error-file-only"))
                assertFalse(text.contains("debug-filtered"))
                assertEquals(if (debug) 1 else 0, printed.size)
            } finally { worker.shutdownNow() }
        }
    }

    @Test fun failedInitializationCanBeRetried() {
        val worker = Executors.newSingleThreadExecutor()
        try {
            val engine = LogEngine(worker) {}
            engine.init(LogConfig(name = "bad"), false) { throw java.io.IOException("disk failure") }
            assertFalse(export(engine, temp.newFolder()).isSuccess)
            engine.init(LogConfig(name = "good"), false, factory(temp.newFolder()))
            engine.log(LogLevel.INFO, null, arrayOf("recovered"), frames)
            assertTrue(contents(export(engine, temp.newFolder())).contains("recovered"))
        } finally { worker.shutdownNow() }
    }

    @Test fun concurrentLogsAndHeaderCommandsAreCompleteBeforeExport() {
        val worker = Executors.newSingleThreadExecutor()
        val clients = Executors.newFixedThreadPool(8)
        try {
            val engine = LogEngine(worker) {}; engine.init(LogConfig(), false, factory(temp.newFolder()))
            val futures = (0 until 8).map { client -> clients.submit {
                repeat(50) { i -> engine.log(LogLevel.INFO, "Client", arrayOf("item=$client:$i"), frames) }
            } }
            futures.forEach { it.get(20, TimeUnit.SECONDS) }
            engine.addHeaders(linkedMapOf("Bluetooth SDK" to "1.3.5"))
            val text = contents(export(engine, temp.newFolder()))
            assertEquals(400, Regex("item=\\d+:\\d+").findAll(text).count())
            assertTrue(text.contains("Bluetooth SDK: 1.3.5"))
        } finally { clients.shutdownNow(); worker.shutdownNow() }
    }

    @Test fun badCallbackDoesNotKillWriterAndBadHeaderDoesNotDestroyLogs() {
        val worker = Executors.newSingleThreadExecutor()
        try {
            val engine = LogEngine(worker) {}; val cache = temp.newFolder()
            engine.init(LogConfig(maxFileSize = LogFileSize.MB_5), false, factory(temp.newFolder()))
            engine.export({ cache }) { throw IllegalStateException("callback crash") }
            engine.log(LogLevel.INFO, null, arrayOf("after callback"), frames)
            assertTrue(contents(export(engine, cache)).contains("after callback"))
            engine.addHeaders(mapOf("huge" to "x".repeat(6 * 1024 * 1024)))
            assertFalse(export(engine, cache).isSuccess)
        } finally { worker.shutdownNow() }
    }
}
