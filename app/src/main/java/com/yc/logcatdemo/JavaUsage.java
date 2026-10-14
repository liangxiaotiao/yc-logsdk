package com.yc.logcatdemo;

import android.content.Context;
import com.yc.yclogsdk.LogLevel;
import com.yc.yclogsdk.Logger;
import java.util.LinkedHashMap;
import java.util.Map;

/** 实际参与编译/运行的 Java 静态调用示例。 */
public final class JavaUsage {
    private JavaUsage() {}
    public static void registerBluetoothSdk(Context context) {
        Logger.init(context);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Bluetooth SDK", "1.3.5");
        headers.put("Protocol Version", "2.0");
        Logger.addFileHeader(headers);
        Logger.iTag("Bluetooth", "SDK 注册完成，复用 App 文件");
    }
    public static void demonstrate() {
        Logger.setLevel(LogLevel.VERBOSE);
        Logger.dTag("Java", "Java 调用示例", 42, null);
        Logger.json("JavaJson", "{\"device\":\"DemoDevice\",\"connected\":true}");
        Logger.xml("JavaXml", "<device><name>DemoDevice</name></device>");
        Logger.eTag("Java", new IllegalStateException("Java 异常", new IllegalArgumentException("原因")));
        Logger.wtfTag("Java", "ASSERT 示例，不触发宿主 fatal handler");
    }
    public static void export(Context context, java.util.function.Consumer<String> result) {
        Logger.export(context, exported -> result.accept(exported.isSuccess()
                ? exported.getFile().getAbsolutePath() : "导出失败: " + exported.getError()));
    }
}
