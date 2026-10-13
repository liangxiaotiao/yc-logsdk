package com.yc.yclogsdk.internal

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 自然日的 UTC 编码仅用于日历运算，避免 API 26 java.time 和额外 desugaring 依赖。 */
@ConsistentCopyVisibility
internal data class LogDate private constructor(private val utcMillis: Long) : Comparable<LogDate> {
    override fun compareTo(other: LogDate) = utcMillis.compareTo(other.utcMillis)
    fun minusDays(days: Long) = LogDate(utcMillis - days * DAY_MILLIS)
    fun plusDays(days: Long) = minusDays(-days)
    override fun toString(): String = formatter(TimeZone.getTimeZone("UTC")).format(Date(utcMillis))

    companion object {
        private const val DAY_MILLIS = 86_400_000L
        private fun formatter(zone: TimeZone) = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = zone; isLenient = false
        }
        fun now() = fromMillis(System.currentTimeMillis())
        fun fromMillis(millis: Long) = parse(formatter(TimeZone.getDefault()).format(Date(millis)))
        fun of(year: Int, month: Int, day: Int) = parse(String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day))
        fun parse(value: String): LogDate {
            require(value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
            val format = formatter(TimeZone.getTimeZone("UTC"))
            val date = format.parse(value) ?: throw IllegalArgumentException("Invalid date")
            require(format.format(date) == value)
            return LogDate(date.time)
        }
    }
}
