package com.yc.yclogsdk.internal

import android.content.Context
import android.os.Build

internal object AndroidFileHeader {
    @Suppress("DEPRECATION")
    fun create(context: Context): Map<String, String> {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val code = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() }
        return linkedMapOf(
            "Device Manufacturer" to Build.MANUFACTURER,
            "Device Model" to Build.MODEL,
            "Android Version" to Build.VERSION.RELEASE,
            "Android API Level" to Build.VERSION.SDK_INT.toString(),
            "App Package" to context.packageName,
            "App VersionName" to (info?.versionName ?: "unknown"),
            "App VersionCode" to (code?.toString() ?: "unknown")
        )
    }
}
