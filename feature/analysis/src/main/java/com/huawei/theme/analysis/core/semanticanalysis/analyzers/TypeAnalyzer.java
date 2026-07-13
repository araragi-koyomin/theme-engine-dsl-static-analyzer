package com.huawei.theme.analysis.core.semanticanalysis.analyzers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary;
import com.huawei.theme.analysis.core.expression.TypeInferenceEngine;
import com.huawei.theme.analysis.core.expression.model.FunctionParam;
import com.huawei.theme.analysis.core.expression.model.FunctionSignature;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;
import com.huawei.theme.analysis.core.semanticanalysis.DslAnalyzer;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.semanticanalysis.model.VarDeclaration;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.shared.type.DslArrayType;
import com.huawei.theme.analysis.core.shared.type.DslMixedType;
import com.huawei.theme.analysis.core.shared.type.DslNumberType;
import com.huawei.theme.analysis.core.shared.type.DslStringType;
import com.huawei.theme.analysis.core.shared.type.DslType;

public class TypeAnalyzer implements DslAnalyzer {

    private static final String TYPE_ATTR = "type";
    private static final String SIZE_ATTR = "size";
    private static final String RULE_TYPE_001 = "SEM-TYPE-001";
    private static final String RULE_TYPE_002 = "SEM-TYPE-002";
    private static final String RULE_TYPE_003 = "SEM-TYPE-003";
    private static final String RULE_ARR_001 = "SEM-ARR-001";
    private static final String RULE_VAR_004 = "SEM-VAR-004";

    @Override
    public List<Diagnostic> analyze(DslAstNode element, DslContext context) {
        if (!(element instanceof DslElementNode elementNode)) {
            return Collections.emptyList();
        }
        RuleRepository ruleRepo = context.getRuleRepository();
        if (ruleRepo == null) {
            return Collections.emptyList();
        }
        FunctionSignatureLibrary functionLibrary = ruleRepo.getFunctionSignatureLibrary();
        if (functionLibrary == null) {
            return Collections.emptyList();
        }
        TypeInferenceEngine engine = new TypeInferenceEngine(functionLibrary);

        List<Diagnostic> diagnostics = new ArrayList<>();
        boolean isVar = "Var".equals(elementNode.getTagName());
        for (DslAttributeNode attr : elementNode.getAttributes()) {
            if (isVar && "expression".equals(attr.getName())) {
                continue;
            }
            checkAttribute(elementNode, attr, engine, context, diagnostics);
        }

        if ("Var".equals(elementNode.getTagName())) {
            checkVarExpressionBody(elementNode, engine, context, functionLibrary, diagnostics);
        } else if (elementNode.getAttributes() != null) {
            for (DslAttributeNode attr : elementNode.getAttributes()) {
                checkArrayBounds(elementNode, attr, context, diagnostics);
            }
        }
        return diagnostics;
    }

    private void checkAttribute(DslElementNode elementNode, DslAttributeNode attr,
                                TypeInferenceEngine engine, DslContext context,
                                List<Diagnostic> diagnostics) {
        RuleRepository ruleRepo = context.getRuleRepository();
        if (ruleRepo == null || attr.getValue() == null) {
            return;
        }
        Optional<AttrTypeSpec> specOpt = ruleRepo.getAttrTypeSpec(elementNode.getTagName(), attr.getName());
        if (specOpt.isEmpty() || !specOpt.get().isSupportsExpression()) {
            return;
        }
        DslAttributeValueNode value = attr.getValue();
        Optional<ExpressionAstNode> exprOpt = value.getExpression();
        if (exprOpt.isEmpty() || !(exprOpt.get() instanceof ExpressionNode exprNode)) {
            return;
        }
        DslType expectedType = resolveExpectedType(specOpt.get(), elementNode);
        if (expectedType == null) {
            return;
        }
        SymbolTable symbolTable = context.getSymbolTable();
        DslType inferred = engine.inferType(exprNode, expectedType, symbolTable);
        if (inferred != null && !TypeInferenceEngine.typeEquals(inferred, expectedType)) {
            if (expectedType instanceof DslStringType && inferred instanceof DslNumberType) {
            } else {
                diagnostics.add(buildTypeMismatchDiagnostic(elementNode, attr, expectedType, inferred, context));
            }
        }
        checkFunctionCalls(exprNode, expectedType, engine, context, elementNode, diagnostics);
        checkRefVarExpressionErrors(exprNode, expectedType, symbolTable, context, elementNode, diagnostics);
        if (expectedType instanceof DslNumberType) {
            FunctionSignatureLibrary functionLibrary =
                    context.getRuleRepository() != null
                            ? context.getRuleRepository().getFunctionSignatureLibrary() : null;
            checkStringLiteralInNumExpr(exprNode, symbolTable, functionLibrary, context, elementNode,
                    true, diagnostics);
            checkIfelseBranchTypes(exprNode, expectedType, symbolTable, functionLibrary,
                    context, elementNode, diagnostics);
        }
    }

    private void checkFunctionCalls(ExpressionNode node, DslType expectedType, TypeInferenceEngine engine,
                                    DslContext context, DslElementNode elementNode,
                                    List<Diagnostic> diagnostics) {
        List<ExpressionNode> calls = new ArrayList<>();
        collectFunctionCalls(node, calls);
        FunctionSignatureLibrary functionLibrary = context.getRuleRepository().getFunctionSignatureLibrary();
        String expressionKind = expectedType.getName();
        for (ExpressionNode call : calls) {
            if ("ifelse".equals(call.getFunctionName())) {
                continue;
            }
            Optional<FunctionSignature> sigOpt = functionLibrary.getSignature(call.getFunctionName(), expressionKind);
            if (sigOpt.isEmpty()) {
                if (functionLibrary.getSignature(call.getFunctionName(), "number").isEmpty()
                        && functionLibrary.getSignature(call.getFunctionName(), "string").isEmpty()) {
                    continue;
                }
                Optional<FunctionSignature> altSig = functionLibrary.getSignature(call.getFunctionName(), "string");
                if (altSig.isEmpty()) {
                    altSig = functionLibrary.getSignature(call.getFunctionName(), "number");
                }
                if (altSig.isPresent()) {
                    checkFunctionParams(call, altSig.get(), engine, context, elementNode, diagnostics);
                }
                continue;
            }
            checkFunctionParams(call, sigOpt.get(), engine, context, elementNode, diagnostics);
        }
    }

    private void checkFunctionParams(ExpressionNode call, FunctionSignature sig, TypeInferenceEngine engine,
                                     DslContext context, DslElementNode elementNode,
                                     List<Diagnostic> diagnostics) {
        List<FunctionParam> params = sig.getParams();
        List<ExpressionNode> args = call.getChildren();
        if (args == null) {
            return;
        }
        int paramCount = params.size();
        if (paramCount > 0) {
            boolean hasVariadic = params.get(paramCount - 1).isVariadic();
            if (!hasVariadic && args.size() > paramCount) {
                diagnostics.add(buildArgCountMismatchDiagnostic(call, elementNode, paramCount, args.size(), context));
                return;
            }
        }
        SymbolTable symbolTable = context.getSymbolTable();
        for (int i = 0; i < args.size(); i++) {
            FunctionParam param = resolveParam(params, i);
            if (param == null || param.getType() == null) {
                continue;
            }
            DslType argType = engine.inferType(args.get(i), param.getType(), symbolTable);
            if (argType != null && !TypeInferenceEngine.typeEquals(argType, param.getType())) {
                diagnostics.add(buildParamMismatchDiagnostic(call, elementNode, i, param.getType(), argType, context));
            }
        }
    }

    private static FunctionParam resolveParam(List<FunctionParam> params, int index) {
        if (params.isEmpty()) {
            return null;
        }
        if (index < params.size()) {
            return params.get(index);
        }
        FunctionParam last = params.get(params.size() - 1);
        return last.isVariadic() ? last : null;
    }

    private void collectFunctionCalls(ExpressionNode node, List<ExpressionNode> calls) {
        if (node == null) {
            return;
        }
        if (node.getKind() == ExpressionKind.FUNCTION_CALL) {
            calls.add(node);
        }
        if (node.getChildren() != null) {
            for (ExpressionNode child : node.getChildren()) {
                collectFunctionCalls(child, calls);
            }
        }
        if (node.getIndexExpression() != null) {
            collectFunctionCalls(node.getIndexExpression(), calls);
        }
    }

    private static DslType resolveExpectedType(AttrTypeSpec spec, DslElementNode elementNode) {
        String kind = spec.getExpressionKind();
        if ("number".equals(kind)) {
            return new DslNumberType();
        }
        if ("string".equals(kind)) {
            return new DslStringType();
        }
        if ("auto".equals(kind)) {
            String typeAttr = getAttrValue(elementNode, TYPE_ATTR);
            return toDslType(typeAttr != null && !typeAttr.isEmpty() ? typeAttr : "number");
        }
        return null;
    }

    private static DslType toDslType(String type) {
        if (type == null || type.isEmpty()) {
            return null;
        }
        if (type.endsWith("[]")) {
            return DslArrayType.builder().baseType(type.substring(0, type.length() - 2)).build();
        }
        if ("number".equals(type)) {
            return new DslNumberType();
        }
        if ("string".equals(type)) {
            return new DslStringType();
        }
        return null;
    }

    private void checkVarExpressionBody(DslElementNode elementNode, TypeInferenceEngine engine,
                                         DslContext context, FunctionSignatureLibrary functionLibrary,
                                         List<Diagnostic> diagnostics) {
        String typeAttr = getAttrValue(elementNode, TYPE_ATTR);
        DslType varType = toDslType(typeAttr != null && !typeAttr.isEmpty() ? typeAttr : "number");
        if (varType == null) {
            return;
        }

        if (varType instanceof DslArrayType && getAttrValue(elementNode, SIZE_ATTR) == null
                && getAttrValue(elementNode, "values") == null) {
            diagnostics.add(buildArrayNoSizeDiagnostic(elementNode, context));
        }

        DslAttributeNode exprAttr = getAttrNode(elementNode, "expression");
        if (exprAttr == null || exprAttr.getValue() == null) {
            return;
        }

        Optional<ExpressionAstNode> exprOpt = exprAttr.getValue().getExpression();
        if (exprOpt.isEmpty() || !(exprOpt.get() instanceof ExpressionNode exprNode)) {
            return;
        }

        SymbolTable symbolTable = context.getSymbolTable();

        checkVarConstRefs(elementNode, exprNode, symbolTable, context, diagnostics);

        DslType exprType = inferExpressionType(exprNode, symbolTable, functionLibrary);
        if (exprType != null && !TypeInferenceEngine.typeEquals(exprType, varType)) {
            if (isSimpleLiteralExpression(exprNode)) {
                diagnostics.add(buildSimpleLiteralTypeMismatchDiagnostic(
                        elementNode, varType, exprType, context));
            } else {
                diagnostics.add(buildVarTypeMismatchDiagnostic(elementNode, varType, exprType, context));
            }
        } else if (exprType == null && hasIfelseMixedBranches(exprNode, symbolTable, functionLibrary)) {
            diagnostics.add(buildVarTypeMismatchDiagnostic(elementNode, varType, new DslMixedType(), context));
        }

        checkFunctionCalls(exprNode, varType, engine, context, elementNode, diagnostics);
        if (varType instanceof DslNumberType) {
            checkStringLiteralInNumExpr(exprNode, symbolTable, functionLibrary, context, elementNode,
                    true, diagnostics);
        }
    }

    private void checkVarConstRefs(DslElementNode elementNode, ExpressionNode exprNode,
                                    SymbolTable symbolTable, DslContext context,
                                    List<Diagnostic> diagnostics) {
        if (!hasConstAttr(elementNode)) {
            return;
        }
        List<ExpressionNode> refs = new ArrayList<>();
        collectVariableRefs(exprNode, refs);
        for (ExpressionNode ref : refs) {
            VarDeclaration decl = symbolTable != null
                    ? symbolTable.lookup(ref.getVariableName()).orElse(null) : null;
            if (decl != null && !decl.isConstAttr()) {
                diagnostics.add(buildVarConstMismatchDiagnostic(elementNode, ref, context));
            }
        }
    }

    private void collectVariableRefs(ExpressionNode node, List<ExpressionNode> refs) {
        if (node == null) {
            return;
        }
        if (node.getKind() == ExpressionKind.VARIABLE_REF && "#".equals(node.getPrefix())) {
            refs.add(node);
        }
        if (node.getChildren() != null) {
            for (ExpressionNode child : node.getChildren()) {
                collectVariableRefs(child, refs);
            }
        }
        if (node.getIndexExpression() != null) {
            collectVariableRefs(node.getIndexExpression(), refs);
        }
    }

    private DslType inferExpressionType(ExpressionNode node, SymbolTable symbolTable,
                                         FunctionSignatureLibrary functionLibrary) {
        if (node == null) {
            return null;
        }
        switch (node.getKind()) {
            case LITERAL:
                if (isNumericValue(node.getLiteralValue())) {
                    return new DslNumberType();
                }
                return new DslStringType();
            case VARIABLE_REF:
                if ("@".equals(node.getPrefix())) {
                    return new DslStringType();
                }
                if ("#".equals(node.getPrefix())) {
                    if (symbolTable != null) {
                        VarDeclaration decl = symbolTable.lookup(node.getVariableName()).orElse(null);
                        return decl != null ? decl.getType() : null;
                    }
                }
                return null;
            case ARRAY_ACCESS:
                if ("@".equals(node.getPrefix())) {
                    return new DslStringType();
                }
                if ("#".equals(node.getPrefix())) {
                    if (symbolTable != null) {
                        VarDeclaration decl = symbolTable.lookup(node.getVariableName()).orElse(null);
                        if (decl != null && decl.getType() instanceof DslArrayType) {
                            return toDslType(((DslArrayType) decl.getType()).getBaseType());
                        }
                    }
                }
                return null;
            case FUNCTION_CALL:
                if ("ifelse".equals(node.getFunctionName()) && node.getChildren() != null) {
                    return inferIfelseType(node, symbolTable, functionLibrary);
                }
                if (functionLibrary != null && node.getFunctionName() != null) {
                    FunctionSignature sig = functionLibrary.getSignature(node.getFunctionName(), "number").orElse(null);
                    if (sig == null) {
                        sig = functionLibrary.getSignature(node.getFunctionName(), "string").orElse(null);
                    }
                    if (sig != null) {
                        return sig.getReturnType();
                    }
                }
                return null;
            case BINARY_EXPR:
                if (node.getChildren() != null) {
                    for (ExpressionNode child : node.getChildren()) {
                        DslType childType = inferExpressionType(child, symbolTable, functionLibrary);
                        if (childType != null) {
                            return childType;
                        }
                    }
                }
                return null;
            case UNARY_EXPR:
                if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                    return inferExpressionType(node.getChildren().get(0), symbolTable, functionLibrary);
                }
                return null;
            case CONDITIONAL:
                if (node.getChildren() != null) {
                    for (ExpressionNode child : node.getChildren()) {
                        DslType childType = inferExpressionType(child, symbolTable, functionLibrary);
                        if (childType != null) {
                            return childType;
                        }
                    }
                }
                return null;
            default:
                return null;
        }
    }

    private DslType inferIfelseType(ExpressionNode node, SymbolTable symbolTable,
                                     FunctionSignatureLibrary functionLibrary) {
        List<ExpressionNode> children = node.getChildren();
        if (children == null || children.size() < 2) {
            return null;
        }
        DslType branchType = null;
        boolean hasConflict = false;
        for (int i = 1; i < children.size(); i++) {
            DslType childType = inferExpressionType(children.get(i), symbolTable, functionLibrary);
            if (childType != null) {
                if (branchType == null) {
                    branchType = childType;
                } else if (!TypeInferenceEngine.typeEquals(branchType, childType)) {
                    hasConflict = true;
                    branchType = null;
                    break;
                }
            }
        }
        if (hasConflict) {
            return new DslMixedType();
        }
        return branchType;
    }

    private boolean hasConstAttr(DslElementNode elementNode) {
        String constVal = getAttrValue(elementNode, "const");
        return "true".equals(constVal);
    }

    private boolean hasIfelseMixedBranches(ExpressionNode node, SymbolTable symbolTable,
                                            FunctionSignatureLibrary functionLibrary) {
        if (node == null || node.getKind() != ExpressionKind.FUNCTION_CALL
                || !"ifelse".equals(node.getFunctionName())) {
            return false;
        }
        List<ExpressionNode> children = node.getChildren();
        if (children == null || children.size() < 3) {
            return false;
        }
        DslType firstType = null;
        for (int i = 1; i < children.size(); i++) {
            DslType childType = inferExpressionType(children.get(i), symbolTable, functionLibrary);
            if (childType != null) {
                if (firstType == null) {
                    firstType = childType;
                } else if (!TypeInferenceEngine.typeEquals(firstType, childType)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSimpleLiteralExpression(ExpressionNode node) {
        if (node == null) {
            return false;
        }
        switch (node.getKind()) {
            case LITERAL:
                return true;
            case BINARY_EXPR:
            case UNARY_EXPR:
                if (node.getChildren() != null) {
                    for (ExpressionNode child : node.getChildren()) {
                        if (!isSimpleLiteralExpression(child)) {
                            return false;
                        }
                    }
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private void checkRefVarExpressionErrors(ExpressionNode node, DslType expectedType,
                                              SymbolTable symbolTable, DslContext context,
                                              DslElementNode elementNode, List<Diagnostic> diagnostics) {
        if (node == null) {
            return;
        }
        if (node.getKind() == ExpressionKind.VARIABLE_REF && "#".equals(node.getPrefix())) {
            checkSingleVarExprError(node, expectedType, symbolTable, context, elementNode, diagnostics);
        } else if (node.getKind() == ExpressionKind.ARRAY_ACCESS && "#".equals(node.getPrefix())) {
            checkSingleVarExprError(node, expectedType, symbolTable, context, elementNode, diagnostics);
        }
        if (node.getChildren() != null) {
            for (ExpressionNode child : node.getChildren()) {
                checkRefVarExpressionErrors(child, expectedType, symbolTable, context, elementNode, diagnostics);
            }
        }
        if (node.getIndexExpression() != null) {
            checkRefVarExpressionErrors(node.getIndexExpression(), expectedType, symbolTable, context,
                    elementNode, diagnostics);
        }
    }

    private void checkSingleVarExprError(ExpressionNode ref, DslType expectedType,
                                           SymbolTable symbolTable,
                                           DslContext context, DslElementNode elementNode, List<Diagnostic> diagnostics) {
        if (symbolTable == null) {
            return;
        }
        VarDeclaration decl = symbolTable.lookup(ref.getVariableName()).orElse(null);
        if (decl == null || decl.getAstNode() == null) {
            return;
        }
        if ("#".equals(ref.getPrefix()) && decl.getType() instanceof DslStringType) {
            if (expectedType == null || TypeInferenceEngine.typeEquals(decl.getType(), expectedType)) {
                diagnostics.add(buildHashPrefixOnStringVarDiagnostic(ref, elementNode, context));
            }
            return;
        }
        DslAttributeNode exprAttr = getAttrNode(decl.getAstNode(), "expression");
        if (exprAttr == null || exprAttr.getValue() == null) {
            return;
        }
        Optional<ExpressionAstNode> exprOpt = exprAttr.getValue().getExpression();
        if (exprOpt.isEmpty() || !(exprOpt.get() instanceof ExpressionNode declExpr)) {
            return;
        }
        DslType varDeclaredType = decl.getType();
        FunctionSignatureLibrary functionLibrary =
                context.getRuleRepository() != null
                        ? context.getRuleRepository().getFunctionSignatureLibrary() : null;
        DslType declExprType = inferExpressionType(declExpr, symbolTable, functionLibrary);
        if (declExprType != null && varDeclaredType != null
                && !TypeInferenceEngine.typeEquals(declExprType, varDeclaredType)) {
            diagnostics.add(buildVarRefTypeErrorDiagnostic(ref, elementNode, decl, context));
            return;
        }
        if (declExprType == null && hasIfelseMixedBranches(declExpr, symbolTable, functionLibrary)) {
            diagnostics.add(buildVarRefTypeErrorDiagnostic(ref, elementNode, decl, context));
            return;
        }
        if (functionLibrary != null && varDeclaredType != null) {
            List<Diagnostic> tempDiagnostics = new ArrayList<>();
            TypeInferenceEngine engine = new TypeInferenceEngine(functionLibrary);
            checkFunctionCalls(declExpr, varDeclaredType, engine, context, decl.getAstNode(), tempDiagnostics);
            if (!tempDiagnostics.isEmpty()) {
                diagnostics.add(buildVarRefTypeErrorDiagnostic(ref, elementNode, decl, context));
            }
        }
    }

    private void checkArrayBounds(DslElementNode elementNode, DslAttributeNode attr,
                                   DslContext context, List<Diagnostic> diagnostics) {
        if (attr.getValue() == null) {
            return;
        }
        Optional<ExpressionAstNode> exprOpt = attr.getValue().getExpression();
        if (exprOpt.isEmpty() || !(exprOpt.get() instanceof ExpressionNode exprNode)) {
            return;
        }
        collectArrayAccessesAndCheck(exprNode, context, elementNode, diagnostics);
    }

    private void collectArrayAccessesAndCheck(ExpressionNode node, DslContext context,
                                              DslElementNode elementNode, List<Diagnostic> diagnostics) {
        if (node == null) {
            return;
        }
        if (node.getKind() == ExpressionKind.ARRAY_ACCESS && "#".equals(node.getPrefix())) {
            checkSingleArrayAccess(node, context, elementNode, diagnostics);
        }
        if (node.getChildren() != null) {
            for (ExpressionNode child : node.getChildren()) {
                collectArrayAccessesAndCheck(child, context, elementNode, diagnostics);
            }
        }
        if (node.getIndexExpression() != null) {
            collectArrayAccessesAndCheck(node.getIndexExpression(), context, elementNode, diagnostics);
        }
    }

    private void checkSingleArrayAccess(ExpressionNode arrayAccess, DslContext context,
                                         DslElementNode elementNode, List<Diagnostic> diagnostics) {
        SymbolTable symbolTable = context.getSymbolTable();
        if (symbolTable == null) {
            return;
        }
        VarDeclaration decl = symbolTable.lookup(arrayAccess.getVariableName()).orElse(null);
        if (decl == null || decl.getAstNode() == null) {
            return;
        }
        String sizeStr = getAttrValue(decl.getAstNode(), SIZE_ATTR);
        if (sizeStr == null || sizeStr.isEmpty()) {
            diagnostics.add(buildArrayNoSizeAccessDiagnostic(arrayAccess, elementNode, context));
            return;
        }
        int size;
        try {
            size = Integer.parseInt(sizeStr);
        } catch (NumberFormatException e) {
            return;
        }
        ExpressionNode indexExpr = arrayAccess.getIndexExpression();
        if (indexExpr == null || indexExpr.getKind() != ExpressionKind.LITERAL) {
            return;
        }
        int index;
        try {
            index = Integer.parseInt(indexExpr.getLiteralValue());
        } catch (NumberFormatException e) {
            return;
        }
        if (index < 0 || index >= size) {
            diagnostics.add(buildArrayBoundsDiagnostic(arrayAccess, elementNode, index, size, context));
        }
    }

    private void checkStringLiteralInNumExpr(ExpressionNode node, SymbolTable symbolTable,
                                              FunctionSignatureLibrary functionLibrary, DslContext context,
                                              DslElementNode elementNode, boolean isTopLevel,
                                              List<Diagnostic> diagnostics) {
        if (node == null) {
            return;
        }
        if (node.getKind() == ExpressionKind.LITERAL && node.getLiteralValue() != null) {
            if (!isTopLevel && !isNumericValue(node.getLiteralValue())) {
                diagnostics.add(buildStringLiteralInNumDiagnostic(node, elementNode, context));
            }
            return;
        }
        if (node.getKind() == ExpressionKind.FUNCTION_CALL) {
            return;
        }
        if (node.getChildren() != null) {
            for (ExpressionNode child : node.getChildren()) {
                checkStringLiteralInNumExpr(child, symbolTable, functionLibrary, context, elementNode,
                        false, diagnostics);
            }
        }
        if (node.getIndexExpression() != null) {
            checkStringLiteralInNumExpr(node.getIndexExpression(), symbolTable, functionLibrary,
                    context, elementNode, false, diagnostics);
        }
    }

    private void checkIfelseBranchTypes(ExpressionNode node, DslType expectedType,
                                         SymbolTable symbolTable, FunctionSignatureLibrary functionLibrary,
                                         DslContext context, DslElementNode elementNode,
                                         List<Diagnostic> diagnostics) {
        if (node == null || node.getChildren() == null) {
            return;
        }
        if (node.getKind() == ExpressionKind.FUNCTION_CALL && "ifelse".equals(node.getFunctionName())) {
            List<ExpressionNode> children = node.getChildren();
            for (int i = 1; i < children.size(); i++) {
                DslType branchType = inferExpressionType(children.get(i), symbolTable, functionLibrary);
                if (branchType != null && !TypeInferenceEngine.typeEquals(branchType, expectedType)) {
                    diagnostics.add(buildIfelseBranchTypeDiagnostic(node, elementNode, expectedType,
                            branchType, context));
                }
            }
        }
        if (node.getChildren() != null) {
            for (ExpressionNode child : node.getChildren()) {
                checkIfelseBranchTypes(child, expectedType, symbolTable, functionLibrary,
                        context, elementNode, diagnostics);
            }
        }
    }

    private static DslAttributeNode getAttrNode(DslElementNode elementNode, String attrName) {
        for (DslAttributeNode attr : elementNode.getAttributes()) {
            if (attrName.equals(attr.getName())) {
                return attr;
            }
        }
        return null;
    }

    private static boolean isNumericValue(String value) {
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

    private Diagnostic buildVarTypeMismatchDiagnostic(DslElementNode elementNode, DslType expected,
                                                       DslType inferred, DslContext context) {
        String varName = getAttrValue(elementNode, "name");
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_TYPE_001)
                .message("类型不匹配，期望" + expected.getName() + "类型但表达式的返回值类型为"
                        + inferred.getName() + "（Var name=\"" + (varName != null ? varName : "") + "\"）")
                .filePath(context.getFilePath())
                .astNode(elementNode)
                .ruleDocUrl(resolveDocUrl(context, RULE_TYPE_001))
                .build();
    }

    private Diagnostic buildSimpleLiteralTypeMismatchDiagnostic(DslElementNode elementNode,
                                                                DslType expected, DslType inferred,
                                                                DslContext context) {
        String varName = getAttrValue(elementNode, "name");
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_TYPE_003)
                .message("属性值类型错误: Var type=" + expected.getName()
                        + " 但表达式返回 " + inferred.getName()
                        + "（Var name=\"" + (varName != null ? varName : "") + "\"）")
                .filePath(context.getFilePath())
                .astNode(elementNode)
                .ruleDocUrl(resolveDocUrl(context, RULE_TYPE_003))
                .build();
    }

    private Diagnostic buildVarConstMismatchDiagnostic(DslElementNode elementNode, ExpressionNode ref,
                                                        DslContext context) {
        String varName = getAttrValue(elementNode, "name");
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_TYPE_001)
                .message("常量Var引用了非常量变量 " + ref.getText()
                        + "（Var name=\"" + (varName != null ? varName : "") + "\"）")
                .filePath(context.getFilePath())
                .astNode(elementNode)
                .ruleDocUrl(resolveDocUrl(context, RULE_TYPE_001))
                .build();
    }

    private Diagnostic buildTypeMismatchDiagnostic(DslElementNode elementNode, DslAttributeNode attr,
                                                   DslType expected, DslType inferred, DslContext context) {
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_TYPE_001)
                .message("类型不匹配，期望" + expected.getName() + "实际" + inferred.getName()
                        + "（属性 " + attr.getName() + "）")
                .filePath(context.getFilePath())
                .astNode(elementNode)
                .ruleDocUrl(resolveDocUrl(context, RULE_TYPE_001))
                .build();
    }

    private Diagnostic buildFunctionNotApplicableDiagnostic(ExpressionNode call, DslElementNode elementNode,
                                                            DslType expected, DslContext context) {
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_TYPE_001)
                .message("函数 " + call.getFunctionName() + " 不适用于 " + expected.getName() + " 表达式")
                .filePath(context.getFilePath())
                .astNode(elementNode)
                .ruleDocUrl(resolveDocUrl(context, RULE_TYPE_001))
                .build();
    }

    private Diagnostic buildParamMismatchDiagnostic(ExpressionNode call, DslElementNode elementNode,
                                                     int paramIdx, DslType expected, DslType actual,
                                                     DslContext context) {
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_TYPE_002)
                .message("函数 " + call.getFunctionName() + " 参数 " + (paramIdx + 1)
                        + " 类型不匹配，期望" + expected.getName() + "实际" + actual.getName())
                .filePath(context.getFilePath())
                .astNode(elementNode)
                .ruleDocUrl(resolveDocUrl(context, RULE_TYPE_002))
                .build();
    }

    private Diagnostic buildArgCountMismatchDiagnostic(ExpressionNode call, DslElementNode elementNode,
                                                        int expected, int actual, DslContext context) {
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_TYPE_002)
                .message("函数 " + call.getFunctionName() + " 参数数量不匹配，期望" + expected + "个实际" + actual + "个")
                .filePath(context.getFilePath())
                .astNode(elementNode)
                .ruleDocUrl(resolveDocUrl(context, RULE_TYPE_002))
                .build();
    }

    private Diagnostic buildArrayNoSizeDiagnostic(DslElementNode elementNode, DslContext context) {
        String varName = getAttrValue(elementNode, "name");
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.WARNING)
                .ruleId(RULE_VAR_004)
                .message("数组类型变量缺少size属性声明（Var name=\"" + (varName != null ? varName : "") + "\"）")
                .filePath(context.getFilePath())
                .astNode(elementNode)
                .ruleDocUrl(resolveDocUrl(context, RULE_VAR_004))
                .build();
    }

    private Diagnostic buildArrayBoundsDiagnostic(ExpressionNode arrayAccess, DslElementNode elementNode,
                                                   int index, int size, DslContext context) {
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_ARR_001)
                .message("数组 " + arrayAccess.getPrefix() + arrayAccess.getVariableName()
                        + " 索引" + index + "越界（数组大小" + size + "）")
                .filePath(context.getFilePath())
                .astNode(elementNode)
                .ruleDocUrl(resolveDocUrl(context, RULE_ARR_001))
                .build();
    }

    private Diagnostic buildArrayNoSizeAccessDiagnostic(ExpressionNode arrayAccess, DslElementNode elementNode,
                                                         DslContext context) {
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_ARR_001)
                .message("数组 " + arrayAccess.getPrefix() + arrayAccess.getVariableName()
                        + " 未声明size属性，无法进行索引访问")
                .filePath(context.getFilePath())
                .astNode(elementNode)
                .ruleDocUrl(resolveDocUrl(context, RULE_ARR_001))
                .build();
    }

    private Diagnostic buildVarRefTypeErrorDiagnostic(ExpressionNode ref, DslElementNode elementNode,
                                                       VarDeclaration decl, DslContext context) {
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_TYPE_001)
                .message("类型不匹配，期望" + decl.getType().getName() + "类型但变量" + ref.getText()
                        + "的表达式返回值类型不匹配（属性 " + ref.getText() + "）")
                .filePath(context.getFilePath())
                .astNode(elementNode)
                .ruleDocUrl(resolveDocUrl(context, RULE_TYPE_001))
                .build();
    }

    private Diagnostic buildHashPrefixOnStringVarDiagnostic(ExpressionNode ref,
                                                            DslElementNode elementNode, DslContext context) {
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_TYPE_001)
                .message("类型不匹配，" + ref.getText() + " 是 string 类型但以数值访问前缀 # 引用")
                .filePath(context.getFilePath())
                .astNode(elementNode)
                .ruleDocUrl(resolveDocUrl(context, RULE_TYPE_001))
                .build();
    }

    private Diagnostic buildStringLiteralInNumDiagnostic(ExpressionNode literal, DslElementNode elementNode,
                                                          DslContext context) {
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_TYPE_003)
                .message("数值表达式上下文中包含字符串字面量 " + literal.getText())
                .filePath(context.getFilePath())
                .astNode(elementNode)
                .ruleDocUrl(resolveDocUrl(context, RULE_TYPE_003))
                .build();
    }

    private Diagnostic buildIfelseBranchTypeDiagnostic(ExpressionNode call, DslElementNode elementNode,
                                                        DslType expected, DslType actual,
                                                        DslContext context) {
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_TYPE_001)
                .message("ifelse分支类型不匹配，期望" + expected.getName() + "类型但分支返回"
                        + actual.getName() + "类型")
                .filePath(context.getFilePath())
                .astNode(elementNode)
                .ruleDocUrl(resolveDocUrl(context, RULE_TYPE_001))
                .build();
    }

    private static String resolveDocUrl(DslContext context, String ruleId) {
        RuleRepository ruleRepo = context.getRuleRepository();
        if (ruleRepo == null) {
            return null;
        }
        Optional<RuleSource> sourceOpt = ruleRepo.getRuleSource(ruleId);
        return sourceOpt.map(RuleSource::getDocUrl).orElse(null);
    }

    private static String getAttrValue(DslElementNode elementNode, String attrName) {
        for (DslAttributeNode attr : elementNode.getAttributes()) {
            if (attrName.equals(attr.getName()) && attr.getValue() != null) {
                return attr.getValue().getRawValue();
            }
        }
        return null;
    }
}
