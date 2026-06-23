package com.huawei.theme.analysis.core.rulelibrary;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Batch4LoadTest {

    private String getRulesDir() {
        String moduleDir = System.getProperty("user.dir");
        return moduleDir + "/src/main/resources/rules";
    }

    @Test
    void loadBatch4ElementsAndVerify() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        assertTrue(repo.getElementRule("Group").isPresent());
        assertTrue(repo.getElementRule("Button").isPresent());
        assertTrue(repo.getElementRule("SourceImage").isPresent());
        assertTrue(repo.getElementRule("ImageNumber").isPresent());
        assertTrue(repo.getElementRule("ImageSeries").isPresent());
    }

    @Test
    void verifyGroupAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule group = repo.getElementRule("Group").orElseThrow();
        assertEquals("layout", group.getCategory());
        assertEquals(0, group.getRequiredAttrs().size());
        assertTrue(group.getOptionalAttrs().contains("name"));
        assertTrue(group.getOptionalAttrs().contains("x"));
        assertTrue(group.getOptionalAttrs().contains("y"));
        assertTrue(group.getOptionalAttrs().contains("width"));
        assertTrue(group.getOptionalAttrs().contains("height"));
        assertTrue(group.getOptionalAttrs().contains("clip"));
        assertTrue(group.getOptionalAttrs().contains("layered"));
        assertTrue(group.getOptionalAttrs().contains("align"));
        assertTrue(group.getOptionalAttrs().contains("alignV"));

        AttrTypeSpec clipSpec = repo.getAttrTypeSpec("Group", "clip").orElseThrow();
        assertEquals("string", clipSpec.getType());
        assertTrue(clipSpec.getEnumValues().contains("true"));
        assertTrue(clipSpec.getEnumValues().contains("false"));
        assertEquals("false", clipSpec.getDefaultValue());

        AttrTypeSpec layeredSpec = repo.getAttrTypeSpec("Group", "layered").orElseThrow();
        assertEquals("string", layeredSpec.getType());
        assertTrue(layeredSpec.getEnumValues().contains("true"));
        assertTrue(layeredSpec.getEnumValues().contains("false"));

        AttrTypeSpec widthSpec = repo.getAttrTypeSpec("Group", "width").orElseThrow();
        assertTrue(widthSpec.getAliases().contains("w"));
        assertTrue(widthSpec.isSupportsExpression());
        assertEquals("number", widthSpec.getExpressionKind());

        assertTrue(group.getAllowedParents().contains("Lockscreen"));
        assertTrue(group.getAllowedParents().contains("Group"));
        assertTrue(group.getAllowedChildren().contains("Text"));
        assertTrue(group.getAllowedChildren().contains("Image"));
        assertTrue(group.getAllowedChildren().contains("Group"));
    }

    @Test
    void verifyGroupConstraints() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule group = repo.getElementRule("Group").orElseThrow();
        assertTrue(group.getConstraints().size() >= 2);

        RuleConstraint clipConstraint = group.getConstraints().stream()
                .filter(c -> "SEM-ATTR-004".equals(c.getRuleId()))
                .findFirst().orElseThrow();
        assertEquals("warning", clipConstraint.getSeverity().name().toLowerCase());

        RuleConstraint layeredConstraint = group.getConstraints().stream()
                .filter(c -> "SEM-ATTR-005".equals(c.getRuleId()))
                .findFirst().orElseThrow();
        assertEquals("error", layeredConstraint.getSeverity().name().toLowerCase());
    }

    @Test
    void verifyGroupScope() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule group = repo.getElementRule("Group").orElseThrow();
        assertTrue(group.getScope().get("Lockscreen"));
        assertTrue(group.getScope().get("Wallpaper"));
        assertTrue(group.getScope().get("LongTake"));
        assertTrue(group.getScope().get("Widget"));
        assertTrue(group.getScope().get("ChargingSkin"));
    }

    @Test
    void verifyGroupAliasResolution() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        AttrTypeSpec wSpec = repo.getAttrTypeSpec("Group", "w").orElseThrow();
        assertEquals("number", wSpec.getType());
        assertTrue(wSpec.isSupportsExpression());

        AttrTypeSpec angleSpec = repo.getAttrTypeSpec("Group", "angle").orElseThrow();
        assertEquals("number", angleSpec.getType());
    }

    @Test
    void verifyButtonAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule button = repo.getElementRule("Button").orElseThrow();
        assertEquals("control", button.getCategory());
        assertEquals(0, button.getRequiredAttrs().size());
        assertTrue(button.getOptionalAttrs().contains("name"));
        assertTrue(button.getOptionalAttrs().contains("x"));
        assertTrue(button.getOptionalAttrs().contains("y"));
        assertTrue(button.getOptionalAttrs().contains("width"));
        assertTrue(button.getOptionalAttrs().contains("height"));
        assertTrue(button.getOptionalAttrs().contains("visibility"));

        assertTrue(button.getAllowedChildren().contains("Trigger"));
        assertTrue(button.getAllowedParents().contains("Lockscreen"));
        assertTrue(button.getAllowedParents().contains("Widget"));
        assertTrue(button.getAllowedParents().contains("Group"));
    }

    @Test
    void verifyButtonScope() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule button = repo.getElementRule("Button").orElseThrow();
        assertTrue(button.getScope().get("Lockscreen"));
        assertFalse(button.getScope().get("Wallpaper"));
        assertFalse(button.getScope().get("LongTake"));
        assertTrue(button.getScope().get("Widget"));
        assertFalse(button.getScope().get("ChargingSkin"));
    }

    @Test
    void verifyButtonConstraints() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule button = repo.getElementRule("Button").orElseThrow();
        assertTrue(button.getConstraints().size() >= 2);

        RuleConstraint trigConstraint = button.getConstraints().stream()
                .filter(c -> "SEM-TRIG-002".equals(c.getRuleId()))
                .findFirst().orElseThrow();
        assertEquals("error", trigConstraint.getSeverity().name().toLowerCase());

        RuleConstraint scopeConstraint = button.getConstraints().stream()
                .filter(c -> "SEM-SCOPE-001".equals(c.getRuleId()))
                .findFirst().orElseThrow();
        assertEquals("error", scopeConstraint.getSeverity().name().toLowerCase());
    }

    @Test
    void verifySourceImageAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule srcImg = repo.getElementRule("SourceImage").orElseThrow();
        assertEquals("view", srcImg.getCategory());
        assertTrue(srcImg.getRequiredAttrs().contains("sourceName"));
        assertTrue(srcImg.getRequiredAttrs().contains("format"));
        assertTrue(srcImg.getRequiredAttrs().contains("to"));
        assertTrue(srcImg.getOptionalAttrs().contains("from"));
        assertTrue(srcImg.getOptionalAttrs().contains("space"));
        assertTrue(srcImg.getOptionalAttrs().contains("loop"));
        assertTrue(srcImg.getOptionalAttrs().contains("direction"));
        assertTrue(srcImg.getOptionalAttrs().contains("unlockTo"));
        assertFalse(srcImg.getOptionalAttrs().contains("category"));

        AttrTypeSpec fromSpec = repo.getAttrTypeSpec("SourceImage", "from").orElseThrow();
        assertTrue(fromSpec.isSupportsExpression());
        assertEquals("number", fromSpec.getExpressionKind());
        assertEquals("0", fromSpec.getDefaultValue());

        AttrTypeSpec spaceSpec = repo.getAttrTypeSpec("SourceImage", "space").orElseThrow();
        assertEquals("30", spaceSpec.getDefaultValue());

        AttrTypeSpec loopSpec = repo.getAttrTypeSpec("SourceImage", "loop").orElseThrow();
        assertEquals("true", loopSpec.getDefaultValue());

        AttrTypeSpec dirSpec = repo.getAttrTypeSpec("SourceImage", "direction").orElseThrow();
        assertEquals("1", dirSpec.getDefaultValue());

        AttrTypeSpec formatSpec = repo.getAttrTypeSpec("SourceImage", "format").orElseThrow();
        assertTrue(formatSpec.getEnumValues().contains("jpg"));
        assertTrue(formatSpec.getEnumValues().contains("png"));

        assertEquals(0, srcImg.getAllowedChildren().size());
        assertTrue(srcImg.getAllowedParents().contains("Lockscreen"));
        assertTrue(srcImg.getAllowedParents().contains("Group"));
    }

    @Test
    void verifySourceImageScope() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule srcImg = repo.getElementRule("SourceImage").orElseThrow();
        assertTrue(srcImg.getScope().get("Lockscreen"));
        assertFalse(srcImg.getScope().get("Wallpaper"));
        assertFalse(srcImg.getScope().get("LongTake"));
        assertFalse(srcImg.getScope().get("Widget"));
        assertFalse(srcImg.getScope().get("ChargingSkin"));
    }

    @Test
    void verifySourceImageConstraints() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule srcImg = repo.getElementRule("SourceImage").orElseThrow();
        assertTrue(srcImg.getConstraints().size() >= 2);

        RuleConstraint dirConstraint = srcImg.getConstraints().stream()
                .filter(c -> "SEM-SRCIMG-001".equals(c.getRuleId()))
                .findFirst().orElseThrow();
        assertEquals("error", dirConstraint.getSeverity().name().toLowerCase());

        RuleConstraint btnConstraint = srcImg.getConstraints().stream()
                .filter(c -> "SEM-SRCIMG-002".equals(c.getRuleId()))
                .findFirst().orElseThrow();
        assertEquals("warning", btnConstraint.getSeverity().name().toLowerCase());
    }

    @Test
    void verifyImageNumberAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule imgNum = repo.getElementRule("ImageNumber").orElseThrow();
        assertEquals("view", imgNum.getCategory());
        assertTrue(imgNum.getRequiredAttrs().contains("src"));
        assertTrue(imgNum.getRequiredAttrs().contains("number"));
        assertFalse(imgNum.getOptionalAttrs().contains("width"));
        assertFalse(imgNum.getOptionalAttrs().contains("height"));
        assertFalse(imgNum.getOptionalAttrs().contains("rotationX"));
        assertFalse(imgNum.getOptionalAttrs().contains("rotationY"));
        assertTrue(imgNum.getOptionalAttrs().contains("category"));

        AttrTypeSpec numberSpec = repo.getAttrTypeSpec("ImageNumber", "number").orElseThrow();
        assertTrue(numberSpec.isSupportsExpression());
        assertEquals("number", numberSpec.getExpressionKind());

        AttrTypeSpec srcSpec = repo.getAttrTypeSpec("ImageNumber", "src").orElseThrow();
        assertFalse(srcSpec.isSupportsExpression());

        assertEquals(0, imgNum.getAllowedChildren().size());
        assertTrue(imgNum.getAllowedParents().contains("Lockscreen"));
        assertTrue(imgNum.getAllowedParents().contains("Wallpaper"));
        assertTrue(imgNum.getAllowedParents().contains("Widget"));
        assertTrue(imgNum.getAllowedParents().contains("ChargingSkin"));
        assertTrue(imgNum.getAllowedParents().contains("Group"));
    }

    @Test
    void verifyImageSeriesAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule imgSeries = repo.getElementRule("ImageSeries").orElseThrow();
        assertEquals("view", imgSeries.getCategory());
        assertTrue(imgSeries.getRequiredAttrs().contains("src"));
        assertTrue(imgSeries.getRequiredAttrs().contains("string"));
        assertTrue(imgSeries.getRequiredAttrs().contains("mapList"));
        assertFalse(imgSeries.getOptionalAttrs().contains("width"));
        assertFalse(imgSeries.getOptionalAttrs().contains("height"));
        assertFalse(imgSeries.getOptionalAttrs().contains("rotationX"));
        assertFalse(imgSeries.getOptionalAttrs().contains("rotationY"));
        assertTrue(imgSeries.getOptionalAttrs().contains("space"));
        assertTrue(imgSeries.getOptionalAttrs().contains("category"));

        AttrTypeSpec stringSpec = repo.getAttrTypeSpec("ImageSeries", "string").orElseThrow();
        assertTrue(stringSpec.isSupportsExpression());
        assertEquals("string", stringSpec.getExpressionKind());

        AttrTypeSpec spaceSpec = repo.getAttrTypeSpec("ImageSeries", "space").orElseThrow();
        assertEquals("0", spaceSpec.getDefaultValue());

        AttrTypeSpec srcSpec = repo.getAttrTypeSpec("ImageSeries", "src").orElseThrow();
        assertFalse(srcSpec.isSupportsExpression());

        AttrTypeSpec mapListSpec = repo.getAttrTypeSpec("ImageSeries", "mapList").orElseThrow();
        assertFalse(mapListSpec.isSupportsExpression());

        assertEquals(0, imgSeries.getAllowedChildren().size());
    }

    @Test
    void verifyImageNumberAndImageSeriesScope() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule imgNum = repo.getElementRule("ImageNumber").orElseThrow();
        assertTrue(imgNum.getScope().get("Lockscreen"));
        assertTrue(imgNum.getScope().get("Wallpaper"));
        assertTrue(imgNum.getScope().get("LongTake"));
        assertTrue(imgNum.getScope().get("Widget"));
        assertTrue(imgNum.getScope().get("ChargingSkin"));

        DslElementRule imgSeries = repo.getElementRule("ImageSeries").orElseThrow();
        assertTrue(imgSeries.getScope().get("Lockscreen"));
        assertTrue(imgSeries.getScope().get("Wallpaper"));
        assertTrue(imgSeries.getScope().get("LongTake"));
        assertTrue(imgSeries.getScope().get("Widget"));
        assertTrue(imgSeries.getScope().get("ChargingSkin"));
    }
}
