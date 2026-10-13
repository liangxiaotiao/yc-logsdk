# V1.0 技术决策

- 按用户授权无人值守执行，使用当前隔离任务分支、模块、包名和工具链。
- SDK 仅使用 Kotlin 标准库及 Android/JDK API。移除原有 Timber、UtilCode、AndroidX、Material 运行时依赖；Sample 使用原生 Activity/控件/主题，避免依赖升级并解决原工程 AndroidX 要求 API 37 的冲突。
- `stackDeep` 表示展示的调用帧数量，参考 Blankj，默认 1；首个外部调用帧用于自动 TAG 和文件定位。
- 文件为 UTF-8 文本，使用显式头结束标记和带字节长度的记录标记；多行正文不会伪造记录边界。裁剪移除至少约 25% 的正文记录字节，必要时继续移除完整旧记录。
- 大小按 MiB（1024 × 1024）计算；超长记录在完整 UTF-8 字符边界截断并标记。超大头更新失败，保留原头和已有记录，不突破限制。
- 初始化磁盘操作在文件队列执行；待初始化期间后续调用同队列排队，初始化失败撤销配置允许重试；无效 init 安全忽略。
- `name`/`dirName` 只接受安全路径段，拒绝路径分隔符、控制字符、`.`/`..`；文件线程再次检查规范路径及符号链接，禁止逃出 App 私有目录。
- 导出使用队列屏障及唯一 ZIP 后缀避免覆盖。回调在文件线程执行，Sample 切到 UI 线程；提供 Java 回调接口和成功/失败结果类。
- 默认 VERBOSE，Logcat 依据宿主 debuggable 标志，文件始终开启；初始化前调用忽略。
- 普通对象使用安全 `toString()`，不做反射/JSON 序列化。JSON 使用 Android `org.json`；XML 禁用外部实体/DOCTYPE，格式异常回退原文。
- 代码原创，仅参考 Blankj 打印体验，没有复制开源实现。
- 删除原有无逻辑的 LogUtils/LogFileWriter/LogUploader 占位类，正式入口只有 Logger，不暗示上传能力。
- 日期采用 API 24 可用的 SimpleDateFormat/Date，UTC 自然日编码用于日历运算；Android lstat 检查符号链接，同目录 rename + fsync 安全替换，避免 API 26 java.time/Files 及 desugaring 依赖。
- 前序已提交文件操作失败时导出返回失败，后续写入继续尝试；不把可能缺失日志的 ZIP 宣称为完整成功。文件线程不被用户回调异常终止。

- SDK RuntimeClasspath 排除 Kotlin 标准库的传递 JetBrains annotations 元数据依赖，验证运行时图只有 Kotlin stdlib 2.2.10。org.json 仅用于 JVM 测试，Android 实际使用平台 org.json。
- 保持 AGP 9.3.3 默认 Debug 单元测试设置，不增加非必要测试变体配置；Release 产物编译和 debug/release 宿主标志策略分别验证。
