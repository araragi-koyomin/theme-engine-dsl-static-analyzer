package com.huawei.theme.analysis.core.expression;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.expression.model.FunctionParam;
import com.huawei.theme.analysis.core.expression.model.FunctionSignature;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.semanticanalysis.model.VarDeclaration;
import com.huawei.theme.analysis.core.shared.type.DslArrayType;
import com.huawei.theme.analysis.core.shared.type.DslNumberType;
import com.huawei.theme.analysis.core.shared.type.DslStringType;
import com.huawei.theme.analysis.core.shared.type.DslType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeInferenceEngineTest {

    private final TypeInferenceEngine engine = new TypeInferenceEngine(stubLibrary());

    @Test
    void numberLiteralInNumberContextReturnsNumber() {
        ExpressionNode node = ExpressionNode.literal("42", "42", 1, 0);
        DslType result = engine.inferType(node, new DslNumberType(), emptyTable());
        assertEquals("number", result.getName());
    }

    @Test
    void numberLiteralInStringContextReturnsNumber() {
        ExpressionNode node = ExpressionNode.literal("42", "42", 1, 0);
        DslType result = engine.inferType(node, new DslStringType(), emptyTable());
        assertEquals("number", result.getName());
    }

    @Test
    void stringLiteralInNumberContextReturnsString() {
        ExpressionNode node = ExpressionNode.literal("hello", "'hello'", 1, 0);
        DslType result = engine.inferType(node, new DslNumberType(), emptyTable());
        assertEquals("string", result.getName());
    }

    @Test
    void hashNumericVarReturnsDeclaredType() {
        ExpressionNode node = ExpressionNode.variableRef("#", "n", "#n", 1, 0);
        DslType result = engine.inferType(node, new DslNumberType(), table(varDecl("n", new DslNumberType())));
        assertEquals("number", result.getName());
    }

    @Test
    void hashStringVarReturnsDeclaredStringType() {
        ExpressionNode node = ExpressionNode.variableRef("#", "s", "#s", 1, 0);
        DslType result = engine.inferType(node, new DslNumberType(), table(varDecl("s", new DslStringType())));
        assertEquals("string", result.getName());
    }

    @Test
    void hashUndefinedVarReturnsNull() {
        ExpressionNode node = ExpressionNode.variableRef("#", "missing", "#missing", 1, 0);
        DslType result = engine.inferType(node, new DslNumberType(), emptyTable());
        assertNull(result);
    }

    @Test
    void atVarAlwaysReturnsString() {
        ExpressionNode node = ExpressionNode.variableRef("@", "n", "@n", 1, 0);
        DslType result = engine.inferType(node, new DslStringType(), table(varDecl("n", new DslNumberType())));
        assertEquals("string", result.getName());
    }

    @Test
    void hashArrayAccessReturnsBaseType() {
        ExpressionNode index = ExpressionNode.literal("0", "0", 1, 0);
        ExpressionNode node = ExpressionNode.arrayAccess("#", "arr", index, "#arr[0]", 1, 0);
        DslType result = engine.inferType(node, new DslNumberType(),
                table(varDecl("arr", DslArrayType.builder().baseType("number").build())));
        assertEquals("number", result.getName());
    }

    @Test
    void functionCallReturnsReturnType() {
        ExpressionNode arg = ExpressionNode.variableRef("#", "x", "#x", 1, 0);
        ExpressionNode node = ExpressionNode.functionCall("sin", List.of(arg), "sin(#x)", 1, 0);
        DslType result = engine.inferType(node, new DslNumberType(), table(varDecl("x", new DslNumberType())));
        assertEquals("number", result.getName());
    }

    @Test
    void functionNotInContextReturnsNull() {
        ExpressionNode node = ExpressionNode.functionCall("sin", List.of(), "sin()", 1, 0);
        DslType result = engine.inferType(node, new DslStringType(), emptyTable());
        assertNull(result);
    }

    @Test
    void binaryExprReturnsExpectedContext() {
        ExpressionNode left = ExpressionNode.literal("1", "1", 1, 0);
        ExpressionNode right = ExpressionNode.literal("2", "2", 1, 0);
        ExpressionNode node = ExpressionNode.binaryExpr("+", left, right, "1+2", 1, 0);
        DslType result = engine.inferType(node, new DslStringType(), emptyTable());
        assertEquals("string", result.getName());
    }

    @Test
    void typeEqualsComparesByName() {
        assertTrue(TypeInferenceEngine.typeEquals(new DslNumberType(), new DslNumberType()));
        assertTrue(TypeInferenceEngine.typeEquals(new DslStringType(), new DslStringType()));
    }

    // ---- helpers ----

    private static VarDeclaration varDecl(String name, DslType type) {
        return VarDeclaration.builder()
                .name(name).type(type).expression(null).isConstAttr(false).isGlobal(false).astNode(null)
                .build();
    }

    private static SymbolTable table(VarDeclaration... decls) {
        Map<String, VarDeclaration> map = new java.util.HashMap<>();
        for (VarDeclaration d : decls) {
            map.put(d.getName(), d);
        }
        return SymbolTable.builder().parent(null).declarations(map).build();
    }

    private static SymbolTable emptyTable() {
        return SymbolTable.builder().parent(null).declarations(Map.of()).build();
    }

    private static FunctionSignatureLibrary stubLibrary() {
        FunctionSignature sin = FunctionSignature.builder()
                .name("sin")
                .params(List.of(FunctionParam.builder().name("x").type(new DslNumberType()).isVariadic(false).build()))
                .returnType(new DslNumberType())
                .expressionKind("number")
                .build();
        FunctionSignature substr = FunctionSignature.builder()
                .name("substr")
                .params(List.of(
                        FunctionParam.builder().name("str").type(new DslStringType()).isVariadic(false).build(),
                        FunctionParam.builder().name("pos").type(new DslNumberType()).isVariadic(false).build(),
                        FunctionParam.builder().name("len").type(new DslNumberType()).isVariadic(false).build()))
                .returnType(new DslStringType())
                .expressionKind("string")
                .build();
        return new StubFunctionLibrary(Map.of(
                "sin:number", sin,
                "substr:string", substr
        ));
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
}
