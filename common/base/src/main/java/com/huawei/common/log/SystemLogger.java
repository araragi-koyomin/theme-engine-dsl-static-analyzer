package com.huawei.common.log;

import com.huawei.common.utils.DateUtils;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class SystemLogger implements ILogger {
    private final String tag;

    public SystemLogger(String tag) {
        this.tag = tag;
    }

    @Override
    public void d(String msg) {
        i(msg);
    }

    @Override
    public void i(String msg) {
        String format = String.format(Locale.ENGLISH, "[%s] [%d] [%s] %s", DateUtils.getConsoleNow(), Thread.currentThread().getId(), tag, msg);
        System.out.println(format);
    }

    @Override
    public void w(String msg) {
        i(msg);
    }

    @Override
    public void e(String msg) {
        i(msg);
    }

    @Override
    public void e(String msg, @Nullable Throwable t) {
        String format = String.format(Locale.ENGLISH, "[%s] [%d] [%s] %s", DateUtils.getConsoleNow(), Thread.currentThread().getId(), tag, msg + (t == null ? "" : (" " + t.getMessage())));
        System.out.println(format);
    }
}
