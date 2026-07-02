package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.huawei.theme.analysis.core.semanticanalysis.analyzers.VarRefAnalyzer;
import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.semanticanalysis.model.VarDeclaration;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.type.DslArrayType;
import com.huawei.theme.analysis.core.shared.type.DslNumberType;
import com.huawei.theme.analysis.core.shared.type.DslType;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VarRefAnalyzerTest {

    private final VarRefAnalyzer analyzer = new VarRefAnalyzer();

    // --- SEM-REF-001: 变量引用存在性 ---

    @Test
    void undefinedNumericRefProducesSEM_REF_001() {
        ExpressionNode ref = ExpressionNode.variableRef("#", "x", "#x", 15, 3);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", ref, "#x"));

        List<Diagnostic> diagnostics = analyzer.analyze(image, context(stubRepo(), globalTable()));

        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals("SEM-REF-001", diag.getRuleId());
        assertEquals(DiagnosticSeverity.ERROR, diag.getSeverity());
        assertEquals("引用未定义变量 #x", diag.getMessage());
        assertEquals("test.xml", diag.getFilePath());
        assertEquals(15, diag.getLine());
        assertEquals(3, diag.getColumn());
        assertEquals(1, diag.getSuggestedFixes().size());
        assertEquals("声明 Var name=\"x\"", diag.getSuggestedFixes().get(0));
    }

    @Test
    void undefinedStringRefProducesSEM_REF_001() {
        ExpressionNode ref = ExpressionNode.variableRef("@", "str", "@str", 15, 3);
        DslElementNode text = element("Text", 10, 5, exprAttr("textExp", ref, "@str"));

        List<Diagnostic> diagnostics = analyzer.analyze(text, context(stubRepo(), globalTable()));

        assertEquals(1, diagnostics.size());
        assertEquals("SEM-REF-001", diagnostics.get(0).getRuleId());
        assertEquals("引用未定义变量 @str", diagnostics.get(0).getMessage());
    }

    @Test
    void definedVarReferenceNoViolation() {
        ExpressionNode ref = ExpressionNode.variableRef("#", "v", "#v", 15, 3);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", ref, "#v"));
        VarDeclaration v = varDecl("v", new DslNumberType(), null);

        List<Diagnostic> diagnostics = analyzer.analyze(image, context(stubRepo(), globalTable(v)));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void presetGlobalVarReferenceNoViolation() {
        ExpressionNode ref = ExpressionNode.variableRef("#", "battery_level", "#battery_level", 15, 3);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", ref, "#battery_level"));
        VarDeclaration battery = varDecl("battery_level", new DslNumberType(), null);

        List<Diagnostic> diagnostics = analyzer.analyze(image, context(stubRepo(), globalTable(battery)));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void dottedGlobalVarNameLookedUpAsWhole() {
        ExpressionNode ref = ExpressionNode.variableRef("#", "system.time.hour1", "#system.time.hour1", 15, 3);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", ref, "#system.time.hour1"));
        VarDeclaration hour = varDecl("system.time.hour1", new DslNumberType(), null);

        List<Diagnostic> diagnostics = analyzer.analyze(image, context(stubRepo(), globalTable(hour)));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void binaryExprReportsOnlyUndefinedRef() {
        ExpressionNode a = ExpressionNode.variableRef("#", "a", "#a", 15, 0);
        ExpressionNode b = ExpressionNode.variableRef("#", "b", "#b", 15, 4);
        ExpressionNode binary = ExpressionNode.binaryExpr("+", a, b, "#a+#b", 15, 0);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", binary, "#a+#b"));
        VarDeclaration aDecl = varDecl("a", new DslNumberType(), null);

        List<Diagnostic> diagnostics = analyzer.analyze(image, context(stubRepo(), globalTable(aDecl)));

        assertEquals(1, diagnostics.size());
        assertEquals("SEM-REF-001", diagnostics.get(0).getRuleId());
        assertEquals(15, diagnostics.get(0).getLine());
        assertEquals(4, diagnostics.get(0).getColumn());
        assertEquals("引用未定义变量 #b", diagnostics.get(0).getMessage());
    }

    @Test
    void binaryExprReportsBothUndefinedRefs() {
        ExpressionNode a = ExpressionNode.variableRef("#", "a", "#a", 15, 0);
        ExpressionNode b = ExpressionNode.variableRef("#", "b", "#b", 15, 4);
        ExpressionNode binary = ExpressionNode.binaryExpr("+", a, b, "#a+#b", 15, 0);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", binary, "#a+#b"));

        List<Diagnostic> diagnostics = analyzer.analyze(image, context(stubRepo(), globalTable()));

        assertEquals(2, diagnostics.size());
    }

    @Test
    void arrayAccessReportsUndefinedIndexRef() {
        ExpressionNode index = ExpressionNode.variableRef("#", "i", "#i", 15, 5);
        ExpressionNode arr = ExpressionNode.arrayAccess("#", "arr", index, "#arr[#i]", 15, 0);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", arr, "#arr[#i]"));
        VarDeclaration arrDecl = varDecl("arr", DslArrayType.builder().baseType("number").build(), null);

        List<Diagnostic> diagnostics = analyzer.analyze(image, context(stubRepo(), globalTable(arrDecl)));

        assertEquals(1, diagnostics.size());
        assertEquals("SEM-REF-001", diagnostics.get(0).getRuleId());
        assertEquals(15, diagnostics.get(0).getLine());
        assertEquals(5, diagnostics.get(0).getColumn());
        assertEquals("引用未定义变量 #i", diagnostics.get(0).getMessage());
    }

    @Test
    void arrayAccessReportsUndefinedArrayVar() {
        ExpressionNode index = ExpressionNode.variableRef("#", "i", "#i", 15, 5);
        ExpressionNode arr = ExpressionNode.arrayAccess("#", "arr", index, "#arr[#i]", 15, 0);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", arr, "#arr[#i]"));
        VarDeclaration iDecl = varDecl("i", new DslNumberType(), null);

        List<Diagnostic> diagnostics = analyzer.analyze(image, context(stubRepo(), globalTable(iDecl)));

        assertEquals(1, diagnostics.size());
        assertEquals(15, diagnostics.get(0).getLine());
        assertEquals(0, diagnostics.get(0).getColumn());
        assertEquals("引用未定义变量 #arr", diagnostics.get(0).getMessage());
    }

    @Test
    void functionCallArgReferenceChecked() {
        ExpressionNode arg = ExpressionNode.variableRef("#", "x", "#x", 15, 4);
        ExpressionNode call = ExpressionNode.functionCall("sin", List.of(arg), "sin(#x)", 15, 0);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", call, "sin(#x)"));

        List<Diagnostic> diagnostics = analyzer.analyze(image, context(stubRepo(), globalTable()));

        assertEquals(1, diagnostics.size());
        assertEquals("引用未定义变量 #x", diagnostics.get(0).getMessage());
    }

    @Test
    void indexFlagLocalVarInArrayScopeNoViolation() {
        DslElementNode array = element("Array", 10, 0, literalAttr("indexFlag", "__i"));
        ExpressionNode ref = ExpressionNode.variableRef("#", "__i", "#__i", 12, 5);
        DslElementNode image = element("Image", 12, 4, exprAttr("x", ref, "#__i"));
        array.setChildElements(new ArrayList<>(List.of(image)));

        SymbolTableBuilder builder = new SymbolTableBuilderImpl();
        SymbolTable arrayScope = builder.build(array, globalTable(), stubRepo());

        List<Diagnostic> diagnostics = analyzer.analyze(image, context(stubRepo(), arrayScope));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void indexFlagLocalVarOutOfScopeProducesSEM_REF_001() {
        ExpressionNode ref = ExpressionNode.variableRef("#", "__i", "#__i", 20, 5);
        DslElementNode sibling = element("Image", 20, 0, exprAttr("x", ref, "#__i"));

        List<Diagnostic> diagnostics = analyzer.analyze(sibling, context(stubRepo(), globalTable()));

        assertEquals(1, diagnostics.size());
        assertEquals("SEM-REF-001", diagnostics.get(0).getRuleId());
    }

    @Test
    void literalAttrNoViolation() {
        DslElementNode image = element("Image", 10, 5, literalAttr("x", "100"));

        List<Diagnostic> diagnostics = analyzer.analyze(image, context(stubRepo(), globalTable()));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void parseFailedAttrNoViolation() {
        DslElementNode image = element("Image", 10, 5, parseFailedAttr("x", "#x+"));

        List<Diagnostic> diagnostics = analyzer.analyze(image, context(stubRepo(), globalTable()));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void diagnosticHasDocUrlFromRuleSource() {
        ExpressionNode ref = ExpressionNode.variableRef("#", "x", "#x", 15, 3);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", ref, "#x"));
        RuleSource source = RuleSource.builder()
                .ruleId("SEM-REF-001")
                .category("SEM")
                .description("引用未定义的变量名")
                .docUrl("https://doc/sem-ref-001")
                .build();
        RuleRepository repo = stubRepo(Map.of("SEM-REF-001", source));

        List<Diagnostic> diagnostics = analyzer.analyze(image, context(repo, globalTable()));

        assertEquals(1, diagnostics.size());
        assertEquals("https://doc/sem-ref-001", diagnostics.get(0).getRuleDocUrl());
    }

    @Test
    void refPositionZeroFallsBackToHostElement() {
        ExpressionNode ref = ExpressionNode.variableRef("#", "x", "#x", 0, 0);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", ref, "#x"));

        List<Diagnostic> diagnostics = analyzer.analyze(image, context(stubRepo(), globalTable()));

        assertEquals(1, diagnostics.size());
        assertEquals(10, diagnostics.get(0).getLine());
        assertEquals(5, diagnostics.get(0).getColumn());
    }

    @Test
    void varExpressionReferencingGlobalNoViolation() {
        ExpressionNode ref = ExpressionNode.variableRef("#", "battery_level", "#battery_level", 5, 0);
        DslElementNode var = element("Var", 5, 0,
                exprAttr("expression", ref, "#battery_level"),
                literalAttr("name", "v"),
                literalAttr("type", "number"));
        VarDeclaration battery = varDecl("battery_level", new DslNumberType(), null);
        VarDeclaration v = varDecl("v", new DslNumberType(), var);

        List<Diagnostic> diagnostics = analyzer.analyze(var, context(stubRepo(), globalTable(battery, v)));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void varExpressionReferencingUndefinedProducesSEM_REF_001() {
        ExpressionNode ref = ExpressionNode.variableRef("#", "missing", "#missing", 5, 20);
        DslElementNode var = element("Var", 5, 0,
                exprAttr("expression", ref, "#missing"),
                literalAttr("name", "v"));
        VarDeclaration v = varDecl("v", new DslNumberType(), var);

        List<Diagnostic> diagnostics = analyzer.analyze(var, context(stubRepo(), globalTable(v)));

        assertEquals(1, diagnostics.size());
        assertEquals("SEM-REF-001", diagnostics.get(0).getRuleId());
        assertEquals(5, diagnostics.get(0).getLine());
        assertEquals(20, diagnostics.get(0).getColumn());
    }

    @Test
    void nonElementNodeReturnsEmpty() {
        DslFileNode fileNode = new DslFileNode();
        fileNode.setFilePath("test.xml");

        List<Diagnostic> diagnostics = analyzer.analyze(fileNode, context(stubRepo(), globalTable()));

        assertTrue(diagnostics.isEmpty());
    }

    // --- SEM-REF-002: 元素 name 引用存在性（表达式场景） ---

    @Test
    void elementPropertyRefWithExistingElementNoViolation() {
        ExpressionNode ref = ExpressionNode.variableRef("#", "unlocker.move_x", "#unlocker.move_x", 15, 3);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", ref, "#unlocker.move_x"));
        VarDeclaration battery = varDecl("battery_level", new DslNumberType(), null);

        List<Diagnostic> diagnostics = analyzer.analyze(image,
                context(repoWithTemplates(), globalTable(elementNames("unlocker"), battery)));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void elementPropertyRefWithUndefinedElementProducesSEM_REF_002() {
        ExpressionNode ref = ExpressionNode.variableRef("#", "unlocker.move_x", "#unlocker.move_x", 15, 3);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", ref, "#unlocker.move_x"));

        List<Diagnostic> diagnostics = analyzer.analyze(image,
                context(repoWithTemplates(), globalTable(elementNames())));

        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals("SEM-REF-002", diag.getRuleId());
        assertEquals(DiagnosticSeverity.ERROR, diag.getSeverity());
        assertEquals("引用未定义元素 unlocker", diag.getMessage());
        assertEquals("test.xml", diag.getFilePath());
        assertEquals(15, diag.getLine());
        assertEquals(3, diag.getColumn());
        assertEquals(1, diag.getSuggestedFixes().size());
        assertEquals("声明带 name=\"unlocker\" 的元素", diag.getSuggestedFixes().get(0));
    }

    @Test
    void videoNameTemplateRefWithUndefinedElementProducesSEM_REF_002() {
        ExpressionNode ref = ExpressionNode.variableRef("#", "myVideo.state", "#myVideo.state", 15, 3);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", ref, "#myVideo.state"));

        List<Diagnostic> diagnostics = analyzer.analyze(image,
                context(repoWithTemplates(), globalTable(elementNames())));

        assertEquals(1, diagnostics.size());
        assertEquals("SEM-REF-002", diagnostics.get(0).getRuleId());
        assertEquals("引用未定义元素 myVideo", diagnostics.get(0).getMessage());
    }

    @Test
    void videoCurrentTimeStringRefWithExistingElementNoViolation() {
        ExpressionNode ref = ExpressionNode.variableRef("@", "myVideo.currentTime", "@myVideo.currentTime", 15, 3);
        DslElementNode text = element("Text", 10, 5, exprAttr("textExp", ref, "@myVideo.currentTime"));

        List<Diagnostic> diagnostics = analyzer.analyze(text,
                context(repoWithTemplates(), globalTable(elementNames("myVideo"))));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void elementPropertyRefDocUrlFromRuleSource() {
        ExpressionNode ref = ExpressionNode.variableRef("#", "unlocker.move_x", "#unlocker.move_x", 15, 3);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", ref, "#unlocker.move_x"));
        RuleSource source = RuleSource.builder()
                .ruleId("SEM-REF-002").category("SEM").description("引用未定义的元素name")
                .docUrl("https://doc/sem-ref-002").build();
        RuleRepository repo = repoWithTemplatesAndSources(Map.of("SEM-REF-002", source));

        List<Diagnostic> diagnostics = analyzer.analyze(image, context(repo, globalTable(elementNames())));

        assertEquals(1, diagnostics.size());
        assertEquals("https://doc/sem-ref-002", diagnostics.get(0).getRuleDocUrl());
    }

    // --- SEM-REF-001 协同：模板匹配引用不走 001 ---

    @Test
    void templateMatchedRefDoesNotProduceSEM_REF_001() {
        ExpressionNode ref = ExpressionNode.variableRef("#", "unlocker.move_x", "#unlocker.move_x", 15, 3);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", ref, "#unlocker.move_x"));

        List<Diagnostic> diagnostics = analyzer.analyze(image,
                context(repoWithTemplates(), globalTable(elementNames("unlocker"))));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void nonTemplateRefFallsBackToSEM_REF_001() {
        ExpressionNode ref = ExpressionNode.variableRef("#", "undefined", "#undefined", 15, 3);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", ref, "#undefined"));

        List<Diagnostic> diagnostics = analyzer.analyze(image,
                context(repoWithTemplates(), globalTable(elementNames())));

        assertEquals(1, diagnostics.size());
        assertEquals("SEM-REF-001", diagnostics.get(0).getRuleId());
    }

    @Test
    void dottedGlobalVarNotMatchingTemplateFallsBackToSEM_REF_001() {
        ExpressionNode ref = ExpressionNode.variableRef("#", "system.time.hour1", "#system.time.hour1", 15, 3);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", ref, "#system.time.hour1"));
        VarDeclaration hour = varDecl("system.time.hour1", new DslNumberType(), null);

        List<Diagnostic> diagnostics = analyzer.analyze(image,
                context(repoWithTemplates(), globalTable(elementNames(), hour)));

        assertTrue(diagnostics.isEmpty());
    }

    // --- SEM-REF-002: Command target 引用 ---

    @Test
    void commandTargetWithExistingElementAndValidPropertyNoViolation() {
        DslElementNode cmd = element("Command", 10, 5,
                literalAttr("target", "img.visibility"), literalAttr("value", "false"));

        List<Diagnostic> diagnostics = analyzer.analyze(cmd,
                context(repoWithTemplates(), globalTable(elementNames("img"))));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void commandTargetWithUndefinedElementProducesSEM_REF_002() {
        DslElementNode cmd = element("Command", 10, 5,
                literalAttr("target", "img.visibility"), literalAttr("value", "false"));

        List<Diagnostic> diagnostics = analyzer.analyze(cmd,
                context(repoWithTemplates(), globalTable(elementNames())));

        assertEquals(1, diagnostics.size());
        assertEquals("SEM-REF-002", diagnostics.get(0).getRuleId());
        assertEquals("Command target 引用未定义元素 img", diagnostics.get(0).getMessage());
        assertEquals(10, diagnostics.get(0).getLine());
        assertEquals(5, diagnostics.get(0).getColumn());
        assertEquals(1, diagnostics.get(0).getSuggestedFixes().size());
        assertEquals("声明元素 name=\"img\"", diagnostics.get(0).getSuggestedFixes().get(0));
    }

    @Test
    void commandTargetAnimationPropertyNoViolation() {
        DslElementNode cmd = element("Command", 10, 5,
                literalAttr("target", "img.animation"), literalAttr("value", "play"));

        List<Diagnostic> diagnostics = analyzer.analyze(cmd,
                context(repoWithTemplates(), globalTable(elementNames("img"))));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void commandTargetInvalidPropertyProducesSEM_REF_002() {
        DslElementNode cmd = element("Command", 10, 5,
                literalAttr("target", "img.unknown"), literalAttr("value", "false"));

        List<Diagnostic> diagnostics = analyzer.analyze(cmd,
                context(repoWithTemplates(), globalTable(elementNames("img"))));

        assertEquals(1, diagnostics.size());
        assertEquals("SEM-REF-002", diagnostics.get(0).getRuleId());
        assertEquals("Command target 属性 'unknown' 不合法，合法值: visibility, animation",
                diagnostics.get(0).getMessage());
    }

    @Test
    void commandTargetUndefinedElementAndInvalidPropertyReportsBoth() {
        DslElementNode cmd = element("Command", 10, 5,
                literalAttr("target", "missing.unknown"), literalAttr("value", "false"));

        List<Diagnostic> diagnostics = analyzer.analyze(cmd,
                context(repoWithTemplates(), globalTable(elementNames())));

        assertEquals(2, diagnostics.size());
    }

    @Test
    void commandTargetWithoutDotSkipped() {
        DslElementNode cmd = element("Command", 10, 5,
                literalAttr("target", "img"), literalAttr("value", "false"));

        List<Diagnostic> diagnostics = analyzer.analyze(cmd,
                context(repoWithTemplates(), globalTable(elementNames())));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void commandTargetWithMultipleDotsSkipped() {
        DslElementNode cmd = element("Command", 10, 5,
                literalAttr("target", "a.b.c"), literalAttr("value", "false"));

        List<Diagnostic> diagnostics = analyzer.analyze(cmd,
                context(repoWithTemplates(), globalTable(elementNames())));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void commandTargetDiagnosticHasDocUrlFromRuleSource() {
        DslElementNode cmd = element("Command", 10, 5,
                literalAttr("target", "img.visibility"), literalAttr("value", "false"));
        RuleSource source = RuleSource.builder()
                .ruleId("SEM-REF-002").category("SEM").description("引用未定义的元素name")
                .docUrl("https://doc/sem-ref-002").build();
        RuleRepository repo = repoWithTemplatesAndSources(Map.of("SEM-REF-002", source));

        List<Diagnostic> diagnostics = analyzer.analyze(cmd, context(repo, globalTable(elementNames())));

        assertEquals(1, diagnostics.size());
        assertEquals("https://doc/sem-ref-002", diagnostics.get(0).getRuleDocUrl());
    }

    @Test
    void nonCommandElementSkipsTargetCheck() {
        DslElementNode image = element("Image", 10, 5, literalAttr("target", "img.unknown"));

        List<Diagnostic> diagnostics = analyzer.analyze(image,
                context(repoWithTemplates(), globalTable(elementNames())));

        assertTrue(diagnostics.isEmpty());
    }

    // --- SEM-REF-003: 重复变量定义 ---

    @Test
    void duplicateVarDeclarationReportsOverriddenOnes() {
        DslElementNode var1 = element("Var", 5, 0, literalAttr("name", "v"), literalAttr("type", "number"));
        DslElementNode var2 = element("Var", 8, 0, literalAttr("name", "v"), literalAttr("type", "number"));
        VarDeclaration effective = varDecl("v", new DslNumberType(), var2);
        SymbolTable table = globalTable(effective);

        List<Diagnostic> d1 = analyzer.analyze(var1, context(stubRepo(), table));
        List<Diagnostic> d2 = analyzer.analyze(var2, context(stubRepo(), table));

        assertEquals(1, d1.size());
        assertEquals("SEM-REF-003", d1.get(0).getRuleId());
        assertEquals(DiagnosticSeverity.ERROR, d1.get(0).getSeverity());
        assertEquals("重复定义变量 v", d1.get(0).getMessage());
        assertEquals("test.xml", d1.get(0).getFilePath());
        assertEquals(5, d1.get(0).getLine());
        assertEquals(0, d1.get(0).getColumn());
        assertEquals(1, d1.get(0).getSuggestedFixes().size());
        assertEquals("移除重复的 Var 声明", d1.get(0).getSuggestedFixes().get(0));
        assertTrue(d2.isEmpty());
    }

    @Test
    void singleVarDeclarationNoViolation() {
        DslElementNode var1 = element("Var", 5, 0, literalAttr("name", "v"), literalAttr("type", "number"));
        VarDeclaration effective = varDecl("v", new DslNumberType(), var1);
        SymbolTable table = globalTable(effective);

        List<Diagnostic> diagnostics = analyzer.analyze(var1, context(stubRepo(), table));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void tripleDuplicateVarDeclarations() {
        DslElementNode var1 = element("Var", 5, 0, literalAttr("name", "v"));
        DslElementNode var2 = element("Var", 8, 0, literalAttr("name", "v"));
        DslElementNode var3 = element("Var", 12, 0, literalAttr("name", "v"));
        VarDeclaration effective = varDecl("v", new DslNumberType(), var3);
        SymbolTable table = globalTable(effective);

        assertEquals(1, analyzer.analyze(var1, context(stubRepo(), table)).size());
        assertEquals(1, analyzer.analyze(var2, context(stubRepo(), table)).size());
        assertTrue(analyzer.analyze(var3, context(stubRepo(), table)).isEmpty());
    }

    @Test
    void varWithoutNameSkipsDuplicateCheck() {
        DslElementNode var1 = element("Var", 5, 0, literalAttr("type", "number"));

        List<Diagnostic> diagnostics = analyzer.analyze(var1, context(stubRepo(), globalTable()));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void nonVarElementSkipsDuplicateCheck() {
        DslElementNode image = element("Image", 5, 0, literalAttr("name", "v"));

        List<Diagnostic> diagnostics = analyzer.analyze(image, context(stubRepo(), globalTable()));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void duplicateVarDiagnosticHasDocUrlFromRuleSource() {
        DslElementNode var1 = element("Var", 5, 0, literalAttr("name", "v"));
        DslElementNode var2 = element("Var", 8, 0, literalAttr("name", "v"));
        VarDeclaration effective = varDecl("v", new DslNumberType(), var2);
        RuleSource source = RuleSource.builder()
                .ruleId("SEM-REF-003")
                .category("SEM")
                .description("重复name定义")
                .docUrl("https://doc/sem-ref-003")
                .build();
        RuleRepository repo = stubRepo(Map.of("SEM-REF-003", source));

        List<Diagnostic> diagnostics = analyzer.analyze(var1, context(repo, globalTable(effective)));

        assertEquals(1, diagnostics.size());
        assertEquals("https://doc/sem-ref-003", diagnostics.get(0).getRuleDocUrl());
    }

    // ---- helpers ----

    private static DslElementNode element(String tag, int line, int column, DslAttributeNode... attrs) {
        DslElementNode node = new DslElementNode();
        node.setTagName(tag);
        node.setLine(line);
        node.setColumn(column);
        node.setAttributes(new ArrayList<>(Arrays.asList(attrs)));
        node.setChildElements(new ArrayList<>());
        node.setSelfClosing(false);
        node.setHasError(false);
        return node;
    }

    private static DslAttributeNode exprAttr(String name, ExpressionAstNode expr, String rawValue) {
        DslAttributeNode a = new DslAttributeNode();
        a.setName(name);
        a.setText(rawValue);
        DslAttributeValueNode v = new DslAttributeValueNode();
        v.setRawValue(rawValue);
        v.setText(rawValue);
        v.setLiteral(false);
        v.setExpression(Optional.of(expr));
        a.setValue(v);
        return a;
    }

    private static DslAttributeNode literalAttr(String name, String rawValue) {
        DslAttributeNode a = new DslAttributeNode();
        a.setName(name);
        a.setText(rawValue);
        DslAttributeValueNode v = new DslAttributeValueNode();
        v.setRawValue(rawValue);
        v.setText(rawValue);
        v.setLiteral(true);
        v.setExpression(Optional.empty());
        a.setValue(v);
        return a;
    }

    private static DslAttributeNode parseFailedAttr(String name, String rawValue) {
        DslAttributeNode a = new DslAttributeNode();
        a.setName(name);
        a.setText(rawValue);
        DslAttributeValueNode v = new DslAttributeValueNode();
        v.setRawValue(rawValue);
        v.setText(rawValue);
        v.setLiteral(false);
        v.setExpression(Optional.empty());
        a.setValue(v);
        return a;
    }

    private static VarDeclaration varDecl(String name, DslType type, DslElementNode astNode) {
        return VarDeclaration.builder()
                .name(name)
                .type(type)
                .expression(null)
                .isConstAttr(false)
                .isGlobal(astNode == null)
                .astNode(astNode)
                .build();
    }

    private static SymbolTable globalTable(VarDeclaration... decls) {
        Map<String, VarDeclaration> map = new HashMap<>();
        for (VarDeclaration d : decls) {
            map.put(d.getName(), d);
        }
        return SymbolTable.builder().parent(null).declarations(map).build();
    }

    private static SymbolTable globalTable(Set<String> elementNames, VarDeclaration... decls) {
        Map<String, VarDeclaration> map = new HashMap<>();
        for (VarDeclaration d : decls) {
            map.put(d.getName(), d);
        }
        return SymbolTable.builder().parent(null).declarations(map).elementNames(elementNames).build();
    }

    private static Set<String> elementNames(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    private static DslGlobalVar elementTemplate(String name) {
        return DslGlobalVar.builder().name(name).type("number").scope("element").build();
    }

    private static DslGlobalVar globalVar(String name, String type) {
        return DslGlobalVar.builder().name(name).type(type).scope("global").build();
    }

    private static RuleRepository repoWithTemplates() {
        Map<String, DslGlobalVar> globals = new HashMap<>();
        globals.put("{elementName}.move_x", elementTemplate("{elementName}.move_x"));
        globals.put("{elementName}.visibility", elementTemplate("{elementName}.visibility"));
        globals.put("{videoName}.state", elementTemplate("{videoName}.state"));
        globals.put("{videoName}.currentTime", elementTemplate("{videoName}.currentTime"));
        return new StubRuleRepository(Map.of(), globals);
    }

    private static RuleRepository repoWithTemplatesAndSources(Map<String, RuleSource> sources) {
        Map<String, DslGlobalVar> globals = new HashMap<>();
        globals.put("{elementName}.move_x", elementTemplate("{elementName}.move_x"));
        globals.put("{elementName}.visibility", elementTemplate("{elementName}.visibility"));
        globals.put("{videoName}.state", elementTemplate("{videoName}.state"));
        globals.put("{videoName}.currentTime", elementTemplate("{videoName}.currentTime"));
        return new StubRuleRepository(sources, globals);
    }

    private static DslContext context(RuleRepository ruleRepo, SymbolTable symbolTable) {
        return new DslContext(ruleRepo, symbolTable, "test.xml");
    }

    private static RuleRepository stubRepo() {
        return new StubRuleRepository(Map.of(), Map.of());
    }

    private static RuleRepository stubRepo(Map<String, RuleSource> ruleSources) {
        return new StubRuleRepository(ruleSources, Map.of());
    }

    private static final class StubRuleRepository implements RuleRepository {
        private final Map<String, RuleSource> ruleSources;
        private final Map<String, DslGlobalVar> globalVars;

        StubRuleRepository(Map<String, RuleSource> ruleSources, Map<String, DslGlobalVar> globalVars) {
            this.ruleSources = ruleSources;
            this.globalVars = globalVars;
        }

        @Override
        public Optional<DslElementRule> getElementRule(String elementName) {
            return Optional.empty();
        }

        @Override
        public List<DslElementRule> getAllElementRules() {
            return Collections.emptyList();
        }

        @Override
        public List<String> getAllElementNames() {
            return Collections.emptyList();
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
            return Collections.emptyList();
        }

        @Override
        public Optional<DslGlobalVar> getGlobalVar(String varName) {
            return Optional.ofNullable(globalVars.get(varName));
        }

        @Override
        public List<DslGlobalVar> getAllGlobalVars() {
            return List.copyOf(globalVars.values());
        }

        @Override
        public Optional<RuleSource> getRuleSource(String ruleId) {
            return Optional.ofNullable(ruleSources.get(ruleId));
        }

        @Override
        public com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary getFunctionSignatureLibrary() {
            return null;
        }
    }
}
