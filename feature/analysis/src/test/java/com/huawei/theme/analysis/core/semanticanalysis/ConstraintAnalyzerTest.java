package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintAnalyzerTest {

    private final ConstraintAnalyzer analyzer = new ConstraintAnalyzer();

    private static DslElementNode element(String tagName, Map<String, String> attrs) {
        DslElementNode node = new DslElementNode();
        node.setTagName(tagName);
        node.setLine(10);
        node.setColumn(5);
        List<DslAttributeNode> attrNodes = new java.util.ArrayList<>();
        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            DslAttributeNode attrNode = new DslAttributeNode();
            attrNode.setName(entry.getKey());
            DslAttributeValueNode valueNode = new DslAttributeValueNode();
            valueNode.setRawValue(entry.getValue());
            attrNode.setValue(valueNode);
            attrNodes.add(attrNode);
        }
        node.setAttributes(attrNodes);
        node.setChildElements(Collections.emptyList());
        return node;
    }

    private static DslContext context(RuleRepository ruleRepo) {
        return new DslContext(ruleRepo, null, "test.xml");
    }

    private static class StubRuleRepository implements RuleRepository {

        private final Map<String, DslElementRule> elementRules;
        private final Map<String, RuleSource> ruleSources;

        StubRuleRepository(Map<String, DslElementRule> elementRules, Map<String, RuleSource> ruleSources) {
            this.elementRules = elementRules;
            this.ruleSources = ruleSources;
        }

        @Override
        public Optional<DslElementRule> getElementRule(String elementName) {
            return Optional.ofNullable(elementRules.get(elementName));
        }

        @Override
        public List<DslElementRule> getAllElementRules() {
            return List.copyOf(elementRules.values());
        }

        @Override
        public List<String> getAllElementNames() {
            return List.copyOf(elementRules.keySet());
        }

        @Override
        public List<String> getRootElementNames() {
            return Collections.emptyList();
        }

        @Override
        public Optional<AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName) {
            return Optional.empty();
        }

        @Override
        public Optional<String> resolveAttrAlias(String elementName, String attrName) {
            return Optional.empty();
        }

        @Override
        public Set<String> getCanonicalAttrNames(String elementName) {
            return Collections.emptySet();
        }

        @Override
        public List<String> getAllowedParents(String elementName) {
            return Collections.emptyList();
        }

        @Override
        public List<String> getAllowedChildren(String elementName) {
            return Collections.emptyList();
        }

        @Override
        public List<RuleConstraint> getConstraints(String elementName) {
            return getElementRule(elementName)
                    .map(DslElementRule::getConstraints)
                    .orElse(Collections.emptyList());
        }

        @Override
        public Optional<DslGlobalVar> getGlobalVar(String varName) {
            return Optional.empty();
        }

        @Override
        public List<DslGlobalVar> getAllGlobalVars() {
            return Collections.emptyList();
        }

        @Override
        public Optional<RuleSource> getRuleSource(String ruleId) {
            return Optional.ofNullable(ruleSources.get(ruleId));
        }
    }

    private static RuleConstraint cmd001Constraint() {
        return RuleConstraint.builder()
                .ruleId("SEM-CMD-001")
                .condition("element.attrs['play'] != null AND element.attrs['sound'] != null")
                .message("VideoCommand中play和sound互斥，不能同时存在")
                .severity(DiagnosticSeverity.ERROR)
                .suggestedFixes(List.of("移除play属性", "移除sound属性"))
                .build();
    }

    private static RuleConstraint persist002Constraint() {
        return RuleConstraint.builder()
                .ruleId("SEM-PERSIST-002")
                .condition("element.attrs['persist'] != null")
                .message("VariableCommand不支持persist属性")
                .severity(DiagnosticSeverity.ERROR)
                .suggestedFixes(List.of("移除persist属性"))
                .build();
    }

    private static DslElementRule videoCommandRule(RuleConstraint... constraints) {
        return DslElementRule.builder()
                .elementName("VideoCommand")
                .category("command")
                .scope(Map.of("Lockscreen", true, "Widget", false))
                .deviceSupport(Map.of("barPhone", true))
                .constraints(List.of(constraints))
                .build();
    }

    private static DslElementRule variableCommandRule(RuleConstraint... constraints) {
        return DslElementRule.builder()
                .elementName("VariableCommand")
                .category("command")
                .constraints(List.of(constraints))
                .build();
    }

    private static RuleSource cmd001Source() {
        return RuleSource.builder()
                .ruleId("SEM-CMD-001")
                .category("SEM")
                .description("VideoCommand play and sound are mutually exclusive")
                .docUrl("https://developer.huawei.com/consumer/cn/doc/harmonyos/sem-cmd-001")
                .build();
    }

    @Test
    void videoCommandPlayAndSoundCoexistProducesSEM_CMD_001() {
        DslElementNode node = element("VideoCommand", Map.of("play", "1", "sound", "0.5"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("VideoCommand", videoCommandRule(cmd001Constraint())),
                Map.of("SEM-CMD-001", cmd001Source())
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals(DiagnosticSeverity.ERROR, diag.getSeverity());
        assertEquals("SEM-CMD-001", diag.getRuleId());
        assertEquals("VideoCommand中play和sound互斥，不能同时存在", diag.getMessage());
        assertEquals("test.xml", diag.getFilePath());
        assertEquals(10, diag.getLine());
        assertEquals(5, diag.getColumn());
        assertEquals(2, diag.getSuggestedFixes().size());
        assertEquals("移除play属性", diag.getSuggestedFixes().get(0));
        assertEquals("移除sound属性", diag.getSuggestedFixes().get(1));
        assertEquals("https://developer.huawei.com/consumer/cn/doc/harmonyos/sem-cmd-001", diag.getRuleDocUrl());
    }

    @Test
    void videoCommandOnlyPlayNoViolation() {
        DslElementNode node = element("VideoCommand", Map.of("play", "1"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("VideoCommand", videoCommandRule(cmd001Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void videoCommandOnlySoundNoViolation() {
        DslElementNode node = element("VideoCommand", Map.of("sound", "0.5"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("VideoCommand", videoCommandRule(cmd001Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void elementWithNoConstraintsReturnsEmpty() {
        DslElementNode node = element("Image", Map.of("src", "bg.png"));
        DslElementRule imageRule = DslElementRule.builder()
                .elementName("Image")
                .category("view")
                .constraints(Collections.emptyList())
                .build();
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Image", imageRule),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void elementNotInRepositoryReturnsEmpty() {
        DslElementNode node = element("UnknownElement", Map.of("attr", "val"));
        RuleRepository ruleRepo = new StubRuleRepository(Collections.emptyMap(), Collections.emptyMap());

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void nonElementNodeReturnsEmpty() {
        DslFileNode fileNode = new DslFileNode();
        fileNode.setFilePath("test.xml");
        RuleRepository ruleRepo = new StubRuleRepository(Collections.emptyMap(), Collections.emptyMap());

        List<Diagnostic> diagnostics = analyzer.analyze(fileNode, context(ruleRepo));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void variableCommandWithPersistProducesSEM_PERSIST_002() {
        DslElementNode node = element("VariableCommand", Map.of("persist", "true", "name", "myVar"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("VariableCommand", variableCommandRule(persist002Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals("SEM-PERSIST-002", diag.getRuleId());
        assertEquals(DiagnosticSeverity.ERROR, diag.getSeverity());
    }

    @Test
    void variableCommandWithoutPersistNoViolation() {
        DslElementNode node = element("VariableCommand", Map.of("name", "myVar"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("VariableCommand", variableCommandRule(persist002Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void diagnosticWithoutRuleSourceHasNullDocUrl() {
        DslElementNode node = element("VideoCommand", Map.of("play", "1", "sound", "0.5"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("VideoCommand", videoCommandRule(cmd001Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertEquals(1, diagnostics.size());
        assertEquals(null, diagnostics.get(0).getRuleDocUrl());
    }

    @Test
    void evaluationContextIncludesScopeAndDeviceSupport() {
        DslElementNode node = element("VideoCommand", Map.of("play", "1", "sound", "0.5"));
        RuleConstraint scopeConstraint = RuleConstraint.builder()
                .ruleId("SEM-SCOPE-TEST")
                .condition("element.attrs['play'] != null")
                .severity(DiagnosticSeverity.WARNING)
                .message("test scope")
                .build();
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("VideoCommand", videoCommandRule(cmd001Constraint(), scopeConstraint)),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertEquals(2, diagnostics.size());
    }

    @Test
    void elementWithEmptyAttributesMapStillBuildsContext() {
        DslElementNode node = element("VideoCommand", Collections.emptyMap());
        node.setAttributes(Collections.emptyList());
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("VideoCommand", videoCommandRule(cmd001Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertTrue(diagnostics.isEmpty());
    }

    // --- SEM-VAR-003: Var values + size coexist (warning) ---

    private static RuleConstraint var003Constraint() {
        return RuleConstraint.builder()
                .ruleId("SEM-VAR-003")
                .condition("element.attrs['values'] != null AND element.attrs['size'] != null")
                .message("Var的values与size属性同时存在，优先取size")
                .severity(DiagnosticSeverity.WARNING)
                .suggestedFixes(List.of("移除values属性", "移除size属性"))
                .build();
    }

    private static DslElementRule varRule(RuleConstraint... constraints) {
        return DslElementRule.builder()
                .elementName("Var")
                .category("variable")
                .scope(Map.of("Lockscreen", true, "Wallpaper", true, "Widget", true, "ChargingSkin", true))
                .deviceSupport(Map.of("barPhone", true, "foldable", true, "tablet", true))
                .constraints(List.of(constraints))
                .build();
    }

    @Test
    void varValuesAndSizeCoexistProducesSEM_VAR_003() {
        DslElementNode node = element("Var", Map.of("name", "count", "values", "1,2,3", "size", "3"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Var", varRule(var003Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals("SEM-VAR-003", diag.getRuleId());
        assertEquals(DiagnosticSeverity.WARNING, diag.getSeverity());
        assertEquals("Var的values与size属性同时存在，优先取size", diag.getMessage());
        assertEquals(2, diag.getSuggestedFixes().size());
    }

    @Test
    void varOnlyValuesNoViolation() {
        DslElementNode node = element("Var", Map.of("name", "count", "values", "1,2,3"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Var", varRule(var003Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertTrue(diagnostics.isEmpty());
    }

    // --- SEM-ATTR-005: Image isBackground + scaleType mismatch (error) ---

    private static RuleConstraint attr005Constraint() {
        return RuleConstraint.builder()
                .ruleId("SEM-ATTR-005")
                .condition("element.attrs['isBackground'] == 'true' AND element.attrs['scaleType'] != 'center_crop'")
                .message("isBackground=true时必须配合scaleType=center_crop")
                .severity(DiagnosticSeverity.ERROR)
                .suggestedFixes(List.of("设置scaleType=center_crop"))
                .build();
    }

    private static DslElementRule imageRule(RuleConstraint... constraints) {
        return DslElementRule.builder()
                .elementName("Image")
                .category("view")
                .scope(Map.of("Lockscreen", true, "Wallpaper", true, "Widget", true, "ChargingSkin", true))
                .deviceSupport(Map.of("barPhone", true, "foldable", true, "tablet", true))
                .constraints(List.of(constraints))
                .build();
    }

    @Test
    void imageIsBackgroundWithWrongScaleTypeProducesSEM_ATTR_005() {
        DslElementNode node = element("Image", Map.of("src", "bg.png", "isBackground", "true", "scaleType", "fit_center"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Image", imageRule(attr005Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals("SEM-ATTR-005", diag.getRuleId());
        assertEquals(DiagnosticSeverity.ERROR, diag.getSeverity());
        assertEquals("isBackground=true时必须配合scaleType=center_crop", diag.getMessage());
    }

    @Test
    void imageIsBackgroundWithCenterCropNoViolation() {
        DslElementNode node = element("Image", Map.of("src", "bg.png", "isBackground", "true", "scaleType", "center_crop"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Image", imageRule(attr005Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void imageIsBackgroundFalseNoViolation() {
        DslElementNode node = element("Image", Map.of("src", "bg.png", "isBackground", "false", "scaleType", "fit_center"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Image", imageRule(attr005Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertTrue(diagnostics.isEmpty());
    }

    // --- SEM-ATTR-003: Text category NOT IN valid enum (error) ---

    private static RuleConstraint attr003Constraint() {
        return RuleConstraint.builder()
                .ruleId("SEM-ATTR-003")
                .condition("element.attrs['category'] != null AND element.attrs['category'] NOT IN ['Normal', 'Charging', 'BatteryLow', 'BatteryFull']")
                .message("category枚举值不合法，合法值为: Normal, Charging, BatteryLow, BatteryFull")
                .severity(DiagnosticSeverity.ERROR)
                .suggestedFixes(List.of("修改category为合法枚举值"))
                .build();
    }

    private static DslElementRule textRule(RuleConstraint... constraints) {
        return DslElementRule.builder()
                .elementName("Text")
                .category("view")
                .scope(Map.of("Lockscreen", true, "Wallpaper", true, "Widget", true, "ChargingSkin", true))
                .deviceSupport(Map.of("barPhone", true, "foldable", true, "tablet", true))
                .constraints(List.of(constraints))
                .build();
    }

    @Test
    void textCategoryNotInValidEnumProducesSEM_ATTR_003() {
        DslElementNode node = element("Text", Map.of("text", "hello", "category", "Custom"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Text", textRule(attr003Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals("SEM-ATTR-003", diag.getRuleId());
        assertEquals(DiagnosticSeverity.ERROR, diag.getSeverity());
        assertEquals("category枚举值不合法，合法值为: Normal, Charging, BatteryLow, BatteryFull", diag.getMessage());
    }

    @Test
    void textCategoryInValidEnumNoViolation() {
        DslElementNode node = element("Text", Map.of("text", "hello", "category", "Charging"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Text", textRule(attr003Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void textCategoryNullNoViolation() {
        DslElementNode node = element("Text", Map.of("text", "hello"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Text", textRule(attr003Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertTrue(diagnostics.isEmpty());
    }

    // --- SEM-ATTR-004: Group clip=true without width/height (warning) ---

    private static RuleConstraint attr004Constraint() {
        return RuleConstraint.builder()
                .ruleId("SEM-ATTR-004")
                .condition("element.attrs['clip'] == 'true' AND (element.attrs['width'] == null OR element.attrs['height'] == null)")
                .message("clip=true时需要设置width和height来定义裁剪边界")
                .severity(DiagnosticSeverity.WARNING)
                .suggestedFixes(List.of("添加width属性", "添加height属性", "移除clip属性"))
                .build();
    }

    private static DslElementRule groupRule(RuleConstraint... constraints) {
        return DslElementRule.builder()
                .elementName("Group")
                .category("layout")
                .scope(Map.of("Lockscreen", true, "Wallpaper", true, "Widget", true, "ChargingSkin", true))
                .deviceSupport(Map.of("barPhone", true, "foldable", true, "tablet", true))
                .constraints(List.of(constraints))
                .build();
    }

    @Test
    void groupClipTrueWithoutHeightProducesSEM_ATTR_004() {
        DslElementNode node = element("Group", Map.of("clip", "true", "width", "100"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Group", groupRule(attr004Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals("SEM-ATTR-004", diag.getRuleId());
        assertEquals(DiagnosticSeverity.WARNING, diag.getSeverity());
        assertEquals("clip=true时需要设置width和height来定义裁剪边界", diag.getMessage());
    }

    @Test
    void groupClipTrueWithWidthAndHeightNoViolation() {
        DslElementNode node = element("Group", Map.of("clip", "true", "width", "100", "height", "200"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Group", groupRule(attr004Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void groupClipFalseNoViolation() {
        DslElementNode node = element("Group", Map.of("clip", "false"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Group", groupRule(attr004Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertTrue(diagnostics.isEmpty());
    }

    // --- SEM-SRCIMG-001: SourceImage direction=0 without loop/unlockTo (error) ---

    private static RuleConstraint srcimg001Constraint() {
        return RuleConstraint.builder()
                .ruleId("SEM-SRCIMG-001")
                .condition("element.attrs['direction'] == '0' AND (element.attrs['loop'] != 'true' OR element.attrs['unlockTo'] == null)")
                .message("direction=0(帧动画解锁)时loop必须为true且unlockTo必须有值")
                .severity(DiagnosticSeverity.ERROR)
                .suggestedFixes(List.of("设置loop=true", "设置unlockTo属性值", "将direction改为1"))
                .build();
    }

    private static DslElementRule sourceImageRule(RuleConstraint... constraints) {
        return DslElementRule.builder()
                .elementName("SourceImage")
                .category("view")
                .scope(Map.of("Lockscreen", true))
                .deviceSupport(Map.of("barPhone", true, "foldable", true, "tablet", true))
                .constraints(List.of(constraints))
                .build();
    }

    @Test
    void sourceImageDirection0WithoutUnlockToProducesSEM_SRCIMG_001() {
        DslElementNode node = element("SourceImage", Map.of("direction", "0", "loop", "true", "src", "anim.zip"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("SourceImage", sourceImageRule(srcimg001Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals("SEM-SRCIMG-001", diag.getRuleId());
        assertEquals(DiagnosticSeverity.ERROR, diag.getSeverity());
        assertEquals("direction=0(帧动画解锁)时loop必须为true且unlockTo必须有值", diag.getMessage());
        assertEquals(3, diag.getSuggestedFixes().size());
    }

    @Test
    void sourceImageDirection0WithLoopAndUnlockToNoViolation() {
        DslElementNode node = element("SourceImage", Map.of("direction", "0", "loop", "true", "unlockTo", "5", "src", "anim.zip"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("SourceImage", sourceImageRule(srcimg001Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void sourceImageDirection1NoViolation() {
        DslElementNode node = element("SourceImage", Map.of("direction", "1", "src", "anim.zip"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("SourceImage", sourceImageRule(srcimg001Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void sourceImageDirection0LoopFalseProducesSEM_SRCIMG_001() {
        DslElementNode node = element("SourceImage", Map.of("direction", "0", "loop", "false", "unlockTo", "5", "src", "anim.zip"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("SourceImage", sourceImageRule(srcimg001Constraint())),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo));

        assertEquals(1, diagnostics.size());
        assertEquals("SEM-SRCIMG-001", diagnostics.get(0).getRuleId());
    }
}
