package com.yc.yclogsdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/** 设备测试：真实 Context/Android JSON/XML/文件头/单例/回调平台行为。 */
@RunWith(AndroidJUnit4::class)
class LoggerIntegrationTest {
    @Test fun exportsKotlinAndJavaCompatibleLogsWithDeviceAndCustomHeaders() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Logger.init(context, name = "../invalid") // 无效配置不能占用第一次成功初始化。
        Logger.init(context, name = "integration", maxFileSize = LogFileSize.MB_5)
        Logger.init(context, name = "ignored")
        Logger.logcatEnabled = false
        Logger.level = LogLevel.VERBOSE
        Logger.addFileHeader("Bluetooth SDK" to "1.3.5", "Protocol Version" to "2.0")
        Logger.addFileHeader(linkedMapOf("Bluetooth SDK" to "1.3.6"))
        val token = "integration-${System.nanoTime()}"
        Logger.dTag("Bluetooth", token, 42)
        Logger.json("JSON", "{\"first\":1,\"second\":2}")
        Logger.xml("XML", "<root><value>中文</value></root>")
        Logger.e(IllegalStateException("outer", IllegalArgumentException("cause")))
        val done = CountDownLatch(1); var result: LogExportResult? = null
        Logger.export(context) { result = it; done.countDown() }
        assertTrue(done.await(20, TimeUnit.SECONDS))
        assertTrue(result?.error.toString(), result!!.isSuccess)
        ZipFile(result!!.file!!).use { zip ->
            val text = zip.entries().asSequence().joinToString("\n") { zip.getInputStream(it).reader().readText() }
            assertTrue(text.contains(token)); assertTrue(text.contains("[Bluetooth]"))
            assertTrue(text.contains("Bluetooth SDK: 1.3.6")); assertTrue(text.contains("Android API Level:"))
            assertTrue(text.contains("App VersionCode:")); assertTrue(text.contains("App Package:"))
            assertTrue(text.contains("Caused by:")); assertTrue(text.contains("args[1] = 42"))
            assertFalse(context.filesDir.resolve("logs").resolve("ignored_${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())}.log").exists())
        }
    }
}
