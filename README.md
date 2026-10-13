# Yc-Log-Sdk V1.0

轻量 Android 日志库：Kotlin 实现、Java 静态调用、单进程全局 Logger，支持 Logcat、按天文件、完整记录裁剪、自定义文件头和 ZIP 导出。

## 集成

开发仓库内使用 `implementation project(':Yc-Log-Sdk')`。最低 Android API 24；当前工程保持 AGP 9.3.3、Gradle 9.5.0、Java 21、compileSdk 36.1。SDK 没有 AndroidX、Timber、UtilCode、Gson 或其他功能性运行时依赖，仅需 Kotlin 标准库。没有 Maven 发布配置。

App 在 Application 中先初始化，然后蓝牙 SDK 调用默认 init 即可复用同一配置和文件：

```kotlin
Logger.init(
    context = application,
    name = "HealthWear",
    dirName = "logs",
    saveDays = 7,
    maxFileSize = LogFileSize.MB_20,
    stackDeep = 1
)
// 蓝牙 SDK 内部：不覆盖 App 配置。
Logger.init(context)
Logger.addFileHeader("Bluetooth SDK" to "1.3.5", "Protocol Version" to "2.0")
```

默认：`sdk` / `logs` / 最近 7 个自然日（含今天）/ 20 MiB / 一个调用帧。大小枚举只有 MB_5、MB_10、MB_15、MB_20。`name` 和 `dirName` 为 1–80 个字母、数字、下划线或连字符，支持中文，不接受路径或 `.`/`..`。`saveDays` 和 `stackDeep` 必须为正整数。

配置只由第一次成功初始化决定。磁盘准备在文件线程执行，期间后续调用排队；磁盘准备失败可再次 init。初始化前日志/头更新/开关设置直接忽略，不缓存。文件操作不在调用线程执行，仅支持单进程。

## 打印 API

```kotlin
Logger.d("用户点击连接")
Logger.iTag("Bluetooth", "开始连接设备")
Logger.d("连接信息", deviceName, deviceAddress, status) // args[0]、args[1]…
Logger.e(IllegalStateException("连接失败", cause))        // 完整 cause/suppressed 堆栈
Logger.json("JSON", """{"device":"Band","status":1}""")
Logger.xml("XML", "<device><status>1</status></device>")
Logger.level = LogLevel.DEBUG                          // 同时过滤 Logcat 和文件
Logger.logcatEnabled = false                          // 不影响文件
Logger.addFileHeader(mapOf("Bluetooth SDK" to "1.3.6"))
Logger.clearLogs()                                    // 异步清理今天之前的托管文件
```

等级：`v/d/i/w/e/a/wtf`；对应手动 TAG 方法为 `vTag/dTag/iTag/wTag/eTag/aTag/wtfTag`。ASSERT/WTF 使用 ASSERT 优先级的 Logcat println，不触发可能终止宿主的系统 wtf handler。JSON/XML 默认 DEBUG，有单字符串、`(tag, value)` 和 `jsonTag/xmlTag` 三种调用形式；非法内容回退原文。普通对象只使用 `toString()`，不自动 JSON 序列化。

Logcat 显示边框、线程及可点击调用位置，超长内容按 UTF-8 字节分段。`stackDeep` 是展示的外部调用帧数量，第一帧用于自动 TAG 和文件定位。默认 VERBOSE；Debug 宿主默认开启 Logcat，Release 宿主默认关闭，均写文件。环境依据宿主 `ApplicationInfo.FLAG_DEBUGGABLE` 判断。

## 文件和导出

文件固定为 `context.filesDir/dirName/name_yyyy-MM-dd.log`。时间、线程、TAG、调用帧在 Logger 调用时捕获，异步写入不重新计时。文件行包含毫秒时间、等级、线程、TAG、文件行号、类/方法及完整正文；后续行带缩进。

文件为 UTF-8 文本。头包含日期、厂商、型号、Android 版本/API、包名、App 版本名/版本码和自定义信息。自定义头按添加顺序，重复键更新原位置；当前文件用同目录临时文件、fsync 和原子 rename 更新，跨天继承。自定义头值中的换行/冒号/反斜杠被转义。请使用非空头键。

记录以 `@record <8位十六进制正文UTF-8字节数>` 标记，随后为正文和换行；读取/裁剪不依赖正文的时间戳或标记外观。大小上限包含头及标记。超限时移除最旧至少约 25% 的完整记录字节，必要时继续移除；超大单条记录保留字符完整的前缀并添加 `[TRUNCATED]`。头始终保留，过大的新头会安全拒绝而保留旧文件。

保留天数在初始化及跨天创建文件时自动清理。仅处理名称/日期/文件签名匹配的本 Logger 文件，不触碰其他文件，不跟随符号链接。重启继续追加当天文件，不重复写头；中断写入造成的不完整尾记录会在重新打开时移除，保留之前完整记录。

```kotlin
Logger.export(context) { result ->
    if (result.isSuccess) {
        val zip = result.file!!
        // 回调在文件线程；需要更新界面时自行 runOnUiThread。
    } else {
        val error = result.error
    }
}
```

ZIP 保存到 cacheDir，名称如 `HealthWear_logs_20261013_<唯一后缀>.zip`。单线程队列保证导出前已提交的日志完成写入；导出中不会插入头更新、裁剪或清理。不会上传、分享或按日期筛选。前序文件命令失败会返回失败，避免将缺失日志宣称为完整成功；之后的写入仍继续尝试。导出前未初始化也返回失败，回调抛异常不会终止日志线程。

## Java

```java
Logger.init(context);
Logger.dTag("Bluetooth", "连接成功", 42);
Logger.setLevel(LogLevel.DEBUG);
Logger.setLogcatEnabled(false);
Map<String, String> headers = new LinkedHashMap<>();
headers.put("Bluetooth SDK", "1.3.5");
Logger.addFileHeader(headers);
Logger.export(context, result -> {
    if (result.isSuccess()) {
        File zip = result.getFile();
    } else {
        Throwable error = result.getError();
    }
});
```

完整可编译示例见 `app/src/main/java/com/yc/logcatdemo/JavaUsage.java`。Sample 在 DemoApplication 中先初始化 HealthWear，再模拟 Bluetooth SDK 默认初始化和版本注册。MainActivity 的按钮覆盖所有主要调用、等级切换、头更新、清理、导出、长文本和多线程打印。

## 构建与验证

当前云环境每个新 shell 先执行 `source /workspace/cloud-setup/activate.sh`，再进入 `/workspace/yc-logsdk`；本地需 JDK 21 和 Android SDK 36.1/build-tools 36.0.0。

```bash
./gradlew :app:assembleDebug :app:assembleRelease :Yc-Log-Sdk:assembleDebug :Yc-Log-Sdk:assembleRelease test :app:assembleDebugAndroidTest :Yc-Log-Sdk:assembleDebugAndroidTest lint --max-workers=4 --console=plain
```

JVM 测试验证文件边界、四档真实容量、自然日/DST、清理、重启、头更新、JSON/XML、异常、多线程完整性、首次初始化、开关/等级和 Java API。AGP 9.3.3 默认仅创建 Debug unit test 任务，Release APK/AAR 单独编译；Debug/Release 输出策略在 JVM 层分别验证宿主标志。

连接设备后运行 `./gradlew :app:connectedDebugAndroidTest :Yc-Log-Sdk:connectedDebugAndroidTest`。当前云端设备运行情况和具体限制见 [KNOWN_ISSUES](docs/KNOWN_ISSUES.md)，自主决策见 [DECISIONS](docs/DECISIONS.md)。
