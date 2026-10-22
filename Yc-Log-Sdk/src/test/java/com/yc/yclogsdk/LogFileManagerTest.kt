package com.yc.yclogsdk

import com.yc.yclogsdk.internal.LogFileManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import com.yc.yclogsdk.internal.LogDate as LocalDate
import java.util.zip.ZipFile

class LogFileManagerTest {
    @get:Rule val temp = TemporaryFolder()
    private val day = LocalDate.of(2026, 10, 13)
    private fun manager(root: File, size: LogFileSize = LogFileSize.MB_5, days: Int = 7,
                        clock: () -> LocalDate = { day }) =
        LogFileManager(root, LogConfig("HealthWear", "logs", days, size, 1),
            linkedMapOf("App Package" to "com.test", "Device" to "中文手机"), clock)
    private fun today(root: File) = File(root, "logs/HealthWear_$day.log")

    @Test fun appendsAfterRestartWithoutDuplicatingHeader() {
        val root = temp.newFolder()
        manager(root).append(day, "first\n  stack frame\n中文")
        manager(root).append(day, "second")
        val text = today(root).readText()
        assertEquals(1, Regex("# Yc-Log-Sdk V1").findAll(text).count())
        assertTrue(text.contains("first\n  stack frame\n中文"))
        assertTrue(text.contains("second"))
    }

    @Test fun retainsSevenNaturalDaysAndLeavesForeignFilesAlone() {
        val root = temp.newFolder()
        var date = day.minusDays(8)
        val m = manager(root, clock = { date })
        repeat(9) { m.append(date, "day=$date"); date = date.plusDays(1) }
        val logs = File(root, "logs")
        val foreign = File(logs, "Other_${day.minusDays(100)}.log").apply { writeText("foreign") }
        val fake = File(logs, "HealthWear_${day.minusDays(100)}.log").apply { writeText("not ours") }
        m.cleanup(day)
        assertEquals(7, logs.listFiles()!!.count { it.readText().startsWith("# Yc-Log-Sdk V1") })
        assertTrue(foreign.exists()); assertTrue(fake.exists())
        assertFalse(File(logs, "HealthWear_${day.minusDays(7)}.log").exists())
        assertTrue(File(logs, "HealthWear_${day.minusDays(6)}.log").exists())
    }

    @Test fun clearPreservesTodayAndForeignFiles() {
        val root = temp.newFolder(); val m = manager(root)
        m.append(day.minusDays(1), "yesterday"); m.append(day, "today")
        val foreign = File(root, "logs/readme.txt").apply { writeText("keep") }
        m.clearHistory()
        assertTrue(today(root).readText().contains("today"))
        assertFalse(File(root, "logs/HealthWear_${day.minusDays(1)}.log").exists())
        assertTrue(foreign.exists())
    }

    @Test fun allFourCapsTruncateUtf8AndKeepHeader() {
        for (size in LogFileSize.entries) {
            val root = temp.newFolder(); val m = manager(root, size)
            m.append(day, "开头" + "中文🙂".repeat(size.bytes / 8))
            val file = today(root)
            assertTrue("$size ${file.length()}", file.length() <= size.bytes)
            val text = file.readText()
            assertTrue(text.contains("开头")); assertTrue(text.contains("[TRUNCATED]"))
            assertTrue(text.contains("中文手机")); assertFalse(text.contains('\uFFFD'))
        }
    }

    @Test fun trimRemovesOldCompleteRecordsAndKeepsNewMultilineRecords() {
        val root = temp.newFolder(); val m = manager(root)
        repeat(10) { m.append(day, "record-$it\n" + "字".repeat(180_000) + "\nend-$it") }
        val text = today(root).readText()
        assertTrue(today(root).length() <= LogFileSize.MB_5.bytes)
        assertFalse(text.contains("record-0\n"))
        assertTrue(text.contains("record-9\n")); assertTrue(text.contains("end-9"))
        for (i in 0..9) assertEquals(text.contains("record-$i\n"), text.contains("end-$i"))
        assertTrue(text.startsWith("# Yc-Log-Sdk V1")); assertFalse(text.contains('\uFFFD'))
    }

    @Test fun headerUpdatesKeepOrderOverwriteValuesAndSurviveRollover() {
        val root = temp.newFolder(); var date = day; val m = manager(root, clock = { date })
        m.append(date, "before")
        m.updateHeaders(linkedMapOf("Bluetooth SDK" to "1.0", "Protocol" to "2.0"))
        m.updateHeaders(mapOf("Bluetooth SDK" to "1.3.5"))
        m.append(date, "after")
        val text = today(root).readText()
        assertTrue(text.indexOf("Bluetooth SDK: 1.3.5") < text.indexOf("Protocol: 2.0"))
        assertTrue(text.contains("before")); assertTrue(text.contains("after"))
        date = day.plusDays(1); m.append(date, "next")
        assertTrue(File(root, "logs/HealthWear_$date.log").readText().contains("Bluetooth SDK: 1.3.5"))
    }

    @Test fun oversizedHeaderFailsWithoutChangingFile() {
        val root = temp.newFolder(); val m = manager(root); m.append(day, "keep")
        val old = today(root).readBytes()
        assertThrows(Exception::class.java) { m.updateHeaders(mapOf("Huge" to "x".repeat(6 * 1024 * 1024))) }
        assertArrayEquals(old, today(root).readBytes())
        m.append(day, "still works")
        assertTrue(today(root).readText().contains("still works"))
    }

    @Test fun maliciousBodyCannotForgeRecordBoundaries() {
        val root = temp.newFolder(); val m = manager(root)
        val body = "first\n@record 00000001\n# End header\n\u0000\n🙂"
        m.append(day, body); manager(root).append(day, "second")
        assertTrue(today(root).readText().contains(body))
        assertTrue(today(root).readText().contains("second"))
    }

    @Test fun headerMarkerInsideValueCannotHideFieldsOrRecords() {
        val root = temp.newFolder(); val cache = temp.newFolder(); val m = manager(root)
        m.append(day, "before header")
        m.updateHeaders(linkedMapOf("Marker" to "# End header", "Later" to "keep"))
        m.append(day, "after header")
        ZipFile(m.export(cache)).use { zip ->
            val text = zip.getInputStream(zip.getEntry(today(root).name)).reader().readText()
            assertTrue(text.contains("Later: keep")); assertTrue(text.contains("before header"))
            assertTrue(text.contains("after header"))
        }
        manager(root).append(day, "restart")
        val text = today(root).readText()
        assertTrue(text.contains("before header")); assertTrue(text.contains("after header")); assertTrue(text.contains("restart"))
        m.updateHeaders(mapOf("Another" to "new value"))
        repeat(10) { m.append(day, "trim-$it\n" + "字".repeat(180_000) + "\nend-$it") }
        val trimmed = today(root).readText()
        assertTrue(trimmed.contains("Later: keep")); assertTrue(trimmed.contains("Another: new value"))
        assertTrue(trimmed.contains("trim-9\n")); assertTrue(trimmed.contains("end-9"))
        assertTrue(today(root).length() <= LogFileSize.MB_5.bytes)
    }

    @Test fun truncatedTailIsRecoveredWithoutLosingCompleteRecords() {
        val root = temp.newFolder(); manager(root).append(day, "complete")
        today(root).appendText("@record 000000ff\ntorn")
        manager(root).append(day, "after recovery")
        val text = today(root).readText()
        assertTrue(text.contains("complete")); assertTrue(text.contains("after recovery"))
        assertFalse(text.contains("torn"))
    }

    @Test fun zipContainsOnlyManagedCompleteLogsAndUsesUniqueNames() {
        val root = temp.newFolder(); val cache = temp.newFolder(); val m = manager(root)
        m.append(day.minusDays(1), "yesterday"); m.append(day, "last committed")
        File(root, "logs/foreign.txt").writeText("not exported")
        val zip = m.export(cache); val zip2 = m.export(cache)
        assertNotEquals(zip.name, zip2.name)
        ZipFile(zip).use { z ->
            assertEquals(2, z.size())
            assertTrue(z.getInputStream(z.getEntry(today(root).name)).reader().readText().contains("last committed"))
        }
    }

    @Test fun legacyFormatFileStaysReadableAppendableAndExportable() {
        val root = temp.newFolder(); val cache = temp.newFolder()
        // 1.0.1 写出的原始字节：容器格式未变，只有正文样式不同。
        val legacyBody = "2026-10-13 15:36:22.736 [D] [main] [AdHolder] [AdHolder.kt:153] com.y.AdHolder\$a\$1.b: args[0] = x\n    | args[1] = 开屏关闭"
        val bytes = legacyBody.toByteArray()
        File(root, "logs").mkdirs()
        today(root).writeBytes("# Yc-Log-Sdk V1\nLog Date: $day\n# End header\n".toByteArray() +
            "@record %08x\n".format(bytes.size).toByteArray() + bytes + "\n".toByteArray())
        val m = manager(root)
        m.append(day, "10-13 15:37:00.000 D/main AdHolder(AdHolder.kt:160) new")
        val text = today(root).readText()
        assertTrue(text.contains(legacyBody)); assertTrue(text.endsWith("AdHolder(AdHolder.kt:160) new\n"))
        ZipFile(m.export(cache)).use { z ->
            val raw = z.getInputStream(z.getEntry(today(root).name)).reader().readText()
            assertEquals(2, Regex("@record [0-9a-f]{8}\n").findAll(raw).count())
        }
        ZipFile(m.export(cache, plain = true)).use { z ->
            val plain = z.getInputStream(z.getEntry(today(root).name)).reader().readText()
            assertFalse(plain.contains("@record"))
            assertTrue(plain.contains("# End header\n$legacyBody\n10-13 15:37:00.000 D/main AdHolder(AdHolder.kt:160) new\n"))
        }
    }

    @Test fun plainZipHasAllManagedFilesWithoutRecordMarkers() {
        val root = temp.newFolder(); val cache = temp.newFolder(); val m = manager(root)
        m.append(day.minusDays(1), "yesterday\n    | second line"); m.append(day, "@record 00000001 in body")
        File(root, "logs/foreign.txt").writeText("not exported")
        val zip = m.export(cache, plain = true)
        assertTrue(zip.name.endsWith(".zip"))
        ZipFile(zip).use { z ->
            assertEquals(listOf("HealthWear_${day.minusDays(1)}.log", today(root).name), z.entries().toList().map { it.name })
            val y = z.getInputStream(z.getEntry("HealthWear_${day.minusDays(1)}.log")).reader().readText()
            assertTrue(y.startsWith("# Yc-Log-Sdk V1\n")); assertTrue(y.endsWith("# End header\nyesterday\n    | second line\n"))
            val t = z.getInputStream(z.getEntry(today(root).name)).reader().readText()
            // 正文里出现的 @record 字样原样保留，只去掉真正的记录头。
            assertTrue(t.endsWith("# End header\n@record 00000001 in body\n"))
        }
        // 原始文件不受 plain 导出影响。
        assertTrue(today(root).readText().contains(Regex("@record [0-9a-f]{8}\n@record 00000001 in body")))
    }

    @Test fun rejectsTraversalAndInvalidConfiguration() {
        for (path in listOf("../logs", "/tmp/logs", ".", "..", "a/b", "a\\b", "\n")) {
            assertThrows(IllegalArgumentException::class.java) { LogConfig("sdk", path, 7, LogFileSize.MB_20, 1) }
        }
        assertThrows(IllegalArgumentException::class.java) { LogConfig("../sdk", "logs", 7, LogFileSize.MB_20, 1) }
        assertThrows(IllegalArgumentException::class.java) { LogConfig("sdk", "logs", 0, LogFileSize.MB_20, 1) }
        assertThrows(IllegalArgumentException::class.java) { LogConfig("sdk", "logs", 7, LogFileSize.MB_20, 0) }
    }

    @Test fun rejectsDirectoryAndFileSymlinks() {
        val root = temp.newFolder(); val outside = temp.newFolder()
        Files.createSymbolicLink(File(root, "logs").toPath(), outside.toPath())
        assertThrows(Exception::class.java) { manager(root).append(day, "escape") }
        assertEquals(0, outside.listFiles()!!.size)
        val root2 = temp.newFolder(); File(root2, "logs").mkdir()
        val target = File(outside, "keep").apply { writeText("original") }
        Files.createSymbolicLink(today(root2).toPath(), target.toPath())
        assertThrows(Exception::class.java) { manager(root2).append(day, "escape") }
        assertEquals("original", target.readText())
    }
}
