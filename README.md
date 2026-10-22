<div align="center">

# YC Log SDK

轻量 Android 日志库 · Kotlin & Java · Logcat / 文件 / ZIP 导出

[![Maven Central](https://img.shields.io/badge/Maven_Central-1.0.3-2563eb)](https://central.sonatype.com/artifact/io.github.liangxiaotiao/yc-log-sdk/1.0.3)
[![Android](https://img.shields.io/badge/Android-API_24%2B-16a34a)](#1-添加依赖)
[![License](https://img.shields.io/badge/License-Apache_2.0-64748b)](LICENSE)

**[添加依赖](#1-添加依赖) · [初始化](#2-初始化) · [打印日志](#3-打印日志) · [导出日志](#4-导出日志)**

</div>

---

## 1. 添加依赖

接入要求：**minSdk ≥ 24、compileSdk ≥ 35**。已验证宿主构建环境：AGP 8.6.0、Kotlin 2.1.0；SDK 字节码目标为 Java 11。

在 `settings.gradle.kts` 的仓库配置中添加 Maven Central：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

在 App 模块的 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation("io.github.liangxiaotiao:yc-log-sdk:1.0.3")
}
```

<details>
<summary>使用 Groovy / build.gradle</summary>

```groovy
// settings.gradle 的 dependencyResolutionManagement.repositories 中
mavenCentral()

// app/build.gradle 的 dependencies 中
implementation 'io.github.liangxiaotiao:yc-log-sdk:1.0.3'
```

</details>

## 2. 初始化

在 `Application.onCreate()` 中初始化，并在 Manifest 注册该 Application：

```kotlin
import android.app.Application
import com.yc.yclogsdk.Logger

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.init(this, name = "MyApp")
    }
}
```

```xml
<!-- 在现有 application 节点添加 android:name，保留其他属性和组件。 -->
<application android:name=".MyApplication">
    <!-- 原有 Activity、Service 等组件声明 -->
</application>
```

默认保留最近 **7 天**日志，单文件上限 **20 MiB**，目录为 `filesDir/logs`。无需存储权限；支持单进程。先初始化再调用日志 API，后续重复初始化不会覆盖首次生效的配置。

Java 项目同样在 `Application.onCreate()` 中调用：

```java
com.yc.yclogsdk.Logger.init(this, "MyApp");
```


## 3. 打印日志

```kotlin
Logger.d("连接成功")
Logger.iTag("Bluetooth", "开始连接设备")
Logger.d("设备信息", "Band", 85)
Logger.e(IllegalStateException("连接失败"))
Logger.json("""{"device":"Band","connected":true}""")
Logger.xml("<device><name>Band</name></device>")
Logger.addFileHeader(mapOf("App Version" to "1.0.0"))
```

Debug 宿主默认同时输出 Logcat 和文件；Release 宿主默认只写文件。日志支持 `v / d / i / w / e / a / wtf`。

<details>
<summary>Java 调用</summary>

```java
import com.yc.yclogsdk.Logger;
import java.util.Collections;

Logger.d("连接成功");
Logger.iTag("Bluetooth", "开始连接设备");
Logger.addFileHeader(Collections.singletonMap("App Version", "1.0.0"));
```

</details>

## 4. 导出日志

```kotlin
Logger.export(applicationContext) { result ->
    if (result.isSuccess) {
        val zipFile = result.file!!
        // 将 ZIP 交给自己的保存或分享流程。
    } else {
        val error = result.error
        // 处理导出失败。
    }
}
```

ZIP 生成在 `cacheDir`；回调在后台文件线程执行，更新 UI 时切回主线程。SDK 不会自动上传或分享日志。

需要直接阅读时可用 `Logger.export(context, callback, true)`：ZIP 内日志去掉每条记录的 `@record` 头；两参数版本保持原始格式。

若通过自己的封装类（如 `MLog`）调用 Logger，初始化时登记封装类全名，位置与默认 TAG 会指向真正的业务调用处：

```kotlin
Logger.addCallerSkip("com.example.app.MLog")
```

<details>
<summary>Java 导出</summary>

```java
Logger.export(getApplicationContext(), result -> {
    if (result.isSuccess()) {
        java.io.File zipFile = result.getFile();
        // 保存或分享 ZIP。
    } else {
        Throwable error = result.getError();
        // 处理导出失败。
    }
});
```

</details>

---

[完整接入示例](app/src/main/java/com/yc/logcatdemo) · [维护者发布指南](docs/MAVEN_PUBLISH.md)
