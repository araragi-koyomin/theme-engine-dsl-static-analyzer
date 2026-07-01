package com.huawei.theme.analysis.core.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CliConfigTest {

    @Test
    void fromArgsWithNoTypeCheckAndVerbose() {
        CliConfig config = CliConfig.fromArgs(new String[]{
                "--rule-dir", "/path/to/rules", "--no-type-check", "--verbose", "theme.xml"
        });
        assertEquals("/path/to/rules", config.getRuleDir());
        assertFalse(config.isTypeCheck());
        assertTrue(config.isVerbose());
        assertEquals("theme.xml", config.getTargetPath());
    }

    @Test
    void fromArgsWithOnlyTargetPath() {
        CliConfig config = CliConfig.fromArgs(new String[]{"theme.xml"});
        assertNull(config.getRuleDir());
        assertTrue(config.isTypeCheck());
        assertFalse(config.isVerbose());
        assertEquals("theme.xml", config.getTargetPath());
    }

    @Test
    void fromArgsWithNoTypeCheckFlag() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--no-type-check", "theme.xml"});
        assertFalse(config.isTypeCheck());
        assertFalse(config.isVerbose());
    }

    @Test
    void fromArgsWithVerboseFlag() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--verbose", "theme.xml"});
        assertTrue(config.isTypeCheck());
        assertTrue(config.isVerbose());
    }

    @Test
    void fromArgsWithRuleDirWithValue() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--rule-dir", "/custom/rules", "theme.xml"});
        assertEquals("/custom/rules", config.getRuleDir());
        assertTrue(config.isTypeCheck());
    }

    @Test
    void fromArgsThrowsWhenTargetPathMissing() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CliConfig.fromArgs(new String[]{"--verbose"})
        );
        assertEquals("No target path provided", ex.getMessage());
    }

    @Test
    void fromArgsThrowsWhenRuleDirMissingValue() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CliConfig.fromArgs(new String[]{"--rule-dir"})
        );
        assertEquals("--rule-dir requires a path value", ex.getMessage());
    }

    @Test
    void fromArgsWithEmptyArrayThrows() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CliConfig.fromArgs(new String[]{})
        );
        assertEquals("No target path provided", ex.getMessage());
    }

    @Test
    void fromArgsWithFlagsBeforeAndAfterTargetPath() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--verbose", "theme.xml", "--no-type-check"});
        assertTrue(config.isVerbose());
        assertFalse(config.isTypeCheck());
        assertEquals("theme.xml", config.getTargetPath());
    }

    @Test
    void fromArgsWithRuleDirAndPathContainingSpaces() {
        CliConfig config = CliConfig.fromArgs(new String[]{
                "--rule-dir", "C:/path with spaces/rules", "theme.xml"
        });
        assertEquals("C:/path with spaces/rules", config.getRuleDir());
        assertEquals("theme.xml", config.getTargetPath());
    }

    @Test
    void fromArgsWithDuplicateFlags() {
        CliConfig config = CliConfig.fromArgs(new String[]{
                "--verbose", "--verbose", "theme.xml"
        });
        assertTrue(config.isVerbose());
        assertEquals("theme.xml", config.getTargetPath());

        CliConfig config2 = CliConfig.fromArgs(new String[]{
                "--rule-dir", "/first/rules", "--rule-dir", "/second/rules", "theme.xml"
        });
        assertEquals("/second/rules", config2.getRuleDir());
        assertEquals("theme.xml", config2.getTargetPath());
    }

    @Test
    void fromArgsThrowsWhenMultiplePositionalArgs() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CliConfig.fromArgs(new String[]{"theme.xml", "layout.xml"})
        );
        assertEquals("Multiple target paths provided. Only one <file-or-directory> argument is allowed.", ex.getMessage());
    }

    @Test
    void fromArgsWithOnlyRuleDirAndTarget() {
        CliConfig config = CliConfig.fromArgs(new String[]{
                "--rule-dir", "/custom/rules", "theme.xml"
        });
        assertEquals("/custom/rules", config.getRuleDir());
        assertTrue(config.isTypeCheck());
        assertFalse(config.isVerbose());
        assertEquals("theme.xml", config.getTargetPath());
    }

    @Test
    void fromArgsWithHelpFlag() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--help", "theme.xml"});
        assertTrue(config.isHelpRequested());
    }

    @Test
    void fromArgsWithShortHelpFlag() {
        CliConfig config = CliConfig.fromArgs(new String[]{"-h", "theme.xml"});
        assertTrue(config.isHelpRequested());
    }

    @Test
    void fromArgsWithHelpFlagDoesNotRequireTarget() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--help"});
        assertTrue(config.isHelpRequested());
        assertNull(config.getTargetPath());
    }

    @Test
    void fromArgsThrowsWhenTypeCheckFlagUsed() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CliConfig.fromArgs(new String[]{"--type-check", "theme.xml"})
        );
        assertEquals("Unknown option: --type-check", ex.getMessage());
    }
}
