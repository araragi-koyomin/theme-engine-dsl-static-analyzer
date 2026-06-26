package com.huawei.common.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateUtils {
    public static final SimpleDateFormat CONSOLE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH);

    public static String getConsoleNow() {
        return CONSOLE_FORMAT.format(new Date(System.currentTimeMillis()));
    }
}
