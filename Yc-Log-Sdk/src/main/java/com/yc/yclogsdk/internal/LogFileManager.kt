package com.yc.yclogsdk.internal

import com.yc.yclogsdk.LogConfig
import com.yc.yclogsdk.LogFileSize
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.FilterOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 只在 Logger 的单线程文件队列使用。所有字节长度包含头、记录标记和换行。 */
internal class LogFileManager(
    root: File, private val config: LogConfig, private val baseHeader: Map<String, String>,
    private val today: () -> LogDate = { LogDate.now() },
    private val isSymbolicLink: (File) -> Boolean = { it.canonicalFile != it.absoluteFile }
) {
    private val root = root.canonicalFile
    private val directory = File(this.root, config.dirName)
    private val pattern = Regex("${Regex.escape(config.name)}_(\\d{4}-\\d{2}-\\d{2})\\.log")
    private var currentDay: LogDate? = null
    private var custom = linkedMapOf<String, String>()

    init {
        checkDirectory()
        cleanup(today())
        ensureDay(today())
    }

    fun append(date: LogDate, text: String) {
        // 极端队列延迟的过期事件不会重新创建已清理的历史文件。
        if (date < today().minusDays(config.saveDays.toLong() - 1)) return
        val file = ensureDay(date)
        val header = readHeader(file)
        val record = makeRecord(text, config.maxFileSize.bytes - header.size)
        if (file.length() + record.size > config.maxFileSize.bytes) {
            val snapshot = readSnapshot(file)
            val kept = trim(snapshot.records, header.size, record.size)
            replace(file) { out ->
                out.write(header)
                kept.forEach(out::write)
                out.write(record)
            }
        } else {
            FileOutputStream(file, true).use { it.write(record) }
        }
    }

    fun cleanup(date: LogDate) {
        val cutoff = date.minusDays(config.saveDays.toLong() - 1)
        managedFiles().filter { it.second < cutoff }.forEach { (file, _) ->
            if (!file.delete()) throw IOException("Cannot delete expired log")
        }
    }

    fun clearHistory() {
        managedFiles().filter { it.second < today() }.forEach { (file, _) ->
            if (!file.delete()) throw IOException("Cannot clear historical log")
        }
    }

    fun updateHeaders(values: Map<String, String>) {
        val merged = LinkedHashMap(custom).apply { putAll(values) }
        val date = today()
        val newHeader = header(date, merged) // 先验证大小；失败不能更改配置或文件。
        val file = ensureDay(date)
        val snapshot = readSnapshot(file)
        val kept = if (newHeader.size + snapshot.records.sumOf { it.size } > config.maxFileSize.bytes)
            trim(snapshot.records, newHeader.size, 0) else snapshot.records
        replace(file) { out -> out.write(newHeader); kept.forEach(out::write) }
        custom = merged
    }

    fun export(cache: File): File {
        cleanup(today())
        ensureDay(today())
        val cacheRoot = cache.canonicalFile
        if (!cacheRoot.isDirectory && !cacheRoot.mkdirs()) throw IOException("Cannot create export directory")
        val destination = File(cacheRoot,
            "${config.name}_logs_${today().toString().replace("-", "")}_${UUID.randomUUID()}.zip")
        val files = managedFiles().map { it.first }.sortedBy { it.name }
        replace(destination) { out ->
            // 文件队列屏障已完成前序 append；整个快照过程中不允许任何文件命令插入。
            ZipOutputStream(object : FilterOutputStream(out) {
                override fun close() { flush() }
            }).use { zip ->
                for (file in files) {
                    checkFile(file)
                    val snapshot = readSnapshot(file)
                    zip.putNextEntry(ZipEntry(file.name))
                    zip.write(snapshot.header)
                    snapshot.records.forEach(zip::write)
                    zip.closeEntry()
                }
                zip.finish()
            }
        }
        return destination
    }

    private fun ensureDay(date: LogDate): File {
        checkDirectory()
        val file = File(directory, "${config.name}_$date.log")
        checkFile(file)
        if (currentDay != date || !file.exists()) {
            cleanup(today())
            if (!file.exists()) {
                val newHeader = header(date, custom)
                replace(file) { it.write(newHeader) }
            } else {
                val snapshot = readSnapshot(file)
                if (currentDay == null) {
                    // 重启不重复写头；保留之前注册的自定义头信息。
                    parseHeader(snapshot.header).filterKeys { it != "Log Date" && it !in baseHeader }
                        .forEach { (key, value) -> custom.putIfAbsent(key, value) }
                }
                if (snapshot.header.size >= config.maxFileSize.bytes - 128)
                    throw IOException("Existing header exceeds configured limit")
                val kept = if (file.length() > config.maxFileSize.bytes)
                    trim(snapshot.records, snapshot.header.size, 0) else snapshot.records
                val validSize = snapshot.header.size + snapshot.records.sumOf { it.size }
                if (validSize != file.length().toInt() || kept !== snapshot.records) {
                    replace(file) { out -> out.write(snapshot.header); kept.forEach(out::write) }
                }
            }
            currentDay = date
        }
        return file
    }

    private fun checkDirectory() {
        if (directory.canonicalFile != directory.absoluteFile || isSymbolicLink(directory))
            throw IOException("Log directory must remain inside filesDir")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create log directory")
    }

    private fun checkFile(file: File) {
        if (isSymbolicLink(file) || file.canonicalFile != file.absoluteFile ||
            (file.exists() && !file.isFile)) throw IOException("Unsafe log file")
    }

    private fun managedFiles(): List<Pair<File, LogDate>> {
        checkDirectory()
        return (directory.listFiles() ?: throw IOException("Cannot list logs")).mapNotNull { file ->
            val date = pattern.matchEntire(file.name)?.groupValues?.get(1)?.let {
                try { LogDate.parse(it) } catch (_: Exception) { null }
            } ?: return@mapNotNull null
            if (!file.isFile || isSymbolicLink(file)) return@mapNotNull null
            checkFile(file)
            val ours = file.inputStream().use { it.readNBytesCompat(MAGIC.size).contentEquals(MAGIC) }
            if (ours) file to date else null
        }
    }

    private fun header(date: LogDate, extras: Map<String, String>): ByteArray {
        val values = linkedMapOf("Log Date" to date.toString()).apply { putAll(baseHeader); putAll(extras) }
        val bytes = buildString {
            append(MAGIC.toString(Charsets.UTF_8))
            values.forEach { (key, value) -> append(escape(key)).append(": ").append(escape(value)).append('\n') }
            append(END_HEADER.toString(Charsets.UTF_8))
        }.toByteArray(Charsets.UTF_8)
        if (bytes.size >= config.maxFileSize.bytes - 128) throw IOException("Log header is too large")
        return bytes
    }

    private fun parseHeader(bytes: ByteArray): Map<String, String> = linkedMapOf<String, String>().apply {
        bytes.toString(Charsets.UTF_8).lineSequence().drop(1).forEach { line ->
            val split = line.indexOf(": ")
            if (split > 0) put(unescape(line.substring(0, split)), unescape(line.substring(split + 2)))
        }
    }

    private fun readHeader(file: File): ByteArray {
        checkFile(file)
        file.inputStream().buffered().use { input ->
            val bytes = java.io.ByteArrayOutputStream()
            var matched = 0
            var lineStart = true
            while (bytes.size() < config.maxFileSize.bytes) {
                val b = input.read()
                if (b < 0) break
                bytes.write(b)
                matched = if (matched > 0 && b == END_HEADER[matched].toInt()) matched + 1
                    else if (lineStart && b == END_HEADER[0].toInt()) 1 else 0
                lineStart = b == '\n'.code
                if (matched == END_HEADER.size) {
                    val result = bytes.toByteArray()
                    if (!result.take(MAGIC.size).toByteArray().contentEquals(MAGIC)) break
                    return result
                }
            }
        }
        throw IOException("Invalid log header")
    }

    private data class Snapshot(val header: ByteArray, val records: List<ByteArray>)

    private fun readSnapshot(file: File): Snapshot {
        val head = readHeader(file)
        // 有效日志最多 20 MiB；不加载无限大的外部修改文件。
        if (file.length() > LogFileSize.MB_20.bytes.toLong() + 1024) throw IOException("Unexpected log size")
        val data = file.readBytes()
        val records = ArrayList<ByteArray>()
        var offset = head.size
        while (offset + RECORD_OVERHEAD <= data.size) {
            val marker = data.copyOfRange(offset, offset + 17).toString(Charsets.US_ASCII)
            if (!marker.matches(Regex("@record [0-9a-f]{8}\\n"))) break
            val length = marker.substring(8, 16).toLong(16)
            val end = offset.toLong() + RECORD_OVERHEAD + length
            if (length > LogFileSize.MB_20.bytes || end > data.size || data[end.toInt() - 1] != '\n'.code.toByte()) break
            records.add(data.copyOfRange(offset, end.toInt()))
            offset = end.toInt()
        }
        return Snapshot(head, records)
    }

    /** 至少删去最旧约 25% 正文字节，随后继续删除直到满足硬上限。 */
    private fun trim(records: List<ByteArray>, headerSize: Int, incomingSize: Int): List<ByteArray> {
        val total = records.sumOf { it.size }
        val minimum = (total + 3) / 4
        var removed = 0; var index = 0
        while (index < records.size && (removed < minimum || headerSize + total - removed + incomingSize > config.maxFileSize.bytes)) {
            removed += records[index++].size
        }
        return records.drop(index)
    }

    private fun makeRecord(text: String, available: Int): ByteArray {
        val full = text.toByteArray(Charsets.UTF_8)
        val maxBody = available - RECORD_OVERHEAD
        if (maxBody < TRUNCATED.size) throw IOException("No space for record")
        val body = if (full.size <= maxBody) full else {
            var end = maxBody - TRUNCATED.size
            // UTF-8 continuation bytes cannot start the excluded suffix.
            while (end > 0 && full[end].toInt() and 0xc0 == 0x80) end--
            full.copyOf(end) + TRUNCATED
        }
        return ("@record %08x\n".format(java.util.Locale.ROOT, body.size)).toByteArray(Charsets.US_ASCII) + body + byteArrayOf(10)
    }

    /** 同目录临时文件 + fsync + 原子 rename；失败保留原文件并删除临时产物。 */
    private fun replace(file: File, write: (FileOutputStream) -> Unit) {
        checkFile(file)
        val temporary = File.createTempFile(".${config.name}-", ".tmp", file.parentFile)
        try {
            FileOutputStream(temporary).use { out ->
                write(out)
                out.fd.sync()
            }
            checkFile(file)
            // Android/Linux 的同目录 rename 原子替换；不先删除旧文件，失败保留旧内容。
            if (!temporary.renameTo(file)) throw IOException("Cannot atomically replace log file")
        } finally { temporary.delete() }
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n").replace(":", "\\c")
    private fun unescape(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            if (value[i] == '\\' && i + 1 < value.length) {
                append(when (val c = value[++i]) { 'r' -> '\r'; 'n' -> '\n'; 'c' -> ':'; else -> c })
            } else append(value[i])
            i++
        }
    }

    private fun java.io.InputStream.readNBytesCompat(count: Int): ByteArray {
        val result = ByteArray(count); var offset = 0
        while (offset < count) { val read = read(result, offset, count - offset); if (read < 0) break; offset += read }
        return result.copyOf(offset)
    }

    private companion object {
        val MAGIC = "# Yc-Log-Sdk V1\n".toByteArray(Charsets.UTF_8)
        val END_HEADER = "# End header\n".toByteArray(Charsets.UTF_8)
        val TRUNCATED = "\n[TRUNCATED]".toByteArray(Charsets.UTF_8)
        const val RECORD_OVERHEAD = 18
    }
}
