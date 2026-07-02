package com.huawei.theme.analysis.core.expression;

import com.huawei.theme.analysis.core.expression.model.FunctionSignature;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.semanticanalysis.model.VarDeclaration;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;
import com.huawei.theme.analysis.core.shared.type.DslArrayType;
import com.huawei.theme.analysis.core.shared.type.DslNumberType;
import com.huawei.theme.analysis.core.shared.type.DslStringType;
import com.huawei.theme.analysis.core.shared.type.DslType;

public class TypeInferenceEngine {

    private final FunctionSignatureLibrary functionLibrary;

    public TypeInferenceEngine(FunctionSignatureLibrary functionLibrary) {
        this.functionLibrary = functionLibrary;
    }

    public DslType inferType(ExpressionNode node, DslType expectedContext, SymbolTable symbolTable) {
        if (node == null) {
            return null;
        }
        ExpressionKind kind = node.getKind();
        switch (kind) {
            case LITERAL:
                return inferLiteral(node, expectedContext);
            case VARIABLE_REF:
                return inferVariableRef(node, symbolTable);
            case ARRAY_ACCESS:
                return inferArrayAccess(node, symbolTable);
            case FUNCTION_CALL:
                return inferFunctionCall(node, expectedContext);
            case BINARY_EXPR:
                return inferBinaryExpr(node, expectedContext, symbolTable);
            case UNARY_EXPR:
                return inferUnaryExpr(node, expectedContext, symbolTable);
            default:
                return null;
        }
    }

    private DslType inferLiteral(ExpressionNode node, DslType expectedContext) {
        if (expectedContext instanceof DslStringType) {
            return new DslStringType();
        }
        if (isNumeric(node.getLiteralValue())) {
            return new DslNumberType();
        }
        return new DslStringType();
    }

    private DslType inferVariableRef(ExpressionNode node, SymbolTable symbolTable) {
        if (symbolTable == null) {
            return null;
        }
        String prefix = node.getPrefix();
        if ("@".equals(prefix)) {
            return new DslStringType();
        }
        if ("#".equals(prefix)) {
            VarDeclaration decl = symbolTable.lookup(node.getVariableName()).orElse(null);
            return decl != null ? decl.getType() : null;
        }
        return null;
    }

    private DslType inferArrayAccess(ExpressionNode node, SymbolTable symbolTable) {
        if (symbolTable == null) {
            return null;
        }
        String prefix = node.getPrefix();
        if ("@".equals(prefix)) {
            return new DslStringType();
        }
        if ("#".equals(prefix)) {
            VarDeclaration decl = symbolTable.lookup(node.getVariableName()).orElse(null);
            if (decl == null || decl.getType() == null) {
                return null;
            }
            if (decl.getType() instanceof DslArrayType) {
                return toDslType(((DslArrayType) decl.getType()).getBaseType());
            }
            return null;
        }
        return null;
    }

    private DslType inferFunctionCall(ExpressionNode node, DslType expectedContext) {
        if (functionLibrary == null || node.getFunctionName() == null) {
            return null;
        }
        String expressionKind = expectedContext != null ? expectedContext.getName() : "number";
        FunctionSignature sig = functionLibrary.getSignature(node.getFunctionName(), expressionKind).orElse(null);
        return sig != null ? sig.getReturnType() : null;
    }

    private DslType inferBinaryExpr(ExpressionNode node, DslType expectedContext, SymbolTable symbolTable) {
        if (node.getChildren() != null) {
            for (ExpressionNode child : node.getChildren()) {
                inferType(child, expectedContext, symbolTable);
            }
        }
        return expectedContext;
    }

    private DslType inferUnaryExpr(ExpressionNode node, DslType expectedContext, SymbolTable symbolTable) {
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            inferType(node.getChildren().get(0), expectedContext, symbolTable);
        }
        return expectedContext;
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static DslType toDslType(String typeName) {
        if ("number".equals(typeName)) {
            return new DslNumberType();
        }
        if ("string".equals(typeName)) {
            return new DslStringType();
        }
        return null;
    }

    public static boolean typeEquals(DslType a, DslType b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.getName().equals(b.getName());
    }
}
