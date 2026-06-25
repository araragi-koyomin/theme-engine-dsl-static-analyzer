package com.huawei.theme.analysis.core.rulelibrary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;

class Batch3LoadTest {

    private String getRulesDir() {
        String moduleDir = System.getProperty("user.dir");
        return moduleDir + "/src/main/resources/rules";
    }

    @Test
    void loadCommandElementsAndVerify() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        assertTrue(repo.getElementRule("Command").isPresent());
        assertTrue(repo.getElementRule("VariableCommand").isPresent());
        assertTrue(repo.getElementRule("VideoCommand").isPresent());
        assertTrue(repo.getElementRule("SoundCommand").isPresent());
        assertTrue(repo.getElementRule("Trigger").isPresent());
    }

    @Test
    void verifyCommandAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule cmd = repo.getElementRule("Command").orElseThrow();
        assertEquals("command", cmd.getCategory());
        assertEquals(2, cmd.getRequiredAttrs().size());
        assertTrue(cmd.getRequiredAttrs().contains("target"));
        assertTrue(cmd.getRequiredAttrs().contains("value"));
        assertTrue(cmd.getOptionalAttrs().contains("condition"));
        assertTrue(cmd.getOptionalAttrs().contains("delay"));
        assertTrue(cmd.getOptionalAttrs().contains("delayCondition"));
        assertEquals(0, cmd.getConstraints().size());
        assertTrue(cmd.getAllowedParents().contains("Trigger"));

        AttrTypeSpec conditionSpec = repo.getAttrTypeSpec("Command", "condition").orElseThrow();
        assertTrue(conditionSpec.isSupportsExpression());
        assertEquals("number", conditionSpec.getExpressionKind());

        AttrTypeSpec valueSpec = repo.getAttrTypeSpec("Command", "value").orElseThrow();
        assertTrue(valueSpec.getEnumValues().contains("true"));
        assertTrue(valueSpec.getEnumValues().contains("false"));
        assertTrue(valueSpec.getEnumValues().contains("toggle"));
        assertTrue(valueSpec.getEnumValues().contains("play"));
        assertTrue(valueSpec.getEnumValues().contains("stop"));
        assertEquals(5, valueSpec.getEnumValues().size());

        AttrTypeSpec delayCondSpec = repo.getAttrTypeSpec("Command", "delayCondition").orElseThrow();
        assertEquals("1", delayCondSpec.getDefaultValue());

        AttrTypeSpec targetSpec = repo.getAttrTypeSpec("Command", "target").orElseThrow();
        assertEquals("string", targetSpec.getType());
        assertTrue(!targetSpec.isSupportsExpression());
    }

    @Test
    void verifyVariableCommandAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule vCmd = repo.getElementRule("VariableCommand").orElseThrow();
        assertEquals("command", vCmd.getCategory());
        assertEquals(2, vCmd.getRequiredAttrs().size());
        assertTrue(vCmd.getRequiredAttrs().contains("name"));
        assertTrue(vCmd.getRequiredAttrs().contains("expression"));
        assertTrue(vCmd.getOptionalAttrs().contains("type"));
        assertTrue(vCmd.getOptionalAttrs().contains("condition"));
        assertTrue(vCmd.getOptionalAttrs().contains("delay"));
        assertTrue(vCmd.getOptionalAttrs().contains("delayCondition"));

        AttrTypeSpec typeSpec = repo.getAttrTypeSpec("VariableCommand", "type").orElseThrow();
        assertEquals("number", typeSpec.getDefaultValue());
        assertTrue(typeSpec.getEnumValues().contains("number"));
        assertTrue(typeSpec.getEnumValues().contains("string"));

        AttrTypeSpec exprSpec = repo.getAttrTypeSpec("VariableCommand", "expression").orElseThrow();
        assertTrue(exprSpec.isSupportsExpression());
        assertEquals("auto", exprSpec.getExpressionKind());
    }

    @Test
    void verifyVariableCommandPersistConstraint() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule vCmd = repo.getElementRule("VariableCommand").orElseThrow();
        assertTrue(vCmd.getConstraints().size() >= 1);

        RuleConstraint persistConstraint = vCmd.getConstraints().stream()
                .filter(c -> "SEM-PERSIST-002".equals(c.getRuleId()))
                .findFirst().orElseThrow();
        assertEquals("error", persistConstraint.getSeverity().name().toLowerCase());
    }

    @Test
    void verifyVideoCommandAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule vidCmd = repo.getElementRule("VideoCommand").orElseThrow();
        assertEquals("command", vidCmd.getCategory());
        assertEquals(1, vidCmd.getRequiredAttrs().size());
        assertTrue(vidCmd.getRequiredAttrs().contains("name"));
        assertTrue(vidCmd.getOptionalAttrs().contains("src"));
        assertTrue(vidCmd.getOptionalAttrs().contains("play"));
        assertTrue(vidCmd.getOptionalAttrs().contains("sound"));
        assertTrue(vidCmd.getOptionalAttrs().contains("seekTime"));

        AttrTypeSpec playSpec = repo.getAttrTypeSpec("VideoCommand", "play").orElseThrow();
        assertTrue(playSpec.isSupportsExpression());
        assertEquals("number", playSpec.getExpressionKind());
    }

    @Test
    void verifyVideoCommandPlaySoundConstraint() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule vidCmd = repo.getElementRule("VideoCommand").orElseThrow();
        assertTrue(vidCmd.getConstraints().size() >= 1);

        RuleConstraint mutexConstraint = vidCmd.getConstraints().stream()
                .filter(c -> "SEM-CMD-001".equals(c.getRuleId()))
                .findFirst().orElseThrow();
        assertEquals("error", mutexConstraint.getSeverity().name().toLowerCase());
    }

    @Test
    void verifySoundCommandAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule sndCmd = repo.getElementRule("SoundCommand").orElseThrow();
        assertEquals("command", sndCmd.getCategory());
        assertEquals(2, sndCmd.getRequiredAttrs().size());
        assertTrue(sndCmd.getRequiredAttrs().contains("sound"));
        assertTrue(sndCmd.getRequiredAttrs().contains("volume"));
        assertTrue(sndCmd.getOptionalAttrs().contains("loop"));
        assertTrue(sndCmd.getOptionalAttrs().contains("keepCur"));
        assertTrue(sndCmd.getOptionalAttrs().contains("play"));

        AttrTypeSpec playSpec = repo.getAttrTypeSpec("SoundCommand", "play").orElseThrow();
        assertTrue(playSpec.isSupportsExpression());
        assertEquals("1", playSpec.getDefaultValue());

        AttrTypeSpec loopSpec = repo.getAttrTypeSpec("SoundCommand", "loop").orElseThrow();
        assertEquals("false", loopSpec.getDefaultValue());
    }

    @Test
    void verifyTriggerAttributes() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule trigger = repo.getElementRule("Trigger").orElseThrow();
        assertEquals("trigger", trigger.getCategory());
        assertEquals(1, trigger.getRequiredAttrs().size());
        assertTrue(trigger.getRequiredAttrs().contains("action"));

        AttrTypeSpec actionSpec = repo.getAttrTypeSpec("Trigger", "action").orElseThrow();
        assertEquals("string", actionSpec.getType());
        assertTrue(actionSpec.getEnumValues().contains("down"));
        assertTrue(actionSpec.getEnumValues().contains("up"));
        assertTrue(actionSpec.getEnumValues().contains("double"));
        assertTrue(actionSpec.getEnumValues().contains("click"));
        assertTrue(actionSpec.getEnumValues().contains("long"));
        assertTrue(actionSpec.getEnumValues().contains("resume"));
        assertTrue(actionSpec.getEnumValues().contains("pause"));
        assertEquals(10, actionSpec.getEnumValues().size());
    }

    @Test
    void verifyTriggerAllowedParentsAndChildren() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule trigger = repo.getElementRule("Trigger").orElseThrow();
        assertTrue(trigger.getAllowedParents().contains("Button"));
        assertTrue(trigger.getAllowedParents().contains("Var"));
        assertTrue(trigger.getAllowedChildren().contains("Command"));
        assertTrue(trigger.getAllowedChildren().contains("VariableCommand"));
        assertTrue(trigger.getAllowedChildren().contains("VideoCommand"));
        assertTrue(trigger.getAllowedChildren().contains("SoundCommand"));
    }

    @Test
    void verifyTriggerConstraint() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule trigger = repo.getElementRule("Trigger").orElseThrow();
        assertTrue(trigger.getConstraints().size() >= 1);

        RuleConstraint actionConstraint = trigger.getConstraints().stream()
                .filter(c -> "SEM-TRIG-001".equals(c.getRuleId()))
                .findFirst().orElseThrow();
        assertEquals("error", actionConstraint.getSeverity().name().toLowerCase());
    }

    @Test
    void verifyCommandScopeMatrix() {
        JsonRuleLoader loader = new JsonRuleLoader();
        RuleRepository repo = loader.loadFromDirectory(getRulesDir());

        DslElementRule cmd = repo.getElementRule("Command").orElseThrow();
        assertTrue(cmd.getScope().get("Lockscreen"));
        assertTrue(cmd.getScope().get("Wallpaper"));
        assertTrue(!cmd.getScope().get("LongTake"));
        assertTrue(cmd.getScope().get("Widget"));
        assertTrue(cmd.getScope().get("ChargingSkin"));

        DslElementRule trigger = repo.getElementRule("Trigger").orElseThrow();
        assertTrue(trigger.getScope().get("Lockscreen"));
    }
}
