package com.yc.yclogsdk

import com.yc.yclogsdk.internal.*
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class LogFormatterTest {
    @Test fun singleArgumentIsVerbatimAndMultipleAreSpaceSeparatedWithStackLast() {
        assertEquals("开屏关闭", LogFormatter.message(arrayOf("开屏关闭")))
        val error = IllegalStateException("outer", IllegalArgumentException("inner"))
        val text = LogFormatter.message(arrayOf("连接", error, null, intArrayOf(1, 2)))
        assertTrue(text.startsWith("连接 null [1, 2]\njava.lang.IllegalStateException: outer"))
        assertFalse(text.contains("args["))
        assertTrue(text.contains("IllegalStateException: outer")); assertTrue(text.contains("Caused by:"))
        assertTrue(text.contains("IllegalArgumentException: inner"))
    }
    @Test fun hostileToStringDoesNotEscape() {
        val value = object { override fun toString(): String = throw IllegalStateException("bad") }
        assertTrue(LogFormatter.message(arrayOf(value)).contains("toString failed"))
    }
    @Test fun jsonAndXmlPrettyPrintAndFallback() {
        assertTrue(LogFormatter.json("{\"value\":1,\"other\":2}").contains("\n"))
        assertTrue(LogFormatter.json("[1,2]").contains("\n"))
        assertEquals("invalid {", LogFormatter.json("invalid {"))
        assertTrue(LogFormatter.xml("<root><value>中文</value></root>").contains("\n"))
        assertEquals("<broken>", LogFormatter.xml("<broken>"))
        val unsafe = "<!DOCTYPE root [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><root>&x;</root>"
        assertEquals(unsafe, LogFormatter.xml(unsafe))
    }
    @Test fun fileFormatKeepsOriginalCallTimeThreadTagAndLocation() {
        val old = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val event = LogEvent(1_000L, LogLevel.DEBUG, "worker", "Bluetooth",
                listOf(Caller("HomeActivity.kt", 125, "com.test.HomeActivity", "connect")), "first\nsecond")
            assertEquals("01-01 00:00:01.000 D/worker Bluetooth(HomeActivity.kt:125) first\n    | second",
                LogFormatter.file(event))
        } finally { TimeZone.setDefault(old) }
    }
    @Test fun logcatIsOneLineWithoutBorderOrClassPath() {
        val event = LogEvent(0, LogLevel.INFO, "main", "BaseActivity",
            listOf(Caller("BaseActivity.kt", 69, "com.test.BaseActivity\$onDestroy\$1", "invoke\$lambda\$3")), "页面销毁:SplashActivity")
        assertEquals(listOf("[main] (BaseActivity.kt:69) 页面销毁:SplashActivity"), LogFormatter.logcat(event))
    }
    @Test fun extraStackFramesUseSimpleNamesWithoutSyntheticSuffixes() {
        val event = LogEvent(0, LogLevel.DEBUG, "main", "AdHolder", listOf(
            Caller("AdHolder.kt", 153, "com.yucheng.ad.AdHolder\$loadSplash\$1\$1", "onAdClose\$lambda\$3"),
            Caller("Splash.java", 9, "com.yucheng.ad.Splash", "lambda\$show\$0")), "开屏关闭")
        assertEquals("[main] (AdHolder.kt:153) 开屏关闭\nat Splash.show(Splash.java:9)", LogFormatter.logcat(event).single())
        assertFalse(LogFormatter.file(event).contains("com.yucheng")); assertFalse(LogFormatter.file(event).contains("\$"))
    }
    @Test fun logcatChunksPreserveSupplementaryUnicode() {
        val body = "🙂中文".repeat(5000)
        val event = LogEvent(0, LogLevel.INFO, "main", "test",
            listOf(Caller("Test.kt", 3, "com.test.Test", "run")), body)
        val lines = LogFormatter.logcat(event)
        assertTrue(lines.size > 1); assertTrue(lines.first().startsWith("[main] (Test.kt:3) "))
        assertEquals("[main] (Test.kt:3) $body", lines.joinToString(""))
        assertTrue(lines.all { it.toByteArray(Charsets.UTF_8).size < 4000 })
    }
    @Test fun callerResolverSkipsLoggerAndUsesStackDeepAsFrameCount() {
        val frames = arrayOf(
            StackTraceElement("java.lang.Thread", "getStackTrace", "Thread.java", 1),
            StackTraceElement("com.yc.yclogsdk.internal.CallerResolver", "resolve", "CallerResolver.kt", 1),
            StackTraceElement("com.yc.yclogsdk.Logger", "d", "Logger.kt", 1),
            StackTraceElement("com.example.Home\$Callback", "onClick", "Home.kt", 20),
            StackTraceElement("com.example.App", "start", "App.java", 30)
        )
        val callers = CallerResolver.resolve(frames, 2)
        assertEquals(2, callers.size); assertEquals("Home", callers[0].tag)
        assertEquals("onClick", callers[0].methodName); assertEquals(20, callers[0].lineNumber)
        assertEquals(1, CallerResolver.resolve(frames, 1).size)
    }
    @Test fun addCallerSkipSkipsWrapperAndItsCompanion() {
        val frames = arrayOf(
            StackTraceElement("com.yc.yclogsdk.Logger", "dTag", "Logger.kt", 1),
            StackTraceElement("com.app.MLog\$Companion", "d", "MLog.kt", 18),
            StackTraceElement("com.app.MLog", "d", "MLog.kt", 5),
            StackTraceElement("com.app.MLogger", "x", "MLogger.kt", 7),
            StackTraceElement("com.app.Home", "onClick", "Home.kt", 20))
        try {
            assertEquals("MLog", CallerResolver.resolve(frames, 1).single().tag)
            Logger.addCallerSkip("com.app.MLog", "com.app.MLog", " ")
            assertEquals(listOf("com.app.MLog"), CallerResolver.skipped)
            // 只跳过本类与内部类，同前缀的其他类不受影响。
            assertEquals("MLogger", CallerResolver.resolve(frames, 1).single().tag)
            Logger.addCallerSkip("com.app.MLogger")
            assertEquals("Home.kt:20", CallerResolver.resolve(frames, 1).single().location)
        } finally { CallerResolver.skipped = emptyList() }
    }
    @Test fun logDateUsesNaturalDaysAcrossDst() {
        val old = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals("2026-03-09", LogDate.of(2026, 3, 8).plusDays(1).toString())
            assertEquals("2026-10-07", LogDate.of(2026, 10, 13).minusDays(6).toString())
            assertThrows(Exception::class.java) { LogDate.parse("2026-02-30") }
        } finally { TimeZone.setDefault(old) }
    }
}
