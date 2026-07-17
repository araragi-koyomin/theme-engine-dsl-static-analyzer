package com.huawei.theme.analysis.core.expression;

import com.huawei.theme.analysis.core.expression.model.FunctionSignature;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.semanticanalysis.model.VarDeclaration;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;
import com.huawei.theme.analysis.core.shared.type.DslArrayType;
import com.huawei.theme.analysis.core.shared.type.DslNumberType;
import com.huawei.theme.analysis.core.shared.type.DslStringType;
import com.huawei.theme.analysis.core.shared.type.DslType;
import com.huawei.theme.analysis.core.shared.type.DslUndefinedType;
import com.huawei.theme.analysis.core.shared.type.DslUnknownType;

public class TypeInferenceEngine {

    private final FunctionSignatureLibrary functionLibrary;

    public TypeInferenceEngine(FunctionSignatureLibrary functionLibrary) {
        this.functionLibrary = functionLibrary;
    }

    public DslType inferType(ExpressionNode node, DslType expectedContext, SymbolTable symbolTable) {
        if (node == null) {
            return new DslUnknownType();
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
                if ("ifelse".equals(node.getFunctionName())) {
                    return inferIfelseType(node, expectedContext, symbolTable);
                }
                return inferFunctionCall(node, expectedContext);
            case BINARY_EXPR:
                return inferBinaryExpr(node, expectedContext, symbolTable);
            case UNARY_EXPR:
                return inferUnaryExpr(node, expectedContext, symbolTable);
            case CONDITIONAL:
                return inferIfelseType(node, expectedContext, symbolTable);
            default:
                return new DslUnknownType();
        }
    }

    private DslType inferLiteral(ExpressionNode node, DslType expectedContext) {
        if (node.getText() != null && node.getText().startsWith("'") && node.getText().endsWith("'")) {
            return new DslStringType();
        }
        if (isNumeric(node.getLiteralValue())) {
            return new DslNumberType();
        }
        return new DslStringType();
    }

    private DslType inferVariableRef(ExpressionNode node, SymbolTable symbolTable) {
        String prefix = node.getPrefix();
        if ("@".equals(prefix)) {
            return new DslStringType();
        }
        if ("#".equals(prefix)) {
            if (symbolTable == null) {
                return new DslUnknownType();
            }
            VarDeclaration decl = symbolTable.lookup(node.getVariableName()).orElse(null);
            return decl != null ? decl.getType() : new DslUnknownType();
        }
        return new DslUnknownType();
    }

    private DslType inferArrayAccess(ExpressionNode node, SymbolTable symbolTable) {
        String prefix = node.getPrefix();
        if ("@".equals(prefix)) {
            return new DslStringType();
        }
        if ("#".equals(prefix)) {
            if (symbolTable == null) {
                return new DslUnknownType();
            }
            VarDeclaration decl = symbolTable.lookup(node.getVariableName()).orElse(null);
            if (decl == null || decl.getType() == null) {
                return new DslUnknownType();
            }
            if (decl.getType() instanceof DslArrayType) {
                DslType base = toDslType(((DslArrayType) decl.getType()).getBaseType());
                return base != null ? base : new DslUnknownType();
            }
            return new DslUnknownType();
        }
        return new DslUnknownType();
    }

    private DslType inferFunctionCall(ExpressionNode node, DslType expectedContext) {
        if (functionLibrary == null || node.getFunctionName() == null) {
            return new DslUnknownType();
        }
        DslType numberReturn = functionLibrary.getSignature(node.getFunctionName(), "number")
                .map(FunctionSignature::getReturnType).orElse(null);
        DslType stringReturn = functionLibrary.getSignature(node.getFunctionName(), "string")
                .map(FunctionSignature::getReturnType).orElse(null);
        if (numberReturn == null && stringReturn == null) {
            return new DslUnknownType();
        }
        if (numberReturn == null) {
            return stringReturn;
        }
        if (stringReturn == null) {
            return numberReturn;
        }
        if (typeEquals(numberReturn, stringReturn)) {
            return numberReturn;
        }
        return new DslUnknownType();
    }

    private DslType inferBinaryExpr(ExpressionNode node, DslType expectedContext, SymbolTable symbolTable) {
        if (node.getChildren() == null || node.getChildren().size() < 2) {
            return new DslUndefinedType();
        }
        DslType left = inferType(node.getChildren().get(0), expectedContext, symbolTable);
        DslType right = inferType(node.getChildren().get(1), expectedContext, symbolTable);
        return inferBinaryResult(left, right, node.getOperator());
    }

    private DslType inferBinaryResult(DslType left, DslType right, String op) {
        if (left == null || right == null) {
            return new DslUndefinedType();
        }
        if (left instanceof DslUndefinedType || right instanceof DslUndefinedType) {
            return new DslUndefinedType();
        }
        if (left instanceof DslUnknownType || right instanceof DslUnknownType) {
            return new DslUnknownType();
        }
        boolean leftNumber = left instanceof DslNumberType;
        boolean rightNumber = right instanceof DslNumberType;
        boolean leftString = left instanceof DslStringType;
        boolean rightString = right instanceof DslStringType;
        if ("+".equals(op)) {
            if (leftNumber && rightNumber) {
                return new DslNumberType();
            }
            if (leftString || rightString) {
                return new DslStringType();
            }
            return new DslUndefinedType();
        }
        if (leftNumber && rightNumber) {
            return new DslNumberType();
        }
        if (leftString || rightString) {
            return new DslUndefinedType();
        }
        return new DslUndefinedType();
    }

    private DslType inferUnaryExpr(ExpressionNode node, DslType expectedContext, SymbolTable symbolTable) {
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            return new DslUndefinedType();
        }
        DslType operand = inferType(node.getChildren().get(0), expectedContext, symbolTable);
        if (operand == null) {
            return new DslUndefinedType();
        }
        if (operand instanceof DslUndefinedType) {
            return new DslUndefinedType();
        }
        if (operand instanceof DslUnknownType) {
            return new DslUnknownType();
        }
        if (operand instanceof DslNumberType) {
            return new DslNumberType();
        }
        return new DslUndefinedType();
    }

    private DslType inferIfelseType(ExpressionNode node, DslType expectedContext, SymbolTable symbolTable) {
        if (node.getChildren() == null || node.getChildren().size() < 2) {
            return new DslUnknownType();
        }
        DslType branchType = null;
        for (int i = 1; i < node.getChildren().size(); i++) {
            DslType childType = inferType(node.getChildren().get(i), expectedContext, symbolTable);
            if (childType == null) {
                continue;
            }
            if (childType instanceof DslUndefinedType) {
                return new DslUnknownType();
            }
            if (branchType == null) {
                branchType = childType;
            } else if (!typeEquals(branchType, childType)) {
                return new DslUnknownType();
            }
        }
        return branchType != null ? branchType : new DslUnknownType();
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
