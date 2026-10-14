package com.yc.logcatdemo

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.yc.yclogsdk.LogLevel
import com.yc.yclogsdk.Logger

/** 原生控件示例页；导出仅返回缓存 ZIP，不分享、不上传。 */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var result: TextView
    private var headerRevision = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Yc-Log-Sdk V1.0"
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        status = TextView(this).apply { textSize = 16f }
        result = TextView(this).apply { textSize = 14f; setTextIsSelectable(true) }
        content.addView(status)
        content.addView(TextView(this).apply {
            text = "文件: App filesDir/logs/HealthWear_日期.log\n文件始终开启；ZIP 在 cacheDir。"
        })
        fun button(label: String, action: () -> Unit) {
            content.addView(
                Button(this).apply {
                    text = label; setOnClickListener { action(); refreshStatus() }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        button("初始化 / 重复初始化") {
            Logger.init(application, name = "ThisMustNotOverride")
            JavaUsage.registerBluetoothSdk(application)
            note("重复初始化复用 Application 的 HealthWear 配置")
        }
        button("普通日志") {
            Logger.v("VERBOSE 示例"); Logger.d("用户点击连接"); Logger.i("开始连接设备")
            Logger.w("连接重试"); Logger.e("连接失败"); Logger.a("ASSERT 示例")
            note("已提交全部等级日志")
        }
        button("TAG 日志") {
            Logger.dTag("Bluetooth", "连接成功"); Logger.iTag("Bluetooth", "设备认证完成")
            Logger.eTag("Bluetooth", "连接失败"); note("已提交 Bluetooth TAG 日志")
        }
        button("多参数") {
            Logger.d(
                "连接信息",
                "DemoDevice",
                "AA:BB:CC:DD:EE:FF",
                true
            ); note("已提交 args[0..3]")
        }
        button("JSON / XML") {
            Logger.json("JSON", "{\"device\":\"DemoDevice\",\"status\":1}")
            Logger.xml("XML", "<device><name>DemoDevice</name><status>1</status></device>")
            Logger.json("非法 JSON 示例 {"); Logger.xml("<broken>")
            note("已提交格式化内容及非法输入回退")
        }
        button("Throwable") {
            Logger.e(IllegalStateException("连接失败", IllegalArgumentException("设备参数错误")))
            note("已提交完整 cause 堆栈")
        }
        button("切换等级 VERBOSE / WARN") {
            Logger.level = if (Logger.level == LogLevel.VERBOSE) LogLevel.WARN else LogLevel.VERBOSE
            Logger.d("DEBUG：在 WARN 级别被过滤"); Logger.w("WARN：保留")
            note("等级同时过滤文件和 Logcat")
        }
        button("切换 Logcat") {
            Logger.logcatEnabled =
                !Logger.logcatEnabled; Logger.i("Logcat 切换，文件继续记录"); note("已切换 Logcat")
        }
        button("自定义文件头") {
            headerRevision++
            Logger.addFileHeader(
                "Demo Revision" to headerRevision,
                "User Note" to "中文 / 多行\n安全转义"
            )
            Logger.addFileHeader(mapOf("Demo Revision" to headerRevision))
            note("已排队更新当天头，Revision=$headerRevision")
        }
        button("SDK 版本注册") { JavaUsage.registerBluetoothSdk(application); note("已注册 Bluetooth SDK / Protocol Version") }
        button("Java 调用") { JavaUsage.demonstrate(); note("Java 静态 API 已调用") }
        button("清理历史日志") { Logger.clearLogs(); note("异步清理已提交；保留当天文件") }
        button("导出 ZIP") {
            note("正在等待前序日志并导出…")
            Logger.export(application) { exported ->
                runOnUiThread { note(if (exported.isSuccess) "导出成功:\n${exported.file!!.absolutePath}" else "导出失败: ${exported.error}") }
            }
        }
        button("多线程并发") {
            Thread({
                val threads = (1..4).map { id ->
                    Thread({
                        repeat(50) { index ->
                            Logger.iTag(
                                "Concurrent",
                                "线程$id",
                                "record=$index"
                            )
                        }
                    }, "Demo-$id").apply { start() }
                }
                threads.forEach { it.join() }
                runOnUiThread { note("4 个线程已提交 200 条日志；点击导出等待写入完成") }
            }, "Demo-Coordinator").start()
            note("正在并发提交日志…")
        }
        button("超长日志 / 分段") {
            Thread({
                Logger.dTag("LongMessage", "🙂中文多行\n".repeat(2000))
                runOnUiThread { note("超长日志已提交；硬上限与裁剪由自动化测试验证") }
            }, "Demo-Long").start()
        }
        content.addView(result)
        setContentView(ScrollView(this).apply { addView(content) })
        refreshStatus(); note("Application 已初始化，蓝牙 SDK 复用同一 Logger")
    }

    private fun refreshStatus() {
        status.text =
            "级别: ${Logger.level}    Logcat: ${if (Logger.logcatEnabled) "开启" else "关闭"}"
    }

    private fun note(message: String) {
        result.text = message
    }
}
