package com.yc.logcatdemo

import android.app.Application
import com.yc.yclogsdk.LogFileSize
import com.yc.yclogsdk.Logger

/** App 先初始化；蓝牙 SDK 的默认 init 随后复用配置及文件。 */
class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.init(this, name = "HealthWear", saveDays = 7, maxFileSize = LogFileSize.MB_20)
        JavaUsage.registerBluetoothSdk(this)
    }
}
