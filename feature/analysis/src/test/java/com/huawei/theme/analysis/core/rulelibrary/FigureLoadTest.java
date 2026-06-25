package com.huawei.theme.analysis.core.rulelibrary;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FigureLoadTest {

    private String getRulesDir() {
        String moduleDir = System.getProperty("user.dir");
        return moduleDir + "/src/main/resources/rules";
    }

    @Test
    void loadFigureElementsAndVerify() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        assertTrue(repo.getElementRule("Arc").isPresent());
        assertTrue(repo.getElementRule("Circle").isPresent());
        assertTrue(repo.getElementRule("Ellipse").isPresent());
        assertTrue(repo.getElementRule("Line").isPresent());
        assertTrue(repo.getElementRule("Rectangle").isPresent());
        assertTrue(repo.getElementRule("PathUtil").isPresent());
    }

    @Test
    void verifyFigureScope() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule arc = repo.getElementRule("Arc").orElseThrow();
        assertTrue(arc.getScope().get("Lockscreen"));
        assertTrue(arc.getScope().get("Wallpaper"));
        assertTrue(arc.getScope().get("LongTake"));
        assertTrue(arc.getScope().get("Widget"));
        assertTrue(arc.getScope().get("ChargingSkin"));

        DslElementRule pathUtil = repo.getElementRule("PathUtil").orElseThrow();
        assertTrue(pathUtil.getScope().get("Lockscreen"));
        assertTrue(pathUtil.getScope().get("Wallpaper"));
        assertTrue(pathUtil.getScope().get("Widget"));
        assertTrue(pathUtil.getScope().get("ChargingSkin"));
    }

    @Test
    void verifyFigureNoNameAlpha() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule arc = repo.getElementRule("Arc").orElseThrow();
        assertFalse(arc.getOptionalAttrs().contains("name"));
        assertFalse(arc.getOptionalAttrs().contains("alpha"));

        DslElementRule circle = repo.getElementRule("Circle").orElseThrow();
        assertFalse(circle.getOptionalAttrs().contains("name"));
        assertFalse(circle.getOptionalAttrs().contains("alpha"));

        DslElementRule ellipse = repo.getElementRule("Ellipse").orElseThrow();
        assertFalse(ellipse.getOptionalAttrs().contains("name"));
        assertFalse(ellipse.getOptionalAttrs().contains("alpha"));

        DslElementRule rectangle = repo.getElementRule("Rectangle").orElseThrow();
        assertFalse(rectangle.getOptionalAttrs().contains("name"));
        assertFalse(rectangle.getOptionalAttrs().contains("alpha"));
    }

    @Test
    void verifyLineNoNameAlphaWidthHeight() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule line = repo.getElementRule("Line").orElseThrow();
        assertFalse(line.getOptionalAttrs().contains("name"));
        assertFalse(line.getOptionalAttrs().contains("alpha"));
        assertFalse(line.getOptionalAttrs().contains("width"));
        assertFalse(line.getOptionalAttrs().contains("height"));
    }

    @Test
    void verifyArcRequiredAttrs() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule arc = repo.getElementRule("Arc").orElseThrow();
        assertTrue(arc.getRequiredAttrs().contains("width"));
        assertTrue(arc.getRequiredAttrs().contains("height"));
        assertTrue(arc.getRequiredAttrs().contains("sweepAngle"));
        assertTrue(arc.getOptionalAttrs().contains("startAngle"));
        assertTrue(arc.getOptionalAttrs().contains("closure"));

        AttrTypeSpec sweepAngleSpec = repo.getAttrTypeSpec("Arc", "sweepAngle").orElseThrow();
        assertTrue(sweepAngleSpec.isSupportsExpression());
        assertEquals("number", sweepAngleSpec.getExpressionKind());

        AttrTypeSpec closureSpec = repo.getAttrTypeSpec("Arc", "closure").orElseThrow();
        assertTrue(closureSpec.getEnumValues().contains("true"));
        assertTrue(closureSpec.getEnumValues().contains("false"));
        assertEquals("false", closureSpec.getDefaultValue());
    }

    @Test
    void verifyCircleRequiredAttr() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule circle = repo.getElementRule("Circle").orElseThrow();
        assertTrue(circle.getRequiredAttrs().contains("r"));
        assertTrue(circle.getOptionalAttrs().contains("width"));
        assertTrue(circle.getOptionalAttrs().contains("height"));

        AttrTypeSpec rSpec = repo.getAttrTypeSpec("Circle", "r").orElseThrow();
        assertTrue(rSpec.isSupportsExpression());
        assertEquals("number", rSpec.getExpressionKind());
    }

    @Test
    void verifyRectangleRequiredAttrs() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule rect = repo.getElementRule("Rectangle").orElseThrow();
        assertTrue(rect.getRequiredAttrs().contains("width"));
        assertTrue(rect.getRequiredAttrs().contains("height"));
        assertTrue(rect.getOptionalAttrs().contains("cornerRadius"));

        AttrTypeSpec cornerRadiusSpec = repo.getAttrTypeSpec("Rectangle", "cornerRadius").orElseThrow();
        assertEquals("0", cornerRadiusSpec.getDefaultValue());
    }

    @Test
    void verifyLineRequiredAttrs() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule line = repo.getElementRule("Line").orElseThrow();
        assertTrue(line.getRequiredAttrs().contains("x1"));
        assertTrue(line.getRequiredAttrs().contains("y1"));

        AttrTypeSpec x1Spec = repo.getAttrTypeSpec("Line", "x1").orElseThrow();
        assertTrue(x1Spec.isSupportsExpression());
        assertEquals("number", x1Spec.getExpressionKind());
    }

    @Test
    void verifyFigureCommonAttrs() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule rect = repo.getElementRule("Rectangle").orElseThrow();

        AttrTypeSpec strokeColorSpec = repo.getAttrTypeSpec("Rectangle", "strokeColor").orElseThrow();
        assertTrue(strokeColorSpec.isSupportsExpression());
        assertEquals("string", strokeColorSpec.getExpressionKind());
        assertEquals("#000000", strokeColorSpec.getDefaultValue());

        AttrTypeSpec weightSpec = repo.getAttrTypeSpec("Rectangle", "weight").orElseThrow();
        assertTrue(weightSpec.isSupportsExpression());
        assertEquals("number", weightSpec.getExpressionKind());
        assertEquals("0", weightSpec.getDefaultValue());

        AttrTypeSpec capSpec = repo.getAttrTypeSpec("Rectangle", "cap").orElseThrow();
        assertTrue(capSpec.getEnumValues().contains("semicircle"));
        assertTrue(capSpec.getEnumValues().contains("square"));
        assertFalse(capSpec.isSupportsExpression());
    }

    @Test
    void verifyHybridModeEnumValues() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        AttrTypeSpec hybridSpec = repo.getAttrTypeSpec("Rectangle", "hybridMode").orElseThrow();
        assertTrue(hybridSpec.getEnumValues().contains("clear"));
        assertTrue(hybridSpec.getEnumValues().contains("0"));
        assertTrue(hybridSpec.getEnumValues().contains("oriOver"));
        assertTrue(hybridSpec.getEnumValues().contains("3"));
        assertTrue(hybridSpec.getEnumValues().contains("xor"));
        assertTrue(hybridSpec.getEnumValues().contains("11"));
        assertEquals(24, hybridSpec.getEnumValues().size());
    }

    @Test
    void verifyPathUtilNoCommonViewAttrs() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule pathUtil = repo.getElementRule("PathUtil").orElseThrow();
        assertTrue(pathUtil.getRequiredAttrs().contains("name"));
        assertTrue(pathUtil.getRequiredAttrs().contains("path"));
        assertFalse(pathUtil.getOptionalAttrs().contains("x"));
        assertFalse(pathUtil.getOptionalAttrs().contains("y"));
        assertFalse(pathUtil.getOptionalAttrs().contains("width"));
        assertEquals("view", pathUtil.getCategory());
    }

    @Test
    void verifyFigureAliasResolution() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        AttrTypeSpec widthSpec = repo.getAttrTypeSpec("Rectangle", "w").orElseThrow();
        assertEquals("number", widthSpec.getType());
        assertTrue(widthSpec.getAliases().contains("w"));

        AttrTypeSpec heightSpec = repo.getAttrTypeSpec("Rectangle", "h").orElseThrow();
        assertEquals("number", heightSpec.getType());
        assertTrue(heightSpec.getAliases().contains("h"));
    }

    @Test
    void verifyFigureAllowedParents() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule arc = repo.getElementRule("Arc").orElseThrow();
        assertTrue(arc.getAllowedParents().contains("Lockscreen"));
        assertTrue(arc.getAllowedParents().contains("Wallpaper"));
        assertTrue(arc.getAllowedParents().contains("Widget"));
        assertTrue(arc.getAllowedParents().contains("ChargingSkin"));
        assertTrue(arc.getAllowedParents().contains("Group"));
    }

    @Test
    void verifyChargingSkinIncludesPathUtil() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule chargingSkin = repo.getElementRule("ChargingSkin").orElseThrow();
        assertTrue(repo.getAllowedChildren("ChargingSkin").contains("PathUtil"));
    }
}
