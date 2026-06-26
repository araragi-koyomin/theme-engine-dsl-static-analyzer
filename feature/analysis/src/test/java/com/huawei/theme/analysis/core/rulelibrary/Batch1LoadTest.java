package com.huawei.theme.analysis.core.rulelibrary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;

class Batch1LoadTest {

    private String getRulesDir() {
        String moduleDir = System.getProperty("user.dir");
        return moduleDir + "/src/main/resources/rules";
    }

    @Test
    void loadRootElementsAndVerify() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        assertTrue(repo.getElementRule("Lockscreen").isPresent());
        assertTrue(repo.getElementRule("Wallpaper").isPresent());
        assertTrue(repo.getElementRule("Widget").isPresent());
        assertTrue(repo.getElementRule("ChargingSkin").isPresent());

        assertEquals(5, repo.getRootElementNames().size());
    }

    @Test
    void loadVarAndVerify() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule varRule = repo.getElementRule("Var").orElseThrow();
        assertEquals("variable", varRule.getCategory());
        assertEquals(1, varRule.getRequiredAttrs().size());
        assertTrue(varRule.getConstraints().size() >= 2);

        AttrTypeSpec typeAttr = repo.getAttrTypeSpec("Var", "type").orElseThrow();
        assertEquals("number", typeAttr.getDefaultValue());
    }

    @Test
    void loadVarArrayAndArray() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule varArray = repo.getElementRule("VarArray").orElseThrow();
        assertEquals("variable", varArray.getCategory());

        DslElementRule array = repo.getElementRule("Array").orElseThrow();
        assertEquals("variable", array.getCategory());
        assertTrue(array.getOptionalAttrs().contains("x"));
        assertTrue(array.getOptionalAttrs().contains("y"));
    }

    @Test
    void loadGlobalVarsAndVerify() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        assertTrue(repo.getGlobalVar("battery_level").isPresent());
        assertTrue(repo.getGlobalVar("ishour12").isPresent());
        assertTrue(repo.getAllGlobalVars().size() >= 30);
    }

    @Test
    void verifyScopeMatrix() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule lockscreen = repo.getElementRule("Lockscreen").orElseThrow();
        assertTrue(lockscreen.getScope().get("Lockscreen"));
    }
}

class Batch2LoadTest {

    private String getRulesDir() {
        String moduleDir = System.getProperty("user.dir");
        return moduleDir + "/src/main/resources/rules";
    }

    @Test
    void loadViewElementsAndVerify() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        assertTrue(repo.getElementRule("Text").isPresent());
        assertTrue(repo.getElementRule("Image").isPresent());
        assertTrue(repo.getElementRule("Video").isPresent());
        assertTrue(repo.getElementRule("Time").isPresent());
        assertTrue(repo.getElementRule("DateTime").isPresent());
    }

    @Test
    void verifyAliasResolve() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        assertEquals("width", repo.resolveAttrAlias("Text", "w").orElseThrow());
        assertEquals("height", repo.resolveAttrAlias("Text", "h").orElseThrow());
        assertEquals("pivotX", repo.resolveAttrAlias("Text", "centerX").orElseThrow());
        assertEquals("pivotY", repo.resolveAttrAlias("Text", "centerY").orElseThrow());
        assertEquals("rotation", repo.resolveAttrAlias("Text", "angle").orElseThrow());
        assertEquals("rotationX", repo.resolveAttrAlias("Text", "angleX").orElseThrow());
        assertEquals("rotationY", repo.resolveAttrAlias("Text", "angleY").orElseThrow());

        assertEquals("width", repo.resolveAttrAlias("Text", "width").orElseThrow());

        AttrTypeSpec widthSpec = repo.getAttrTypeSpec("Text", "w").orElseThrow();
        assertEquals("number", widthSpec.getType());
        assertTrue(widthSpec.isSupportsExpression());
        assertEquals("number", widthSpec.getExpressionKind());

        AttrTypeSpec heightSpec = repo.getAttrTypeSpec("Image", "h").orElseThrow();
        assertEquals("number", heightSpec.getType());
    }

    @Test
    void verifyCanonicalAttrNames() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        assertTrue(repo.getCanonicalAttrNames("Text").contains("width"));
        assertTrue(repo.getCanonicalAttrNames("Text").contains("height"));
        assertTrue(!repo.getCanonicalAttrNames("Text").contains("w"));
        assertTrue(!repo.getCanonicalAttrNames("Text").contains("h"));

        assertTrue(repo.getCanonicalAttrNames("Image").contains("loop"));
    }

    @Test
    void verifyTextAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule text = repo.getElementRule("Text").orElseThrow();
        assertEquals("view", text.getCategory());
        assertTrue(text.getOptionalAttrs().contains("x"));
        assertTrue(text.getOptionalAttrs().contains("color"));
        assertTrue(text.getOptionalAttrs().contains("textExp"));
        assertTrue(text.getOptionalAttrs().contains("format"));
        assertTrue(text.getOptionalAttrs().contains("paras"));
        assertTrue(!text.getOptionalAttrs().contains("w"));
        assertTrue(!text.getOptionalAttrs().contains("h"));

        AttrTypeSpec xSpec = repo.getAttrTypeSpec("Text", "x").orElseThrow();
        assertTrue(xSpec.isSupportsExpression());
        assertEquals("number", xSpec.getExpressionKind());
        assertEquals("0", xSpec.getDefaultValue());

        AttrTypeSpec sizeSpec = repo.getAttrTypeSpec("Text", "size").orElseThrow();
        assertEquals("40", sizeSpec.getDefaultValue());

        AttrTypeSpec parasSpec = repo.getAttrTypeSpec("Text", "paras").orElseThrow();
        assertEquals("auto", parasSpec.getExpressionKind());
    }

    @Test
    void verifyTextConstraints() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule text = repo.getElementRule("Text").orElseThrow();
        assertTrue(text.getConstraints().size() >= 4);

        assertTrue(text.getConstraints().stream()
                .anyMatch(c -> "SEM-ATTR-007".equals(c.getRuleId())));
        assertTrue(text.getConstraints().stream()
                .anyMatch(c -> "SEM-ATTR-008".equals(c.getRuleId())));
        assertTrue(text.getConstraints().stream()
                .anyMatch(c -> "SEM-ATTR-009".equals(c.getRuleId())));
    }

    @Test
    void verifyVideoRequiredAttrs() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule video = repo.getElementRule("Video").orElseThrow();
        assertEquals("view", video.getCategory());
        assertEquals(2, video.getRequiredAttrs().size());
        assertTrue(video.getRequiredAttrs().contains("name"));
        assertTrue(video.getRequiredAttrs().contains("src"));
        assertTrue(video.getConstraints().size() >= 1);
        assertTrue(!video.getOptionalAttrs().contains("w"));
        assertTrue(!video.getOptionalAttrs().contains("h"));
    }

    @Test
    void verifyTimeNoWidthHeight() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule time = repo.getElementRule("Time").orElseThrow();
        assertEquals("view", time.getCategory());
        assertTrue(time.getRequiredAttrs().contains("src"));
        assertTrue(!time.getOptionalAttrs().contains("width"));
        assertTrue(!time.getOptionalAttrs().contains("height"));
    }

    @Test
    void verifyDateTimeNoAlphaWidthHeight() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule datetime = repo.getElementRule("DateTime").orElseThrow();
        assertEquals("view", datetime.getCategory());
        assertTrue(datetime.getRequiredAttrs().contains("format"));
        assertTrue(datetime.getRequiredAttrs().contains("size"));
        assertTrue(!datetime.getOptionalAttrs().contains("alpha"));
        assertTrue(!datetime.getOptionalAttrs().contains("width"));
        assertTrue(!datetime.getOptionalAttrs().contains("height"));
        assertTrue(!datetime.getOptionalAttrs().contains("enableMove"));
        assertTrue(!datetime.getOptionalAttrs().contains("moveRect"));
    }

    @Test
    void verifyImageConstraints() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule image = repo.getElementRule("Image").orElseThrow();
        assertTrue(image.getConstraints().size() >= 3);

        assertTrue(image.getConstraints().stream()
                .anyMatch(c -> "SEM-IMG-002".equals(c.getRuleId())));
        assertTrue(image.getConstraints().stream()
                .anyMatch(c -> "SEM-IMG-003".equals(c.getRuleId())));
    }

    @Test
    void verifyImageLoopAttribute() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        assertTrue(repo.getElementRule("Image").orElseThrow().getOptionalAttrs().contains("loop"));

        AttrTypeSpec loopSpec = repo.getAttrTypeSpec("Image", "loop").orElseThrow();
        assertEquals("string", loopSpec.getType());
        assertTrue(loopSpec.getEnumValues().contains("true"));
        assertTrue(loopSpec.getEnumValues().contains("false"));
    }
}
