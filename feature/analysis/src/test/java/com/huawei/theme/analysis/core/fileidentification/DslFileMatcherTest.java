package com.huawei.theme.analysis.core.fileidentification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DslFileMatcherTest {

    @Test
    void dslFileMatcherInterfaceExists() {
        DslFileMatcher matcher = new StubMatcher();
        assertTrue(matcher.isDslFile("test.xml", "<Lockscreen>"));
    }

    private static class StubMatcher implements DslFileMatcher {
        @Override
        public boolean isDslFile(String filePath, String content) {
            return true;
        }
    }
}
