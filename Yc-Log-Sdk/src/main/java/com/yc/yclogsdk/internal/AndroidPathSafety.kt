package com.yc.yclogsdk.internal

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File

internal object AndroidPathSafety {
    /** lstat 在 API 21 已提供，能识别指向不存在目标的符号链接，不依赖 API 26 Files。 */
    fun isSymbolicLink(file: File): Boolean = try {
        OsConstants.S_ISLNK(Os.lstat(file.absolutePath).st_mode)
    } catch (error: ErrnoException) {
        if (error.errno == OsConstants.ENOENT) false else throw error
    }
}
