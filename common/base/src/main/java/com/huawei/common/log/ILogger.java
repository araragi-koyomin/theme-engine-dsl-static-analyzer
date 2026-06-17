package com.huawei.common.log;

import org.jetbrains.annotations.Nullable;

public interface ILogger {
    void d(String message);
    void i(String message);
    void w(String message);
    void e(String message);
    void e(String message, @Nullable Throwable t);
}
