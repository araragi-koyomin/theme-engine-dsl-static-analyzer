package com.huawei.theme.analysis.core.rulelibrary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;

class Batch5LoadTest {

    private String getRulesDir() {
        String moduleDir = System.getProperty("user.dir");
        return moduleDir + "/src/main/resources/rules";
    }

    @Test
    void loadAnimationElementsAndVerify() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        assertTrue(repo.getElementRule("AlphaAnimation").isPresent());
        assertTrue(repo.getElementRule("PositionAnimation").isPresent());
        assertTrue(repo.getElementRule("RotationAnimation").isPresent());
        assertTrue(repo.getElementRule("SizeAnimation").isPresent());
        assertTrue(repo.getElementRule("SourcesAnimation").isPresent());
        assertTrue(repo.getElementRule("VariableAnimation").isPresent());
        assertTrue(repo.getElementRule("Alpha").isPresent());
        assertTrue(repo.getElementRule("Position").isPresent());
        assertTrue(repo.getElementRule("Rotation").isPresent());
        assertTrue(repo.getElementRule("Size").isPresent());
        assertTrue(repo.getElementRule("Source").isPresent());
        assertTrue(repo.getElementRule("AniFrame").isPresent());
    }

    @Test
    void verifyAlphaAnimationAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule anim = repo.getElementRule("AlphaAnimation").orElseThrow();
        assertEquals("animation", anim.getCategory());
        assertEquals(0, anim.getRequiredAttrs().size());
        assertTrue(anim.getOptionalAttrs().contains("delay"));
        assertTrue(anim.getOptionalAttrs().contains("repeat"));
        assertTrue(repo.getAllowedChildren("AlphaAnimation").contains("Alpha"));
        assertTrue(anim.getAllowedParents().contains("Image"));
        assertTrue(anim.getAllowedParents().contains("Group"));

        AttrTypeSpec delaySpec = repo.getAttrTypeSpec("AlphaAnimation", "delay").orElseThrow();
        assertEquals("number", delaySpec.getType());
        assertFalse(delaySpec.isSupportsExpression());
    }

    @Test
    void verifyAnimationScope() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule alpha = repo.getElementRule("AlphaAnimation").orElseThrow();
        assertTrue(alpha.getScope().get("Lockscreen"));
        assertTrue(alpha.getScope().get("Wallpaper"));
        assertTrue(alpha.getScope().get("LongTake"));
        assertTrue(alpha.getScope().get("Widget"));
        assertFalse(alpha.getScope().get("ChargingSkin"));

        DslElementRule varAnim = repo.getElementRule("VariableAnimation").orElseThrow();
        assertTrue(varAnim.getScope().get("Lockscreen"));
        assertFalse(varAnim.getScope().get("ChargingSkin"));
    }

    @Test
    void verifySourcesAnimationParentRestriction() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule srcAnim = repo.getElementRule("SourcesAnimation").orElseThrow();
        assertEquals(1, srcAnim.getAllowedParents().size());
        assertTrue(srcAnim.getAllowedParents().contains("Image"));
        assertTrue(repo.getAllowedChildren("SourcesAnimation").contains("Source"));
    }

    @Test
    void verifyVariableAnimationParentRestriction() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule varAnim = repo.getElementRule("VariableAnimation").orElseThrow();
        assertEquals(1, varAnim.getAllowedParents().size());
        assertTrue(varAnim.getAllowedParents().contains("Var"));
        assertTrue(repo.getAllowedChildren("VariableAnimation").contains("AniFrame"));
    }

    @Test
    void verifyAlphaFrameAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule alpha = repo.getElementRule("Alpha").orElseThrow();
        assertEquals("animation_frame", alpha.getCategory());
        assertTrue(alpha.getRequiredAttrs().contains("a"));
        assertTrue(alpha.getRequiredAttrs().contains("time"));
        assertTrue(alpha.getOptionalAttrs().contains("varSpeedFlag"));
        assertEquals(1, alpha.getAllowedParents().size());
        assertTrue(alpha.getAllowedParents().contains("AlphaAnimation"));

        AttrTypeSpec aVar = repo.getAttrTypeSpec("Alpha", "a").orElseThrow();
        assertEquals("number", aVar.getType());

        AttrTypeSpec varSpeed = repo.getAttrTypeSpec("Alpha", "varSpeedFlag").orElseThrow();
        assertEquals("string", varSpeed.getType());
        assertTrue(varSpeed.getEnumValues().contains("SineFun_In"));
        assertTrue(varSpeed.getEnumValues().contains("BounceFun_InOut"));
        assertEquals(30, varSpeed.getEnumValues().size());
    }

    @Test
    void verifyPositionFrameAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule pos = repo.getElementRule("Position").orElseThrow();
        assertEquals("animation_frame", pos.getCategory());
        assertTrue(pos.getRequiredAttrs().contains("x"));
        assertTrue(pos.getRequiredAttrs().contains("y"));
        assertTrue(pos.getRequiredAttrs().contains("time"));
        assertTrue(pos.getOptionalAttrs().contains("varSpeedFlag"));
        assertTrue(pos.getAllowedParents().contains("PositionAnimation"));
    }

    @Test
    void verifyRotationFrameAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule rot = repo.getElementRule("Rotation").orElseThrow();
        assertEquals("animation_frame", rot.getCategory());
        assertTrue(rot.getRequiredAttrs().contains("angle"));
        assertTrue(rot.getRequiredAttrs().contains("time"));
        assertTrue(rot.getOptionalAttrs().contains("varSpeedFlag"));
        assertTrue(rot.getAllowedParents().contains("RotationAnimation"));

        AttrTypeSpec angleSpec = repo.getAttrTypeSpec("Rotation", "angle").orElseThrow();
        assertEquals("number", angleSpec.getType());
    }

    @Test
    void verifySizeFrameAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule size = repo.getElementRule("Size").orElseThrow();
        assertEquals("animation_frame", size.getCategory());
        assertTrue(size.getRequiredAttrs().contains("w"));
        assertTrue(size.getRequiredAttrs().contains("h"));
        assertTrue(size.getRequiredAttrs().contains("time"));
        assertTrue(size.getOptionalAttrs().contains("varSpeedFlag"));
        assertTrue(size.getAllowedParents().contains("SizeAnimation"));
    }

    @Test
    void verifySourceFrameNoVarSpeedFlag() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule src = repo.getElementRule("Source").orElseThrow();
        assertEquals("animation_frame", src.getCategory());
        assertTrue(src.getRequiredAttrs().contains("src"));
        assertTrue(src.getRequiredAttrs().contains("time"));
        assertFalse(src.getOptionalAttrs().contains("varSpeedFlag"));
        assertTrue(src.getAllowedParents().contains("SourcesAnimation"));

        AttrTypeSpec srcAttr = repo.getAttrTypeSpec("Source", "src").orElseThrow();
        assertEquals("string", srcAttr.getType());
        assertFalse(srcAttr.isSupportsExpression());
    }

    @Test
    void verifyAniFrameAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule frame = repo.getElementRule("AniFrame").orElseThrow();
        assertEquals("animation_frame", frame.getCategory());
        assertTrue(frame.getRequiredAttrs().contains("value"));
        assertTrue(frame.getRequiredAttrs().contains("time"));
        assertTrue(frame.getOptionalAttrs().contains("varSpeedFlag"));
        assertTrue(frame.getAllowedParents().contains("VariableAnimation"));

        AttrTypeSpec valueSpec = repo.getAttrTypeSpec("AniFrame", "value").orElseThrow();
        assertTrue(valueSpec.isSupportsExpression());
        assertEquals("number", valueSpec.getExpressionKind());
    }

    @Test
    void verifySourcesAnimationInRootAllowedChildren() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        assertTrue(repo.getAllowedChildren("Image").contains("SourcesAnimation"));
    }
}
