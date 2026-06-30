package com.huawei.theme.analysis.core.semanticanalysis.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.rulelibrary.DefaultRuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilderImpl;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.type.DslArrayType;
import com.huawei.theme.analysis.core.shared.type.DslNumberType;

class SymbolTableTest {

    @Test
    void symbolTableWithDeclarationsAndReferences() {
        DslElementNode astNode = new DslElementNode();
        astNode.setTagName("Var");
        astNode.setAttributes(List.of());
        astNode.setChildElements(List.of());
        astNode.setSelfClosing(false);
        astNode.setHasError(false);
        astNode.setText("<Var>");
        astNode.setLine(5);
        astNode.setColumn(0);

        VarDeclaration decl = VarDeclaration.builder()
                .name("steps_value")
                .type(new DslNumberType())
                .expression(null) //TODO
                .isConstAttr(false)
                .astNode(astNode)
                .build();

        VarReference ref = VarReference.builder()
                .name("steps_value")
                .kind(ReferenceKind.NUMERIC)
                .astNode(astNode)
                .build();

        Map<String, VarDeclaration> declarations = new HashMap<>();
        declarations.put("steps_value", decl);

        SymbolTable table = SymbolTable.builder()
                .declarations(declarations)
//                .references(List.of(ref))
                .build();

        assertTrue(table.getDeclarations().containsKey("steps_value"));
        assertEquals("number", table.getDeclarations().get("steps_value").getType().getName());
//        assertEquals(1, table.getReferences().size());
//        assertEquals(ReferenceKind.NUMERIC, table.getReferences().get(0).getKind());
    }

    @Test
    void buildGlobal_loadsPresetGlobalVarsFromRepository() {
        DslGlobalVar battery = DslGlobalVar.builder().name("battery_level").type("number").scope("global").build();
        DslGlobalVar ishour12 = DslGlobalVar.builder().name("ishour12").type("string").scope("global").build();
        RuleRepository repo = repoWithGlobals(battery, ishour12);

        SymbolTable table = new SymbolTableBuilderImpl().buildGlobal(file(elem("Lockscreen")), repo);

        VarDeclaration batteryDecl = table.getDeclarations().get("battery_level");
        assertNotNull(batteryDecl);
        assertEquals("battery_level", batteryDecl.getName());
        assertEquals("number", batteryDecl.getType().getName());
        assertTrue(batteryDecl.isGlobal());
        assertNull(batteryDecl.getAstNode());
        assertNull(batteryDecl.getExpression());

        VarDeclaration hourDecl = table.getDeclarations().get("ishour12");
        assertNotNull(hourDecl);
        assertEquals("string", hourDecl.getType().getName());
    }

    @Test
    void buildGlobal_nullFileNodeReturnsOnlyPresetGlobals() {
        DslGlobalVar battery = DslGlobalVar.builder().name("battery_level").type("number").scope("global").build();
        RuleRepository repo = repoWithGlobals(battery);

        SymbolTable table = new SymbolTableBuilderImpl().buildGlobal(null, repo);

        assertEquals(1, table.getDeclarations().size());
        assertNotNull(table.getDeclarations().get("battery_level"));
    }

    @Test
    void buildGlobal_collectsVarElementsAnywhereInTree() {
        DslElementNode varA = var("a", "number");
        DslElementNode varB = var("b", "string");
        DslElementNode nested = elem("Group", varB);
        DslElementNode root = elem("Lockscreen", varA, nested);

        SymbolTable table = new SymbolTableBuilderImpl().buildGlobal(file(root), emptyRepo());

        VarDeclaration a = table.getDeclarations().get("a");
        assertNotNull(a);
        assertEquals("number", a.getType().getName());
        assertFalse(a.isGlobal());
        assertSame(varA, a.getAstNode());

        VarDeclaration b = table.getDeclarations().get("b");
        assertNotNull(b);
        assertEquals("string", b.getType().getName());
        assertFalse(b.isGlobal());
        assertSame(varB, b.getAstNode());
    }

    @Test
    void buildGlobal_varWithoutTypeDefaultsToNumber() {
        DslElementNode varNode = var("counter", null);

        SymbolTable table = new SymbolTableBuilderImpl().buildGlobal(file(elem("Lockscreen", varNode)), emptyRepo());

        VarDeclaration decl = table.getDeclarations().get("counter");
        assertNotNull(decl);
        assertEquals("number", decl.getType().getName());
    }

    @Test
    void buildGlobal_arrayTypeVarMappedToDslArrayType() {
        DslElementNode varNode = var("arr", "number[]");

        SymbolTable table = new SymbolTableBuilderImpl().buildGlobal(file(elem("Lockscreen", varNode)), emptyRepo());

        VarDeclaration decl = table.getDeclarations().get("arr");
        assertNotNull(decl);
        assertTrue(decl.getType() instanceof DslArrayType);
        assertEquals("number", ((DslArrayType) decl.getType()).getBaseType());
    }

    @Test
    void buildGlobal_constVarSetsIsConstAttr() {
        DslElementNode varNode = var("pi", "number", attr("const", "true"));

        SymbolTable table = new SymbolTableBuilderImpl().buildGlobal(file(elem("Lockscreen", varNode)), emptyRepo());

        VarDeclaration decl = table.getDeclarations().get("pi");
        assertNotNull(decl);
        assertTrue(decl.isConstAttr());
    }

    @Test
    void buildGlobal_varWithoutNameIsSkipped() {
        DslElementNode varNode = var("", null);

        SymbolTable table = new SymbolTableBuilderImpl().buildGlobal(file(elem("Lockscreen", varNode)), emptyRepo());

        assertTrue(table.getDeclarations().isEmpty());
    }

    @Test
    void buildGlobal_populatesExpressionFromVarExpressionAttr() {
        ExpressionAstNode expr = ExpressionNode.literal("1", "1", 2, 3);
        DslElementNode varNode = var("v", "number", exprAttr("expression", "1", expr));

        SymbolTable table = new SymbolTableBuilderImpl().buildGlobal(file(elem("Lockscreen", varNode)), emptyRepo());

        VarDeclaration decl = table.getDeclarations().get("v");
        assertNotNull(decl);
        assertSame(expr, decl.getExpression());
    }

    @Test
    void buildGlobal_userVarOverridesPresetGlobalWithSameName() {
        DslGlobalVar preset = DslGlobalVar.builder().name("dup").type("number").scope("global").build();
        DslElementNode varNode = var("dup", "string");

        SymbolTable table = new SymbolTableBuilderImpl().buildGlobal(file(elem("Lockscreen", varNode)), repoWithGlobals(preset));

        VarDeclaration decl = table.getDeclarations().get("dup");
        assertNotNull(decl);
        assertEquals("string", decl.getType().getName());
        assertFalse(decl.isGlobal());
        assertSame(varNode, decl.getAstNode());
    }

    @Test
    void build_arrayWithIndexFlagCreatesLocalNumberVar() {
        SymbolTable parent = emptyTable();
        DslElementNode array = indexedElement("Array", "__i");

        SymbolTable child = new SymbolTableBuilderImpl().build(array, parent, emptyRepo());

        VarDeclaration decl = child.getDeclarations().get("__i");
        assertNotNull(decl);
        assertEquals("__i", decl.getName());
        assertEquals("number", decl.getType().getName());
        assertFalse(decl.isGlobal());
        assertSame(array, decl.getAstNode());
        assertSame(parent, child.getParent());
    }

    @Test
    void build_cycleCommandWithIndexFlagCreatesLocalNumberVar() {
        SymbolTable parent = emptyTable();
        DslElementNode cmd = indexedElement("CycleCommand", "col");

        SymbolTable child = new SymbolTableBuilderImpl().build(cmd, parent, emptyRepo());

        VarDeclaration decl = child.getDeclarations().get("col");
        assertNotNull(decl);
        assertEquals("number", decl.getType().getName());
        assertFalse(decl.isGlobal());
        assertSame(cmd, decl.getAstNode());
        assertSame(parent, child.getParent());
    }

    @Test
    void build_nonLocalElementReturnsParentUnchanged() {
        SymbolTable parent = emptyTable();
        DslElementNode group = elem("Group");

        SymbolTable result = new SymbolTableBuilderImpl().build(group, parent, emptyRepo());

        assertSame(parent, result);
    }

    @Test
    void build_arrayWithoutIndexFlagReturnsParent() {
        SymbolTable parent = emptyTable();
        DslElementNode arrayNoFlag = elem("Array");

        SymbolTable result = new SymbolTableBuilderImpl().build(arrayNoFlag, parent, emptyRepo());

        assertSame(parent, result);
    }

    @Test
    void build_arrayWithEmptyIndexFlagReturnsParent() {
        SymbolTable parent = emptyTable();
        DslElementNode arrayEmptyFlag = indexedElement("Array", "");

        SymbolTable result = new SymbolTableBuilderImpl().build(arrayEmptyFlag, parent, emptyRepo());

        assertSame(parent, result);
    }

    @Test
    void build_nullElementNodeReturnsParent() {
        SymbolTable parent = emptyTable();

        SymbolTable result = new SymbolTableBuilderImpl().build(null, parent, emptyRepo());

        assertSame(parent, result);
    }

    @Test
    void build_indexFlagShadowsParentVarWithSameName() {
        VarDeclaration globalI = VarDeclaration.builder()
                .name("i").type(new DslNumberType()).isGlobal(true).astNode(null).expression(null).isConstAttr(false).build();
        SymbolTable parent = SymbolTable.builder().parent(null).declarations(new HashMap<>(Map.of("i", globalI))).build();
        DslElementNode array = indexedElement("Array", "i");

        SymbolTable child = new SymbolTableBuilderImpl().build(array, parent, emptyRepo());

        VarDeclaration localI = child.getDeclarations().get("i");
        assertNotNull(localI);
        assertFalse(localI.isGlobal());
        assertSame(array, localI.getAstNode());
        VarDeclaration parentI = child.getParent().getDeclarations().get("i");
        assertTrue(parentI.isGlobal());
    }

    // ---- helpers ----

    private static SymbolTable emptyTable() {
        return SymbolTable.builder().parent(null).declarations(Map.of()).build();
    }

    private static DslElementNode indexedElement(String tag, String indexFlag, DslElementNode... children) {
        return elemWithAttrs(tag, new DslAttributeNode[]{attr("indexFlag", indexFlag)}, children);
    }

    private static DslElementNode elemWithAttrs(String tag, DslAttributeNode[] attrs, DslElementNode... children) {
        DslElementNode e = new DslElementNode();
        e.setTagName(tag);
        e.setAttributes(new ArrayList<>(Arrays.asList(attrs)));
        e.setChildElements(new ArrayList<>(Arrays.asList(children)));
        e.setSelfClosing(false);
        e.setHasError(false);
        return e;
    }

    private static RuleRepository emptyRepo() {
        return new DefaultRuleRepository(Map.of(), Map.of(), Map.of());
    }

    private static RuleRepository repoWithGlobals(DslGlobalVar... globals) {
        Map<String, DslGlobalVar> map = new HashMap<>();
        for (DslGlobalVar g : globals) {
            map.put(g.getName(), g);
        }
        return new DefaultRuleRepository(Map.of(), map, Map.of());
    }

    private static DslFileNode file(DslElementNode root) {
        DslFileNode f = new DslFileNode();
        f.setRootElement(root);
        return f;
    }

    private static DslElementNode elem(String tag, DslElementNode... children) {
        DslElementNode e = new DslElementNode();
        e.setTagName(tag);
        e.setAttributes(new ArrayList<>());
        e.setChildElements(new ArrayList<>(Arrays.asList(children)));
        e.setSelfClosing(false);
        e.setHasError(false);
        return e;
    }

    private static DslElementNode var(String name, String type, DslAttributeNode... extra) {
        DslElementNode v = new DslElementNode();
        v.setTagName("Var");
        List<DslAttributeNode> attrs = new ArrayList<>();
        attrs.add(attr("name", name));
        if (type != null) {
            attrs.add(attr("type", type));
        }
        Collections.addAll(attrs, extra);
        v.setAttributes(attrs);
        v.setChildElements(new ArrayList<>());
        v.setSelfClosing(true);
        v.setHasError(false);
        return v;
    }

    private static DslAttributeNode attr(String name, String rawValue) {
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

    private static DslAttributeNode exprAttr(String name, String rawValue, ExpressionAstNode expr) {
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
}
