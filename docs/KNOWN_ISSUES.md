# 已知问题与验证边界

## 环境验证边界

- 云端 adb 已启动，但 `adb devices` 返回空列表，且没有 `/dev/kvm`。设备测试 APK 已构建，尚未在设备/模拟器运行。Android API 24/36 的实际 Logcat、JSON/XML 解析、系统文件行为和 Sample 操作需后续真机验证，不能将编译成功等同于设备测试通过。
- AGP 9.3.3 默认不创建 `testReleaseUnitTest`；显式请求该任务得到 task not found。Release APK/AAR 编译验证和 JVM Debug/Release 宿主标志策略测试已覆盖对应逻辑，未声称执行 Release unit test。
- Lint 无错误，仍有版本升级提示、Sample 字符串国际化、原有未使用颜色、测试依赖放在版本目录外等非阻塞警告。按要求不进行无关升级/重构。
- GitHub REST 请求在 HTTPS CONNECT 阶段被云端代理以 403 拒绝，尚不能确认自动 PR 创建能力；无需重新提供 GitHub token。Git 读写正常：代码已提交并推送到 origin/work；gh pr create 请求 https://api.github.com/graphql 返回 Forbidden，PR 未创建。已在云环境草稿加入 api.github.com，需用户审阅保存并发布/应用后再试。可直接访问 https://github.com/liangxiaotiao/yc-logsdk/pull/new/work 创建 PR；说明见 PR_DESCRIPTION.md。

## 独立复查

- 重要问题已确认并修复：自定义头的值恰好包含 `# End header` 时，旧读取器可能将值中的标记误认成头结束，导致导出/重启遗漏记录。现仅按完整行识别结束标记，回归覆盖导出、重启、后续头更新及容量裁剪。
- 延后的小项：空自定义头键可写入，但重启恢复时不继承。当前文档要求使用非空键；后续可统一拒绝或支持空键，不影响普通版本/设备头。
- 延后的小项：Sample instrumentation 的并发按钮目前仅执行点击冒烟，未等待所有演示 worker 或断言 200 条记录。JVM LogEngine 测试已等待客户端提交后导出并精确验证 8×50=400 条记录，Sample 设备测试仍待运行。

## V1.0 使用范围

- 单进程，队列不设置复杂缓冲/背压，应用应避免持续无限日志洪峰或巨量字符串；这是本次要求的轻量单线程 Executor 方案。
- 不包含多进程、上传、系统分享、脱敏、普通对象 JSON 序列化或 Maven 发布。
- 之前已提交的文件操作出错后，导出持续返回失败，避免假报完整 ZIP；后续写入继续执行。IO 初始化失败允许重试初始化；已成功初始化不会通过重复 init 改配置。
