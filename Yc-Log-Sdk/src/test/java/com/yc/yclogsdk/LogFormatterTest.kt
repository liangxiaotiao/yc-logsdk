package com.yc.yclogsdk

import com.yc.yclogsdk.internal.*
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class LogFormatterTest {
    @Test fun multipleArgumentsAreIndexedAndThrowableIncludesCause() {
        val error = IllegalStateException("outer", IllegalArgumentException("inner"))
        val text = LogFormatter.message(arrayOf("连接", null, intArrayOf(1, 2), error))
        assertTrue(text.contains("args[0] = 连接")); assertTrue(text.contains("args[1] = null"))
        assertTrue(text.contains("args[2] = [1, 2]"))
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
            val text = LogFormatter.file(event)
            assertTrue(text.startsWith("1970-01-01 00:00:01.000 [D] [worker] [Bluetooth] [HomeActivity.kt:125]"))
            assertTrue(text.contains("connect")); assertTrue(text.contains("first\n    | second"))
        } finally { TimeZone.setDefault(old) }
    }
    @Test fun logcatChunksHaveBordersAndPreserveSupplementaryUnicode() {
        val body = "🙂中文".repeat(5000)
        val event = LogEvent(0, LogLevel.INFO, "main", "test",
            listOf(Caller("Test.kt", 3, "com.test.Test", "run")), body)
        val lines = LogFormatter.logcat(event)
        assertTrue(lines.first().startsWith("┌")); assertTrue(lines.last().startsWith("└"))
        assertTrue(lines.any { it.contains("Test.kt:3") && it.contains("main") })
        val content = lines.drop(2).dropLast(1).joinToString("") { it.removePrefix("│ ") }
        assertEquals(body, content)
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
