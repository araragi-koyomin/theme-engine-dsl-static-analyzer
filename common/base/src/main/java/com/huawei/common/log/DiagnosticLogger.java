package com.huawei.common.log;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

public class DiagnosticLogger implements ILogger {
    private final Logger log;

    public DiagnosticLogger(String tag) {
        log = Logger.getInstance(tag);
    }

    @Override
    public void d(String message) {
        log.debug(message);
    }

    @Override
    public void i(String message) {
        log.info(message);
    }

    @Override
    public void w(String message) {
        log.warn(message);
    }

    @Override
    public void e(String message) {
        log.error(message);
    }

    @Override
    public void e(String message, @Nullable Throwable t) {
        log.error(message, t);
    }
}
