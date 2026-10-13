package com.yc.yclogsdk.internal

import org.json.JSONArray
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

internal object LogFormatter {
    fun message(args: Array<out Any?>): String = when (args.size) {
        0 -> "(empty)"
        1 -> render(args[0])
        else -> args.mapIndexed { i, value -> "args[$i] = ${render(value)}" }.joinToString("\n")
    }

    private fun render(value: Any?): String = try {
        when (value) {
            null -> "null"
            is Throwable -> StringWriter().also { value.printStackTrace(PrintWriter(it)) }.toString()
            is Array<*> -> value.contentDeepToString()
            is IntArray -> value.contentToString()
            is LongArray -> value.contentToString()
            is ByteArray -> value.contentToString()
            is ShortArray -> value.contentToString()
            is CharArray -> value.contentToString()
            is FloatArray -> value.contentToString()
            is DoubleArray -> value.contentToString()
            is BooleanArray -> value.contentToString()
            else -> value.toString()
        }
    } catch (error: Throwable) { "[toString failed: ${error.javaClass.simpleName}]" }

    fun json(value: String): String = try {
        when (value.trimStart().firstOrNull()) {
            '{' -> JSONObject(value).toString(2)
            '[' -> JSONArray(value).toString(2)
            else -> value
        }
    } catch (_: Exception) { value }

    fun xml(value: String): String {
        // Android 的解析器不统一支持所有 JAXP 安全属性；先拒绝 DTD 和实体定义，绝不解析外部资源。
        if (Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(value)) return value
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            val builder = factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> InputSource(StringReader("")) }
                setErrorHandler(DefaultHandler())
            }
            val document = builder.parse(InputSource(StringReader(value)))
            val output = StringWriter()
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
                runCatching { setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2") }
            }.transform(DOMSource(document), StreamResult(output))
            output.toString().trimEnd()
        } catch (_: Exception) { value }
    }

    fun file(event: LogEvent): String {
        val caller = event.callers.first()
        return "${event.timestamp} [${event.level.letter}] [${singleLine(event.thread)}] " +
            "[${singleLine(event.tag)}] [${singleLine(caller.location)}] " +
            "${singleLine(caller.className)}.${singleLine(caller.methodName)}: " +
            event.message.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\n    | ")
    }

    fun logcat(event: LogEvent): List<String> = buildList {
        add("┌────────────────────────────────────────────────")
        event.callers.forEach { caller ->
            chunks("[${singleLine(event.thread)}] ${singleLine(caller.className)}.${singleLine(caller.methodName)} (${singleLine(caller.location)})")
                .forEach { add("│ $it") }
        }
        event.message.replace("\r\n", "\n").replace("\r", "\n").split('\n').forEach { line ->
            chunks(line).forEach { add("│ $it") }
        }
        add("└────────────────────────────────────────────────")
    }

    private fun singleLine(value: String) = value.replace("\n", "\\n").replace("\r", "\\r")

    /** 每段最多 3000 UTF-8 字节，不拆 Unicode code point，给边框和 Android 元数据留余量。 */
    private fun chunks(value: String): List<String> {
        if (value.isEmpty()) return listOf("")
        val result = ArrayList<String>()
        var start = 0; var offset = 0; var bytes = 0
        while (offset < value.length) {
            val cp = value.codePointAt(offset)
            val size = when { cp <= 0x7f -> 1; cp <= 0x7ff -> 2; cp <= 0xffff -> 3; else -> 4 }
            if (bytes + size > 3000) { result.add(value.substring(start, offset)); start = offset; bytes = 0 }
            bytes += size; offset += Character.charCount(cp)
        }
        result.add(value.substring(start))
        return result
    }
}
