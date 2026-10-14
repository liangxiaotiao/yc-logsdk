# Maven Central 发布指南

适用于本仓库的 `Yc-Log-Sdk` 模块。当前待发布版本为 **1.0.1**，坐标为 `io.github.liangxiaotiao:yc-log-sdk:1.0.1`。按顺序执行以下步骤；已完成的账号和密钥配置无需重复设置。

## 1. 准备构建环境

| 项目 | 版本 |
| --- | --- |
| JDK | 17 |
| Gradle | 8.7（使用仓库 wrapper） |
| AGP / Kotlin | 8.6.0 / 2.1.0 |
| Android SDK / Build Tools | 35 / 34.0.0 |
| Vanniktech maven-publish | 0.34.0 |

本机 macOS 操作：

```bash
cd /Users/jump/Android/YuCheng/LogcatDemo
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
java -version
bash ./gradlew --version
git status --short --branch
```

其他电脑先克隆 `https://github.com/liangxiaotiao/yc-logsdk.git` 并进入仓库根目录。通过 Android Studio SDK Manager 安装 SDK 35、Build Tools 34.0.0，在本地 `local.properties` 配置 `sdk.dir`。确认 JDK 为 17、Gradle 为 8.7，且待发布改动已提交。

## 2. 配置 Central Portal 凭据

1. 登录 [Central Portal](https://central.sonatype.com/)，确认命名空间 `io.github.liangxiaotiao` 已验证。
2. 在账号菜单的 **View Account → Generate User Token** 生成发布 token。
3. 将生成的用户名和密码配置在本机 `~/.gradle/gradle.properties`，已有文件只补充缺失项：

```properties
mavenCentralUsername=YOUR_TOKEN_USERNAME
mavenCentralPassword=YOUR_TOKEN_PASSWORD
```

这里使用的是 **User Token**，不是网页登录密码。不要将凭据放入仓库的 `gradle.properties`、文档或提交记录。

## 3. 确认 GPG 签名

仓库使用 `signing { useGpgCmd() }` 调用本机 GPG：

```bash
gpg --version
gpg --list-secret-keys --keyid-format LONG
```

将实际用于签名的密钥 ID 写入本机 `~/.gradle/gradle.properties`：

```properties
signing.gnupg.keyName=YOUR_SIGNING_KEY_ID
```

密码由本机 GPG agent / pinentry 输入。已有有效密钥可直接使用；若尚无密钥，先执行 `gpg --full-generate-key` 创建，再将公钥发布到 keyserver（将下面 ID 替换为实际值）：

```bash
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys YOUR_SIGNING_KEY_ID
```

仅发布公钥，不导出或上传私钥。Central 必须能找到签名公钥。下面的本地发布必须成功签名后，才能继续上传。

## 4. 核对版本与发布配置

`Yc-Log-Sdk/build.gradle` 中应为：

```groovy
mavenPublishing {
    coordinates('io.github.liangxiaotiao', 'yc-log-sdk', '1.0.1')
    publishToMavenCentral()
    signAllPublications()
    // 保留仓库现有完整 pom 配置。
}
```

不要用此片段覆盖整个发布配置。`gradle/libs.versions.toml` 中发布插件保持 **0.34.0**；0.37.0 要求 Gradle 9.0 / AGP 8.13 / Kotlin 2.2，不适合当前构建基线。

先检查当前版本是否已存在：

```bash
curl --silent --show-error --location --output /dev/null --write-out '%{http_code}\n' \
  https://repo.maven.apache.org/maven2/io/github/liangxiaotiao/yc-log-sdk/1.0.1/yc-log-sdk-1.0.1.pom
```

- `404`：再检查 Central Portal 的 [Deployments](https://central.sonatype.com/publishing/deployments) 是否有相同版本待发布；没有则继续。
- `200`：该版本已发布，停止上传。后续版本使用新的版本号。
- 其他状态或网络失败：先排查网络，不能据此认定版本不存在。

已发布的 **1.0.0 不可覆盖**；每次后续发布均替换本指南命令和依赖示例中的版本号。

## 5. 编译和测试

```bash
bash ./gradlew :app:assembleDebug :app:assembleRelease \
  :Yc-Log-Sdk:assembleDebug :Yc-Log-Sdk:assembleRelease test \
  :app:assembleDebugAndroidTest :Yc-Log-Sdk:assembleDebugAndroidTest \
  :app:lintDebug :app:lintRelease :Yc-Log-Sdk:lintDebug :Yc-Log-Sdk:lintRelease \
  --max-workers=4 --console=plain
```

必须看到 `BUILD SUCCESSFUL`，并确认测试无失败、lint 无错误。此命令编译 Android 测试 APK，但不执行设备测试；设备测试另用测试设备运行，不能把编译成功当作设备验证。

## 6. 本地发布并检查产物

```bash
bash ./gradlew :Yc-Log-Sdk:publishToMavenLocal --max-workers=4 --console=plain
ls "$HOME/.m2/repository/io/github/liangxiaotiao/yc-log-sdk/1.0.1/"
unzip -p Yc-Log-Sdk/build/outputs/aar/Yc-Log-Sdk-release.aar \
  META-INF/com/android/build/gradle/aar-metadata.properties
gpg --verify \
  "$HOME/.m2/repository/io/github/liangxiaotiao/yc-log-sdk/1.0.1/yc-log-sdk-1.0.1.aar.asc" \
  "$HOME/.m2/repository/io/github/liangxiaotiao/yc-log-sdk/1.0.1/yc-log-sdk-1.0.1.aar"
```

确认有 `.aar`、`-sources.jar`、`-javadoc.jar`、`.pom`、`.module` 及各自 `.asc` 签名；AAR 的 `minCompileSdk=35`，没有 API 36.1 要求；GPG 验证成功。本地发布不会上传 Central。正式发布验证不要跳过签名任务。

## 7. 提交并推送 GitHub

本仓库按当前授权直接在 `main` 操作，先核对暂存内容，不纳入凭据、本地产物或无关改动：

```bash
git switch main
git diff --check
git add README.md docs/MAVEN_PUBLISH.md
git diff --cached
git commit -m "docs: simplify integration guide and document Maven publishing"
git push github main
```

以上暂存命令针对本次文档变更。以后若调整版本或源码，按实际文件明确添加并提交，再发布对应已提交的版本。不要强制推送。

## 8. 上传并自动发布 1.0.1

本机凭据和 GPG 已配置时，执行：

```bash
bash ./gradlew :Yc-Log-Sdk:publishAndReleaseToMavenCentral \
  --no-configuration-cache --max-workers=4 --console=plain
```

该任务上传产物并请求自动发布；Central 会异步校验，通过后正式发布，**会让版本公开且无法覆盖**。0.34.0 的 Gradle 任务不等待远程校验和仓库同步完成；`BUILD SUCCESSFUL` 后仍需执行步骤 9 确认结果。本次已由用户明确授权。不要重复执行来判断是否成功；应查看日志及 Central Portal 的 deployment 状态。若校验失败，在 Portal 查看具体原因后修正，确认已有 deployment 的状态再重试。

若只想上传后在 Portal 手动确认，则改为执行下面的任务，并在校验通过后点击 **Publish**：

```bash
bash ./gradlew :Yc-Log-Sdk:publishToMavenCentral \
  --no-configuration-cache --max-workers=4 --console=plain
```

这两个上传方式选择一个，不要对同一版本连续执行两次。`publishToMavenLocal` 成功只证明本地产物有效，不证明远程上传或发布完成。

## 9. 确认仓库可下载

Central Portal 显示 `PUBLISHED` 后，等待仓库同步；通常还需 10–30 分钟。执行：

```bash
curl --fail --silent --show-error --location \
  https://repo.maven.apache.org/maven2/io/github/liangxiaotiao/yc-log-sdk/1.0.1/yc-log-sdk-1.0.1.pom
curl --fail --silent --show-error --location --output /tmp/yc-log-sdk-1.0.1.aar \
  https://repo.maven.apache.org/maven2/io/github/liangxiaotiao/yc-log-sdk/1.0.1/yc-log-sdk-1.0.1.aar
```

下载成功后，在只配置 `google()` / `mavenCentral()` 的宿主中接入：

```kotlin
implementation("io.github.liangxiaotiao:yc-log-sdk:1.0.1")
```

最后确认宿主编译通过。若 Portal 已发布而文件暂时 404，等待同步后再检查，不重新上传。

## 常见问题

| 现象 | 处理 |
| --- | --- |
| HTTP 401 / 403 | 检查 User Token、命名空间权限；不要把 token 打印到日志 |
| GPG 失败 | 检查本机密钥、keyName 和密码提示；先让本地签名发布成功 |
| Central 校验找不到公钥 | 将签名公钥分发到 keyserver，等待可查询后再处理 deployment |
| 版本重复 | 检查已发布文件和 Portal deployment；不要覆盖或重复上传 |
| Kotlin / AAR metadata 不兼容 | 确认宿主 Kotlin 2.1.0、AGP 8.6.0、compileSdk 35 的验证基线 |

参考：[Vanniktech Maven Central 指南](https://vanniktech.github.io/gradle-maven-publish-plugin/central/) · [0.34.0 兼容说明](https://github.com/vanniktech/gradle-maven-publish-plugin/releases/tag/0.34.0) · [Central Portal 发布流程](https://central.sonatype.org/publish/publish-portal-api/)
