package com.huawei.theme.analysis.file;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;

import com.huawei.theme.analysis.rule.loader.JsonRuleLoader;
import com.huawei.theme.analysis.rule.model.DslElementRule;
import com.huawei.theme.analysis.rule.model.RuleSource;
import com.huawei.theme.analysis.rule.repository.RuleRepository;
import com.huawei.theme.analysis.rule.repository.RuleRepositoryImpl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslFileIdentifierTest {

    private DslFileIdentifier identifier;

    @BeforeEach
    void setUp() {
        JsonRuleLoader loader = new JsonRuleLoader();
        Map<String, DslElementRule> elementMap = loader.buildElementRuleMap("rules/test_rules.json");
        Map<String, RuleSource> sourceMap = loader.buildRuleSourceMap("rules/test_rules.json");
        RuleRepository repository = new RuleRepositoryImpl(elementMap, sourceMap);
        identifier = new DslFileIdentifier(repository);
    }

    private ByteArrayInputStream toInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void isDslFile_xmlWithDslRootElement_shouldReturnTrue() {
        for (String root : List.of("Lockscreen", "Wallpaper", "Widget", "ChargingSkin")) {
            String content = "<?xml version=\"1.0\" encoding=\"utf-8\"?><" + root + "></" + root + ">";
            assertTrue(identifier.isDslRootElementByContent(toInputStream(content)),
                    "Should return true for root element: " + root);
        }
    }

    @Test
    void isDslFile_nonXmlExtension_shouldReturnFalse() {
        LightVirtualFile txtFile = new LightVirtualFile("theme.txt", "some content");
        assertFalse(identifier.isDslFile(txtFile));

        LightVirtualFile jsonFile = new LightVirtualFile("config.json", "{}");
        assertFalse(identifier.isDslFile(jsonFile));

        assertFalse(identifier.isDslFile((VirtualFile) null));
    }

    @Test
    void isDslFile_xmlWithNonDslRootElement_shouldReturnFalse() {
        String manifestContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?><manifest></manifest>";
        assertFalse(identifier.isDslRootElementByContent(toInputStream(manifestContent)));

        String htmlContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?><html></html>";
        assertFalse(identifier.isDslRootElementByContent(toInputStream(htmlContent)));
    }

    @Test
    void isDslFile_nullVirtualFile_shouldReturnFalse() {
        assertFalse(identifier.isDslFile((VirtualFile) null));
    }

    @Test
    void rootElementNames_fromRuleRepository_notHardcoded() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = new RuleRepositoryImpl(loader, "rules/test_rules.json");
        List<String> roots = repo.getRootElementNames();
        assertTrue(roots.contains("Lockscreen"));
        assertTrue(roots.contains("Wallpaper"));
        assertTrue(roots.contains("Widget"));
        assertTrue(roots.contains("ChargingSkin"));
        assertFalse(roots.contains("Var"));
    }

    @Test
    void isDslRootElementByContent_dslRootWithAttributes_shouldReturnTrue() {
        String lockscreenWithAttrs = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<Lockscreen frameRate=\"60\" screenWidth=\"1080\">"
                + "<Var name=\"aniTime\" expression=\"#time\" />"
                + "</Lockscreen>";
        assertTrue(identifier.isDslRootElementByContent(toInputStream(lockscreenWithAttrs)));

        String widgetWithAttrs = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<Widget screenWidth=\"1080\" screenHeight=\"530\" frameRate=\"30\">"
                + "<Text text=\"Hello\" />"
                + "</Widget>";
        assertTrue(identifier.isDslRootElementByContent(toInputStream(widgetWithAttrs)));
    }

    @Test
    void isDslRootElementByContent_malformedXml_shouldReturnFalse() {
        String truncatedXml = "<?xml version=\"1.0\"?><Lockscreen><Var name=";
        assertFalse(identifier.isDslRootElementByContent(toInputStream(truncatedXml)));

        String garbledContent = "this is not xml at all!!!";
        assertFalse(identifier.isDslRootElementByContent(toInputStream(garbledContent)));

        String emptyContent = "";
        assertFalse(identifier.isDslRootElementByContent(toInputStream(emptyContent)));
    }

    @Test
    void isDslRootElementByContent_nestedDslInsideNonDslRoot_shouldReturnFalse() {
        String nestedContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<manifest>"
                + "<Lockscreen frameRate=\"60\" screenWidth=\"1080\">"
                + "<Var name=\"x\" />"
                + "</Lockscreen>"
                + "</manifest>";
        assertFalse(identifier.isDslRootElementByContent(toInputStream(nestedContent)));
    }

    @Test
    void isDslRootElementByContent_xmlWithCommentsBeforeRoot_shouldReturnTrue() {
        String withComments = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<!-- This is a theme file -->"
                + "<!-- Generated by tool -->"
                + "<Lockscreen frameRate=\"60\">"
                + "</Lockscreen>";
        assertTrue(identifier.isDslRootElementByContent(toInputStream(withComments)));
    }
}
