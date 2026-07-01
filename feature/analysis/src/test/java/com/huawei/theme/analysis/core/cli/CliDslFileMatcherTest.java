package com.huawei.theme.analysis.core.cli;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;

class CliDslFileMatcherTest {

    private static CliDslFileMatcher matcher;
    private static String dslDir;

    @BeforeAll
    static void setUp() {
        String moduleDir = System.getProperty("user.dir");
        String rulesDir = moduleDir + "/src/main/resources/rules";
        RuleRepository repo = new JsonRuleLoader().loadFromDirectory(rulesDir);
        matcher = new CliDslFileMatcher(repo);
        dslDir = moduleDir + "/src/test/resources/dsl";
    }

    @Test
    void isDslFileWithValidLockscreen() {
        File file = new File(dslDir, "valid_lockscreen.xml");
        assertTrue(matcher.isDslFile(file));
    }

    @Test
    void rejectRegularConfigXml() {
        File file = new File(dslDir, "regular_config.xml");
        assertFalse(matcher.isDslFile(file));
    }

    @Test
    void rejectNonXmlExtension() throws IOException {
        Path tempTxt = Files.createTempFile("dsl-test", ".txt");
        Files.writeString(tempTxt, "<Lockscreen/>", StandardCharsets.UTF_8);
        assertFalse(matcher.isDslFile(tempTxt.toFile()));
        Files.deleteIfExists(tempTxt);

        Path tempJson = Files.createTempFile("dsl-test", ".json");
        Files.writeString(tempJson, "<Lockscreen/>", StandardCharsets.UTF_8);
        assertFalse(matcher.isDslFile(tempJson.toFile()));
        Files.deleteIfExists(tempJson);
    }

    @Test
    void isDslFileStringNonexistentPathReturnsFalse() {
        assertFalse(matcher.isDslFile("/nonexistent/path/theme.xml"));
    }

    @Test
    void isDslFileStringStringMatchesDslFileIdentifier() {
        assertTrue(matcher.isDslFile("test.xml", "<Lockscreen/>"));
        assertFalse(matcher.isDslFile("test.xml", "<configuration/>"));
        assertFalse(matcher.isDslFile("test.txt", "<Lockscreen/>"));
    }

    @Test
    void isDslFileWithUnreadableFileReturnsFalse() throws IOException {
        Path tempFile = Files.createTempFile("dsl-test", ".xml");
        Files.writeString(tempFile, "<Lockscreen/>", StandardCharsets.UTF_8);
        File file = tempFile.toFile();
        assertTrue(matcher.isDslFile(file));
        Files.deleteIfExists(tempFile);
        assertFalse(matcher.isDslFile(file));
    }
}
