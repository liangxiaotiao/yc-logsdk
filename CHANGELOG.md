# Changelog

## 1.0.3（未发布）
- Logcat 每条日志只打印一行 `[线程] (File.kt:行) 消息`，TAG 由 logcat 自带；去掉画框，仅超过 3000 UTF-8 字节时拆条。
- 文件每条一行 `MM-dd HH:mm:ss.SSS L/线程 TAG(File.kt:行) 消息`，不再输出完整类路径与 `$1`、`$lambda$` 合成名；时间去掉年份（文件名含日期）。
- 多参数以空格分列，不再输出 `args[n] = `；Throwable 堆栈跟在该条消息之后。
- 新增 `Logger.addCallerSkip(vararg classNames)`：跳过宿主封装类（如 MLog），位置与默认 TAG 指向业务调用处。
- 新增 `Logger.export(context, callback, plain)`：`plain = true` 时 ZIP 内日志去掉 `@record` 记录头；原两参数 `export` 行为不变。
- 文件容器格式（`# Yc-Log-Sdk V1` 头、`@record %08x` 记录）不变，1.0.1 生成的旧文件可继续追加、读取与导出。

## 1.0.1
- 首个 Maven Central 版本。
