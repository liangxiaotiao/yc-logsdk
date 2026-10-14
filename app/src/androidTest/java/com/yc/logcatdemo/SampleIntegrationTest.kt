package com.yc.logcatdemo

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yc.yclogsdk.Logger
import com.yc.yclogsdk.LogExportResult
import com.yc.yclogsdk.LogLevel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class SampleIntegrationTest {
    @Test fun sampleButtonsExerciseKotlinAndJavaAndExport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            Logger.level = LogLevel.VERBOSE
            instrumentation.runOnMainSync {
                val root = activity.window.decorView
                for (label in listOf("普通日志", "TAG 日志", "多参数", "JSON / XML", "Throwable", "Java 调用", "多线程并发")) {
                    val button = findButton(root, label)
                    assertNotNull(label, button); button!!.performClick()
                }
            }
            instrumentation.waitForIdleSync()
            val done = CountDownLatch(1); var result: LogExportResult? = null
            Logger.export(context) { result = it; done.countDown() }
            assertTrue(done.await(30, TimeUnit.SECONDS)); assertTrue(result?.error.toString(), result!!.isSuccess)
            ZipFile(result!!.file!!).use { zip ->
                val text = zip.entries().asSequence().joinToString("\n") { zip.getInputStream(it).reader().readText() }
                assertTrue(text.contains("Java 调用示例")); assertTrue(text.contains("Bluetooth SDK:"))
                assertTrue(text.contains("用户点击连接")); assertTrue(text.contains("args[1] = DemoDevice"))
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    private fun findButton(view: View, label: String): Button? {
        if (view is Button && view.text.toString() == label) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findButton(view.getChildAt(i), label)?.let { return it }
        return null
    }
}
