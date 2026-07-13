package com.huawei.theme.analysis.core.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliOutputFormatterExtendedTest {

    @Test
    void formatWarningReturnsWarningPrefix() {
        assertEquals("Warning: missing rule files", CliOutputFormatter.formatWarning("missing rule files"));
    }

    @Test
    void formatVersionReturnsVersionString() {
        assertEquals("dsl-analyzer 0.1.0", CliOutputFormatter.formatVersion());
    }
}
