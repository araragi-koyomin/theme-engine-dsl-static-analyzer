package com.huawei.theme.analysis.core.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliConfigExtendedTest {

    @Test
    void fromArgsWithFormatJson() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--format", "json", "theme.xml"});
        assertEquals("json", config.getFormat());
        assertEquals("theme.xml", config.getTargetPath());
    }

    @Test
    void fromArgsWithFormatMarkdown() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--format", "markdown", "theme.xml"});
        assertEquals("markdown", config.getFormat());
    }

    @Test
    void fromArgsDefaultFormatIsTerminal() {
        CliConfig config = CliConfig.fromArgs(new String[]{"theme.xml"});
        assertEquals("terminal", config.getFormat());
    }

    @Test
    void fromArgsThrowsWhenFormatMissingValue() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CliConfig.fromArgs(new String[]{"--format"})
        );
        assertEquals("--format requires a value (json/markdown/terminal)", ex.getMessage());
    }

    @Test
    void fromArgsWithOutputPath() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--output", "/tmp/report.json", "theme.xml"});
        assertEquals("/tmp/report.json", config.getOutputPath());
    }

    @Test
    void fromArgsThrowsWhenOutputMissingValue() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CliConfig.fromArgs(new String[]{"--output"})
        );
        assertEquals("--output requires a path value", ex.getMessage());
    }

    @Test
    void fromArgsWithNoColor() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--no-color", "theme.xml"});
        assertTrue(config.isNoColor());
    }

    @Test
    void fromArgsDefaultNoColorIsFalse() {
        CliConfig config = CliConfig.fromArgs(new String[]{"theme.xml"});
        assertFalse(config.isNoColor());
    }

    @Test
    void fromArgsWithQuiet() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--quiet", "theme.xml"});
        assertTrue(config.isQuiet());
        assertFalse(config.isVerbose());
    }

    @Test
    void fromArgsWithSyntaxOnly() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--syntax-only", "theme.xml"});
        assertTrue(config.isSyntaxOnly());
        assertFalse(config.isSemanticOnly());
    }

    @Test
    void fromArgsWithSemanticOnly() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--semantic-only", "theme.xml"});
        assertTrue(config.isSemanticOnly());
        assertFalse(config.isSyntaxOnly());
    }

    @Test
    void fromArgsWithVersion() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--version"});
        assertTrue(config.isVersionRequested());
    }

    @Test
    void fromArgsWithAllNewFlagsCombined() {
        CliConfig config = CliConfig.fromArgs(new String[]{
                "--format", "json", "--output", "/tmp/report.json",
                "--no-color", "--no-type-check", "--verbose",
                "--rule-dir", "/rules", "theme.xml"
        });
        assertEquals("json", config.getFormat());
        assertEquals("/tmp/report.json", config.getOutputPath());
        assertTrue(config.isNoColor());
        assertFalse(config.isTypeCheck());
        assertTrue(config.isVerbose());
        assertEquals("/rules", config.getRuleDir());
        assertEquals("theme.xml", config.getTargetPath());
    }
}
