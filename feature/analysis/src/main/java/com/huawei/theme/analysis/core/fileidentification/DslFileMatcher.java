package com.huawei.theme.analysis.core.fileidentification;

public interface DslFileMatcher {
    boolean isDslFile(String filePath, String content);
}
