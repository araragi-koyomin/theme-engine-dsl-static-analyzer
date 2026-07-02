package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary;
import com.huawei.theme.analysis.core.expression.model.FunctionParam;
import com.huawei.theme.analysis.core.expression.model.FunctionSignature;
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
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.shared.type.DslNumberType;
import com.huawei.theme.analysis.core.shared.type.DslStringType;
import com.huawei.theme.analysis.core.shared.type.DslType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeAnalyzerTest {

    private final TypeAnalyzer analyzer = new TypeAnalyzer();
    private final FunctionSignatureLibrary functionLibrary = stubLibrary();

    // --- SEM-TYPE-001: 表达式类型不匹配 ---

    @Test
    void stringLiteralInNumberAttrProducesSEM_TYPE_001() {
        ExpressionNode expr = ExpressionNode.literal("hello", "'hello'", 1, 0);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", expr, "'hello'"));
        VarDeclaration n = varDecl("n", new DslNumberType());

        List<Diagnostic> diagnostics = analyzer.analyze(image,
                context(repoWithAttrs(), table(n)));

        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals("SEM-TYPE-001", diag.getRuleId());
        assertEquals(DiagnosticSeverity.ERROR, diag.getSeverity());
        assertEquals("类型不匹配，期望number实际string（属性 x）", diag.getMessage());
        assertEquals("test.xml", diag.getFilePath());
        assertEquals(10, diag.getLine());
        assertEquals(5, diag.getColumn());
    }

    @Test
    void numberLiteralInNumberAttrNoViolation() {
        ExpressionNode expr = ExpressionNode.literal("42", "42", 1, 0);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", expr, "42"));

        List<Diagnostic> diagnostics = analyzer.analyze(image,
                context(repoWithAttrs(), emptyTable()));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void hashNumberVarInNumberAttrNoViolation() {
        ExpressionNode expr = ExpressionNode.variableRef("#", "n", "#n", 1, 0);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", expr, "#n"));
        VarDeclaration n = varDecl("n", new DslNumberType());

        List<Diagnostic> diagnostics = analyzer.analyze(image,
                context(repoWithAttrs(), table(n)));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void hashStringVarInNumberAttrProducesSEM_TYPE_001() {
        ExpressionNode expr = ExpressionNode.variableRef("#", "s", "#s", 1, 0);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", expr, "#s"));
        VarDeclaration s = varDecl("s", new DslStringType());

        List<Diagnostic> diagnostics = analyzer.analyze(image,
                context(repoWithAttrs(), table(s)));

        assertEquals(1, diagnostics.size());
        assertEquals("SEM-TYPE-001", diagnostics.get(0).getRuleId());
    }

    @Test
    void stringLiteralInStringAttrNoViolation() {
        ExpressionNode expr = ExpressionNode.literal("hello", "'hello'", 1, 0);
        DslElementNode text = element("Text", 10, 5, exprAttr("textExp", expr, "'hello'"));

        List<Diagnostic> diagnostics = analyzer.analyze(text,
                context(repoWithAttrs(), emptyTable()));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void binaryExprInNumberContextNoViolation() {
        ExpressionNode left = ExpressionNode.variableRef("#", "n", "#n", 1, 0);
        ExpressionNode right = ExpressionNode.literal("2", "2", 1, 0);
        ExpressionNode expr = ExpressionNode.binaryExpr("+", left, right, "#n+2", 1, 0);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", expr, "#n+2"));
        VarDeclaration n = varDecl("n", new DslNumberType());

        List<Diagnostic> diagnostics = analyzer.analyze(image,
                context(repoWithAttrs(), table(n)));

        assertTrue(diagnostics.isEmpty());
    }

    // --- SEM-TYPE-001: auto 上下文（Var.expression）---

    @Test
    void varAutoNumberNoViolation() {
        ExpressionNode expr = ExpressionNode.variableRef("#", "n", "#n", 1, 0);
        DslElementNode var = element("Var", 10, 5,
                exprAttr("expression", expr, "#n"),
                literalAttr("type", "number"));
        VarDeclaration n = varDecl("n", new DslNumberType());

        List<Diagnostic> diagnostics = analyzer.analyze(var,
                context(repoWithAttrs(), table(n)));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void varAutoStringMismatchProducesSEM_TYPE_001() {
        ExpressionNode expr = ExpressionNode.variableRef("#", "n", "#n", 1, 0);
        DslElementNode var = element("Var", 10, 5,
                exprAttr("expression", expr, "#n"),
                literalAttr("type", "string"));
        VarDeclaration n = varDecl("n", new DslNumberType());

        List<Diagnostic> diagnostics = analyzer.analyze(var,
                context(repoWithAttrs(), table(n)));

        assertEquals(1, diagnostics.size());
        assertEquals("SEM-TYPE-001", diagnostics.get(0).getRuleId());
    }

    @Test
    void varAutoDefaultNumberNoTypeAttr() {
        ExpressionNode expr = ExpressionNode.variableRef("#", "n", "#n", 1, 0);
        DslElementNode var = element("Var", 10, 5, exprAttr("expression", expr, "#n"));
        VarDeclaration n = varDecl("n", new DslNumberType());

        List<Diagnostic> diagnostics = analyzer.analyze(var,
                context(repoWithAttrs(), table(n)));

        assertTrue(diagnostics.isEmpty());
    }

    // --- SEM-TYPE-001: 函数不适用上下文 ---

    @Test
    void functionNotApplicableToContextProducesSEM_TYPE_001() {
        ExpressionNode expr = ExpressionNode.functionCall("sin", List.of(), "sin()", 1, 0);
        DslElementNode text = element("Text", 10, 5, exprAttr("textExp", expr, "sin()"));

        List<Diagnostic> diagnostics = analyzer.analyze(text,
                context(repoWithAttrs(), emptyTable()));

        assertEquals(1, diagnostics.size());
        assertEquals("SEM-TYPE-001", diagnostics.get(0).getRuleId());
        assertEquals("函数 sin 不适用于 string 表达式", diagnostics.get(0).getMessage());
    }

    // --- SEM-TYPE-002: 函数参数类型不匹配 ---

    @Test
    void functionParamMismatchProducesSEM_TYPE_002() {
        ExpressionNode arg = ExpressionNode.literal("hello", "'hello'", 1, 0);
        ExpressionNode expr = ExpressionNode.functionCall("sin", List.of(arg), "sin('hello')", 1, 0);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", expr, "sin('hello')"));

        List<Diagnostic> diagnostics = analyzer.analyze(image,
                context(repoWithAttrs(), emptyTable()));

        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals("SEM-TYPE-002", diag.getRuleId());
        assertEquals(DiagnosticSeverity.ERROR, diag.getSeverity());
        assertEquals("函数 sin 参数 1 类型不匹配，期望number实际string", diag.getMessage());
        assertEquals(10, diag.getLine());
        assertEquals(5, diag.getColumn());
    }

    @Test
    void functionNumberContextNoViolation() {
        ExpressionNode arg = ExpressionNode.variableRef("#", "n", "#n", 1, 0);
        ExpressionNode expr = ExpressionNode.functionCall("sin", List.of(arg), "sin(#n)", 1, 0);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", expr, "sin(#n)"));
        VarDeclaration n = varDecl("n", new DslNumberType());

        List<Diagnostic> diagnostics = analyzer.analyze(image,
                context(repoWithAttrs(), table(n)));

        assertTrue(diagnostics.isEmpty());
    }

    // --- 边界 ---

    @Test
    void nonExpressionAttrSkipped() {
        DslElementNode image = element("Image", 10, 5, literalAttr("src", "bg.png"));

        List<Diagnostic> diagnostics = analyzer.analyze(image,
                context(repoWithAttrs(), emptyTable()));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void nullFunctionLibrarySkipped() {
        ExpressionNode expr = ExpressionNode.literal("hello", "'hello'", 1, 0);
        DslElementNode image = element("Image", 10, 5, exprAttr("x", expr, "'hello'"));

        DslContext ctx = new DslContext(repoWithAttrs(), emptyTable(), "test.xml", null);

        List<Diagnostic> diagnostics = analyzer.analyze(image, ctx);

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void nonElementNodeReturnsEmpty() {
        DslAstNode node = new com.huawei.theme.analysis.core.shared.ast.DslFileNode();

        List<Diagnostic> diagnostics = analyzer.analyze(node,
                context(repoWithAttrs(), emptyTable()));

        assertTrue(diagnostics.isEmpty());
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

    private static VarDeclaration varDecl(String name, DslType type) {
        return VarDeclaration.builder()
                .name(name).type(type).expression(null).isConstAttr(false).isGlobal(false).astNode(null)
                .build();
    }

    private static SymbolTable table(VarDeclaration... decls) {
        Map<String, VarDeclaration> map = new HashMap<>();
        for (VarDeclaration d : decls) {
            map.put(d.getName(), d);
        }
        return SymbolTable.builder().parent(null).declarations(map).build();
    }

    private static SymbolTable emptyTable() {
        return SymbolTable.builder().parent(null).declarations(Map.of()).build();
    }

    private DslContext context(RuleRepository ruleRepo, SymbolTable symbolTable) {
        return new DslContext(ruleRepo, symbolTable, "test.xml", functionLibrary);
    }

    private static RuleRepository repoWithAttrs() {
        Map<String, AttrTypeSpec> specs = new HashMap<>();
        specs.put("Image:x", numberExprAttr());
        specs.put("Text:textExp", stringExprAttr());
        specs.put("Var:expression", autoExprAttr());
        specs.put("Image:src", literalAttr());
        return new StubRuleRepository(specs, Map.of());
    }

    private static AttrTypeSpec numberExprAttr() {
        return AttrTypeSpec.builder().type("number").supportsExpression(true).expressionKind("number").build();
    }

    private static AttrTypeSpec stringExprAttr() {
        return AttrTypeSpec.builder().type("string").supportsExpression(true).expressionKind("string").build();
    }

    private static AttrTypeSpec autoExprAttr() {
        return AttrTypeSpec.builder().type("string").supportsExpression(true).expressionKind("auto").build();
    }

    private static AttrTypeSpec literalAttr() {
        return AttrTypeSpec.builder().type("string").supportsExpression(false).expressionKind(null).build();
    }

    private static FunctionSignatureLibrary stubLibrary() {
        FunctionSignature sin = FunctionSignature.builder()
                .name("sin")
                .params(List.of(FunctionParam.builder().name("x").type(new DslNumberType()).isVariadic(false).build()))
                .returnType(new DslNumberType())
                .expressionKind("number")
                .build();
        return new StubFunctionLibrary(Map.of("sin:number", sin));
    }

    private static final class StubFunctionLibrary implements FunctionSignatureLibrary {
        private final Map<String, FunctionSignature> signatures;

        StubFunctionLibrary(Map<String, FunctionSignature> signatures) {
            this.signatures = signatures;
        }

        @Override
        public Optional<FunctionSignature> getSignature(String name, String expressionKind) {
            return Optional.ofNullable(signatures.get(name + ":" + expressionKind));
        }

        @Override
        public List<FunctionSignature> getSignatures(String name) {
            return List.of();
        }

        @Override
        public boolean hasFunction(String name) {
            return false;
        }
    }

    private static final class StubRuleRepository implements RuleRepository {
        private final Map<String, AttrTypeSpec> attrSpecs;
        private final Map<String, RuleSource> ruleSources;

        StubRuleRepository(Map<String, AttrTypeSpec> attrSpecs, Map<String, RuleSource> ruleSources) {
            this.attrSpecs = attrSpecs;
            this.ruleSources = ruleSources;
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
            return Optional.ofNullable(attrSpecs.get(elementName + ":" + attrName));
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
}
