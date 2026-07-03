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
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.shared.type.DslArrayType;
import com.huawei.theme.analysis.core.shared.type.DslNumberType;
import com.huawei.theme.analysis.core.shared.type.DslStringType;
import com.huawei.theme.analysis.core.shared.type.DslType;

public class TypeAnalyzer implements DslAnalyzer {

    private static final String TYPE_ATTR = "type";
    private static final String RULE_TYPE_001 = "SEM-TYPE-001";
    private static final String RULE_TYPE_002 = "SEM-TYPE-002";

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
        for (DslAttributeNode attr : elementNode.getAttributes()) {
            checkAttribute(elementNode, attr, engine, context, diagnostics);
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
            diagnostics.add(buildTypeMismatchDiagnostic(elementNode, attr, expectedType, inferred, context));
        }
        checkFunctionCalls(exprNode, expectedType, engine, context, elementNode, diagnostics);
    }

    private void checkFunctionCalls(ExpressionNode node, DslType expectedType, TypeInferenceEngine engine,
                                    DslContext context, DslElementNode elementNode,
                                    List<Diagnostic> diagnostics) {
        List<ExpressionNode> calls = new ArrayList<>();
        collectFunctionCalls(node, calls);
        FunctionSignatureLibrary functionLibrary = context.getRuleRepository().getFunctionSignatureLibrary();
        String expressionKind = expectedType.getName();
        for (ExpressionNode call : calls) {
            Optional<FunctionSignature> sigOpt = functionLibrary.getSignature(call.getFunctionName(), expressionKind);
            if (sigOpt.isEmpty()) {
                diagnostics.add(buildFunctionNotApplicableDiagnostic(call, elementNode, expectedType, context));
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
