# 任务：开发 Yc-Log-Sdk Android 日志库 V1.0

## 一、任务目标

请基于当前 GitHub 仓库，直接完成 Android 日志 SDK V1.0 的开发。

参考项目：
https://github.com/Blankj/AndroidUtilCode

重点参考：
com.blankj.utilcode.util.LogUtils

目标是开发一个轻量、稳定、零第三方运行时依赖的 Android 日志打印库。

要求：
- Kotlin 开发，兼容 Java。
- 保留 Blankj LogUtils 的主要打印体验。
- 支持日志文件存储、自动清理、超限裁剪、导出。
- 同时适用于公司 App 和提供给第三方客户的蓝牙 SDK。
- 对外 API 尽可能简单。
- 不要过度设计，不要引入复杂架构。

本次任务直接完成编码，无需等待我的方案确认。

---

## 二、现有项目结构

当前项目已经创建完成。

项目名称：LogcatDemo

现有模块：

1. app
   - 包名：com.yc.logcatdemo
   - 用于演示和测试日志 SDK。

2. Yc-Log-Sdk
   - 包名：com.yc.yclogsdk
   - 日志 SDK 正式开发模块。

要求：

- 直接使用现有项目。
- 不重新创建 Android 工程。
- 不修改现有模块名称。
- 不修改现有包名。
- SDK 所有核心代码放在 Yc-Log-Sdk 模块。
- app 模块负责功能演示和集成测试。
- 当前项目使用 Groovy build.gradle，保持现有构建脚本格式。
- 尽量保留已有 Gradle、AGP 和 SDK 配置，仅修改实现所需内容。
- 如果缺少 Kotlin 支持，可以补充必要插件。
- SDK 不允许依赖 app 模块。
- SDK 不允许依赖第三方运行时库。

先检查现有工程配置，再开始实现。

---

## 三、初始化设计

对外统一使用 Logger。

蓝牙 SDK 默认初始化：

Logger.init(context)

公司自己的 App 初始化：

Logger.init(
    context = application,
    name = "HealthWear",
    dirName = "logs",
    saveDays = 7,
    maxFileSize = LogFileSize.MB_20,
    stackDeep = 1
)

默认参数：

name = "sdk"
dirName = "logs"
saveDays = 7
maxFileSize = MB_20
stackDeep = 1

文件大小枚举只允许四档：

MB_5
MB_10
MB_15
MB_20

默认 MB_20。

初始化规则：

1. Logger 是进程内全局单例。
2. 只接受第一次成功初始化的配置。
3. 重复 init 不覆盖原有配置。
4. 公司 App 保证先初始化 Logger，再初始化蓝牙 SDK。
5. SDK 后续 init 直接复用已有 Logger。
6. App 与蓝牙 SDK 的日志写入同一份文件。
7. 不设计 SDK/APP 双通道。
8. 不需要单独的 LogSource。
9. 初始化前的日志直接忽略，不缓存。
10. 只支持单进程，不实现多进程日志管理。

---

## 四、日志打印 API

参考 Blankj LogUtils 的调用习惯。

### 1. 普通日志

Logger.v("message")
Logger.d("message")
Logger.i("message")
Logger.w("message")
Logger.e("message")

支持 ASSERT/WTF 等级。

### 2. 手动 TAG

Logger.dTag("Bluetooth", "连接成功")
Logger.iTag("Bluetooth", "设备认证完成")
Logger.eTag("Bluetooth", "连接失败")

其他等级提供对应 Tag 方法。

### 3. 多参数打印

Logger.d("连接信息", deviceName, deviceAddress, status)

Logger.dTag(
    "Bluetooth",
    "连接信息",
    deviceName,
    status
)

多个参数按照 args[0]、args[1] 等形式格式化输出。

### 4. JSON/XML

Logger.json(jsonString)
Logger.xml(xmlString)

支持指定 TAG。

JSON/XML 格式异常时，回退为原始字符串。

不实现普通对象自动 JSON 序列化。

使用者自行调用 Gson 等工具序列化对象。

### 5. Throwable

支持完整异常堆栈打印。

### 6. 调用位置

自动获取：

- TAG
- 类名
- 方法名
- 行号
- 线程名称

支持 stackDeep 配置，默认 1。

### 7. 日志等级

Logger.level = LogLevel.DEBUG

默认 VERBOSE，记录所有等级。

设置等级后，同时影响 Logcat 和文件输出。

### 8. Logcat 开关

Logger.logcatEnabled = true

Logger.logcatEnabled = false

默认规则：

Debug：Logcat 开启，文件开启。
Release：Logcat 关闭，文件开启。

根据宿主 App 的 debuggable 标志判断环境。

logcatEnabled 允许运行时修改，不影响文件输出。

---

## 五、日志输出格式

Logcat 和日志文件采用两种格式。

### Logcat

参考 Blankj LogUtils 的美化输出：

- 带边框。
- 显示调用位置。
- 显示线程。
- 支持超长日志分段。
- 方便 Android Studio 定位问题。

### 文件日志

采用简洁格式：

2026-10-13 10:00:01.123 [D] [main] [HomeActivity.kt:125] 用户点击连接

2026-10-13 10:00:01.256 [I] [main] [BluetoothManager.kt:86] 开始连接设备

必须保留：

- 日期时间，精确到毫秒。
- 日志等级。
- 线程。
- TAG。
- 调用位置。
- 完整日志内容。

日志时间在调用 Logger 时生成，而不是异步写入时生成。

多行日志必须保持可读，并且能够识别完整记录边界。

---

## 六、日志文件存储

固定存储在：

context.filesDir/dirName/

默认目录：

context.filesDir/logs/

支持自定义目录名称，但禁止路径穿越，不允许写入私有目录以外的位置。

### 按天存储

每天一个文件。

例如：

HealthWear_2026-10-11.log
HealthWear_2026-10-12.log
HealthWear_2026-10-13.log

蓝牙 SDK 默认：

sdk_2026-10-13.log

同一天追加写入。

跨天自动创建新文件。

App 重启后继续追加当天文件，不重复创建文件头。

### 自动清理

默认保存最近 7 个自然日，包括今天。

saveDays 支持自定义正整数。

清理时机：

1. Logger 初始化。
2. 跨天创建新文件。

只清理 Logger 管理的过期日志文件。

### 文件大小限制

单文件最大大小通过 LogFileSize 枚举设置。

默认 20MB。

支持：

5MB
10MB
15MB
20MB

当下一条日志写入会超过文件大小限制时：

1. 保留文件头。
2. 删除最早约 25% 的完整日志记录。
3. 保留较新的约 75% 日志记录。
4. 继续追加新日志。
5. 最终文件大小不能超过配置上限。

必须按完整日志记录裁剪，不能破坏多行日志、异常堆栈或 UTF-8 字符。

文件裁剪采用临时文件安全替换机制。

如果单条日志超过文件大小上限：

- 截断日志内容。
- 优先保留开头。
- 增加截断标记。
- 不允许突破大小上限。

文件头必须始终保留。

---

## 七、日志文件头

参考 Blankj LogUtils 的设备信息头功能。

每天创建日志文件时自动写入手机和应用信息。

至少包括：

- 日志日期。
- 手机厂商。
- 手机型号。
- Android 版本。
- Android API Level。
- App 包名。
- App VersionName。
- App VersionCode。

### 自定义文件头

对外提供：

Logger.addFileHeader(
    "Bluetooth SDK" to "1.3.5",
    "Protocol Version" to "2.0"
)

支持：

1. Kotlin 可变参数 Pair。
2. Kotlin/Java Map。
3. 一次添加任意数量键值对。
4. 重复 Key 覆盖旧 Value。
5. 保持添加顺序。
6. 初始化后追加。
7. 当天文件已经存在时安全更新文件头。
8. 跨天自动继承自定义信息。
9. 文件裁剪时保留完整文件头。

蓝牙 SDK 可以在自身初始化时注册版本号。

Logger 不直接依赖蓝牙 SDK。

请在 app 示例中模拟 SDK 版本注册。

文件头处理与日志写入必须保证线程安全。

---

## 八、手动清理日志

提供：

Logger.clearLogs()

功能：

删除今天之前的全部日志文件。

当天日志文件必须保留。

清理过程异步执行，不阻塞主线程。

---

## 九、日志导出

提供简单的导出 API：

Logger.export(context) { result ->
    // 导出结果
}

要求：

1. 导出当前保留的全部日志文件。
2. 导出之前完成已提交日志的文件写入。
3. 自动压缩为 ZIP。
4. ZIP 保存到 App 缓存目录。
5. 异步执行。
6. 回调返回成功或失败结果。
7. 不需要日期筛选。
8. 不自动上传服务器。
9. 不自动调用系统分享。
10. 导出过程中保证文件数据完整。

示例：

HealthWear_logs_20261013.zip

---

## 十、实现原则

采用轻量架构。

建议包含：

Logger
LogConfig
LogLevel
LogFileSize

以及 internal：

LogEvent
LogFormatter
LogcatPrinter
LogFileWriter
LogFileManager
LogFileHeader
LogExporter
CallerResolver

可根据实际实现适当调整。

要求：

1. 核心代码使用 Kotlin。
2. 兼容 Java 静态调用。
3. 使用单线程 ExecutorService 异步写入文件。
4. 文件写入、裁剪、清理、导出串行协调。
5. 不在主线程进行磁盘操作。
6. 不实现复杂缓冲管理。
7. 不实现多进程支持。
8. 不实现日志上传。
9. 不实现日志脱敏。
10. 不实现对象反射 JSON 序列化。
11. 不引入第三方运行时依赖。
12. 运行时日志组件异常不得导致宿主 App 崩溃。
13. 避免无意义的类、接口和过度封装。
14. 公共 API 和复杂逻辑编写必要 KDoc/中文注释。

可以研究 Blankj LogUtils 的实现方式，但不要直接复制其依赖大量工具类的架构。

如果复用开源代码，必须遵守原项目的开源许可证要求。

---

## 十一、Sample App

直接使用现有 app 模块。

通过 MainActivity 演示和测试：

1. Logger 初始化。
2. 普通日志。
3. TAG 日志。
4. 多参数打印。
5. JSON/XML 打印。
6. Throwable。
7. 日志等级切换。
8. Logcat 开关。
9. 自定义文件头。
10. SDK 版本注册。
11. 手动清理历史日志。
12. 导出 ZIP。
13. 多线程并发打印。

建议创建简单的功能测试页面，使用按钮触发对应功能。

不需要复杂 UI。

同时提供 Java 调用示例，验证 SDK 兼容性。

---

## 十二、测试与验收

必须尽可能完成以下验证：

- Debug/Release 输出规则。
- Kotlin/Java API。
- 首次初始化和重复初始化。
- App 与 SDK 共用日志文件。
- 文件跨天存储。
- 7 天自动清理。
- 5/10/15/20MB 大小限制。
- 超限裁剪。
- 单条超长日志。
- 文件头保留。
- 自定义文件头。
- JSON/XML。
- Throwable。
- 多线程打印。
- 日志导出。
- App 重启后继续追加。

对于文件管理、裁剪、日期、大小限制等核心逻辑，编写自动化测试。

尽可能执行 Gradle 编译和测试。

如果云端环境缺少 Android SDK 或必要依赖：

1. 优先尝试合理修复环境。
2. 不要无限重复安装或下载。
3. 记录无法验证的项目。
4. 继续完成不依赖该环境的工作。
5. 禁止伪造编译或测试通过结果。

---

## 十三、重要：云端自主执行规则

这是一次无人值守 Coding 任务。

我暂时无法及时回复你的问题。

因此：

### 1. 不需要等待我确认

请直接：

阅读工程 → 确定实现 → 编写代码 → 编译测试 → 修复问题 → 提交结果。

不要在每个阶段等待我确认。

### 2. 遇到不明确的问题自行决策

如果需求存在轻微歧义：

- 优先遵守本 Prompt 已确定的要求。
- 选择最简单、稳定、易维护的实现。
- 不要自行扩大功能范围。
- 不要因为细节不明确而停止开发。

将自行决定的内容记录到：

docs/DECISIONS.md

方便我明天审核。

### 3. 遇到技术问题自行修复

例如：

- Gradle 配置异常。
- Kotlin 编译错误。
- Java 兼容问题。
- Android API 兼容问题。
- 单元测试失败。
- 文件管理实现问题。

请先自行分析并修复。

不要遇到错误就停止等待我回复。

如果确实无法解决：

记录到：

docs/KNOWN_ISSUES.md

并继续执行其他不受影响的任务。

### 4. 不要过度设计

本次核心目标是：

以最直接、稳定、可维护的方式完成一个可运行的 Logger SDK V1.0。

不要为未来需求提前设计复杂框架。

不要增加未经要求的功能。

### 5. 代码管理

- 保留现有工程结构。
- 不删除无关业务代码。
- 不修改无关文件。
- 不随意升级整个项目的依赖版本。
- 不执行破坏性的 Git 操作。
- 不修改或覆盖其他人的提交。
- 在当前任务分支完成开发。
- 如果环境支持，提交完成的代码并创建 PR。
- 不要实际发布 Maven。
- 不需要生成 Maven 发布配置，后续单独讨论。

### 6. 最终交付

完成后输出：

1. 已实现功能清单。
2. 主要新增和修改的文件。
3. Logger 对外 API 使用示例。
4. 编译及测试结果。
5. 当前未解决的问题。
6. 自行作出的技术决策。
7. 下一步建议。

如果无法完成某个功能，必须明确标注，不能假装已实现。

---

## 十四、执行要求

现在开始执行。

请先快速检查现有仓库和 Gradle 配置，然后直接开展编码。

不需要先输出长篇设计文档。

不需要反复向我确认。

优先保证：

1. Logger 基础打印正常。
2. 文件日志正常存储。
3. 设备信息与自定义文件头正常。
4. 文件自动清理与大小裁剪正常。
5. 日志导出正常。
6. Sample App 能验证主要功能。

先把 SDK V1.0 做出来。

不确定的问题记录下来，明天再由我审核调整。

不要为了追求完美而长时间停留在架构讨论阶段。