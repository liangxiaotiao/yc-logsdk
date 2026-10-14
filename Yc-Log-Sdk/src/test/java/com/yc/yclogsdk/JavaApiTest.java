package com.yc.yclogsdk;

import android.content.Context;
import org.junit.Test;
import java.util.LinkedHashMap;
import static org.junit.Assert.*;

/** Java 编译和静态调用验证；真实 Context 的初始化在 instrumentation 测试验证。 */
public class JavaApiTest {
    @Test public void staticMethodsAndOverloadsAreJavaCompatible() throws Exception {
        Logger.class.getMethod("init", Context.class);
        Logger.class.getMethod("init", Context.class, String.class, String.class, int.class, LogFileSize.class, int.class);
        Logger.class.getMethod("export", Context.class, LogExportCallback.class);
        Logger.v("ignored before init"); Logger.d("message", 42, null);
        Logger.iTag("Java", "info"); Logger.wTag("Java", "warning");
        Logger.eTag("Java", new IllegalStateException("exception"));
        Logger.a("assert"); Logger.wtfTag("Java", "wtf");
        Logger.json("Java", "{\"a\":1}"); Logger.xml("Java", "<root/>");
        Logger.addFileHeader(new LinkedHashMap<String, String>());
        Logger.clearLogs(); Logger.setLevel(LogLevel.INFO); Logger.setLogcatEnabled(false);
        assertEquals(LogLevel.VERBOSE, Logger.getLevel());
        assertFalse(Logger.getLogcatEnabled());
        assertEquals(20 * 1024 * 1024, LogFileSize.MB_20.getBytes());
    }
}
