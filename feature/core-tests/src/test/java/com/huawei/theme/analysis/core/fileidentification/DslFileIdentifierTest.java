package com.huawei.theme.analysis.core.fileidentification;

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

class DslFileIdentifierTest {

    private static DslFileIdentifier identifier;
    private static String dslDir;

    @BeforeAll
    static void setUp() {
        String moduleDir = System.getProperty("user.dir");
        String rulesDir = moduleDir + "/src/main/resources/rules";
        RuleRepository repo = new JsonRuleLoader().loadFromDirectory(rulesDir);
        identifier = new DslFileIdentifier(repo);
        dslDir = moduleDir + "/src/test/resources/dsl";
    }

    private static String readResource(String fileName) throws IOException {
        return Files.readString(Path.of(dslDir, fileName), StandardCharsets.UTF_8);
    }

    @Test
    void identifyLockscreenAsDsl() throws IOException {
        String content = readResource("valid_lockscreen.xml");
        assertTrue(identifier.isDslFile("valid_lockscreen.xml", content));
    }

    @Test
    void identifyWidgetAsDsl() throws IOException {
        String content = readResource("valid_widget.xml");
        assertTrue(identifier.isDslFile("valid_widget.xml", content));
    }

    @Test
    void rejectRegularConfigXml() throws IOException {
        String content = readResource("regular_config.xml");
        assertFalse(identifier.isDslFile("regular_config.xml", content));
    }

    @Test
    void identifyMalformedUnclosedAsDsl() throws IOException {
        String content = readResource("error_unclosed.xml");
        assertTrue(identifier.isDslFile("error_unclosed.xml", content));
    }

    @Test
    void identifyMalformedQuotesAsDsl() throws IOException {
        String content = readResource("error_quotes.xml");
        assertTrue(identifier.isDslFile("error_quotes.xml", content));
    }

    @Test
    void rejectNonXmlExtension() {
        assertFalse(identifier.isDslFile("theme.txt", "<Lockscreen/>"));
        assertFalse(identifier.isDslFile("theme.json", "<Lockscreen/>"));
        assertFalse(identifier.isDslFile("theme", "<Lockscreen/>"));
    }

    @Test
    void acceptXmlExtensionCaseInsensitive() {
        assertTrue(identifier.isDslFile("theme.XML", "<Lockscreen/>"));
        assertTrue(identifier.isDslFile("theme.Xml", "<Lockscreen/>"));
    }

    @Test
    void rejectNullFilePath() {
        assertFalse(identifier.isDslFile(null, "<Lockscreen/>"));
    }

    @Test
    void rejectNullContent() {
        assertFalse(identifier.isDslFile("test.xml", null));
    }

    @Test
    void rejectEmptyContent() {
        assertFalse(identifier.isDslFile("test.xml", ""));
    }

    @Test
    void rejectContentWithNoElementTag() {
        assertFalse(identifier.isDslFile("test.xml", "just plain text, no tags"));
        assertFalse(identifier.isDslFile("test.xml", "<?xml version=\"1.0\"?>"));
    }

    @Test
    void identifyWithXmlDeclaration() {
        String content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<Lockscreen/>";
        assertTrue(identifier.isDslFile("test.xml", content));
    }

    @Test
    void identifyWithCommentBeforeRoot() {
        String content = "<?xml version=\"1.0\"?>\n<!-- a comment -->\n<Wallpaper/>";
        assertTrue(identifier.isDslFile("test.xml", content));
    }

    @Test
    void skipCommentContainingElementLikeText() {
        String content = "<!-- <Lockscreen> fake -->\n<configuration/>";
        assertFalse(identifier.isDslFile("test.xml", content));
    }

    @Test
    void rejectUnknownRootElement() {
        assertFalse(identifier.isDslFile("test.xml", "<UnknownRoot/>"));
        assertFalse(identifier.isDslFile("test.xml", "<html><body/></html>"));
    }

    @Test
    void identifyAllKnownRootElements() {
        assertTrue(identifier.isDslFile("a.xml", "<Lockscreen/>"));
        assertTrue(identifier.isDslFile("a.xml", "<Wallpaper/>"));
        assertTrue(identifier.isDslFile("a.xml", "<Widget/>"));
        assertTrue(identifier.isDslFile("a.xml", "<ChargingSkin/>"));
        assertTrue(identifier.isDslFile("a.xml", "<LongTake/>"));
    }

    @Test
    void identifyRootElementWithAttributes() {
        String content = "<?xml version=\"1.0\"?>\n<Lockscreen frameRate=\"60\" screenWidth=\"1080\">";
        assertTrue(identifier.isDslFile("test.xml", content));
    }
}
