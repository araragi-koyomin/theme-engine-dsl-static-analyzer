package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;
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

public class VarRefAnalyzer implements DslAnalyzer {

    private static final String VAR_TAG = "Var";
    private static final String NAME_ATTR = "name";
    private static final String RULE_REF_001 = "SEM-REF-001";
    private static final String RULE_REF_003 = "SEM-REF-003";

    @Override
    public List<Diagnostic> analyze(DslAstNode element, DslContext context) {
        if (!(element instanceof DslElementNode elementNode)) {
            return Collections.emptyList();
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        collectUndefinedReferences(elementNode, context, diagnostics);
        detectDuplicateVarDeclaration(elementNode, context, diagnostics);
        return diagnostics;
    }

    private void collectUndefinedReferences(DslElementNode elementNode, DslContext context,
                                            List<Diagnostic> diagnostics) {
        SymbolTable symbolTable = context.getSymbolTable();
        if (symbolTable == null) {
            return;
        }
        for (DslAttributeNode attr : elementNode.getAttributes()) {
            DslAttributeValueNode value = attr.getValue();
            if (value == null) {
                continue;
            }
            Optional<ExpressionAstNode> exprOpt = value.getExpression();
            if (exprOpt.isEmpty() || !(exprOpt.get() instanceof ExpressionNode exprNode)) {
                continue;
            }
            List<ExpressionNode> references = new ArrayList<>();
            collectVarReferences(exprNode, references);
            for (ExpressionNode ref : references) {
                String varName = ref.getVariableName();
                if (varName == null || varName.isEmpty()) {
                    continue;
                }
                if (symbolTable.lookup(varName).isEmpty()) {
                    diagnostics.add(buildUndefinedReferenceDiagnostic(ref, elementNode, context));
                }
            }
        }
    }

    private void detectDuplicateVarDeclaration(DslElementNode elementNode, DslContext context,
                                               List<Diagnostic> diagnostics) {
        if (!VAR_TAG.equals(elementNode.getTagName())) {
            return;
        }
        SymbolTable symbolTable = context.getSymbolTable();
        if (symbolTable == null) {
            return;
        }
        String varName = getAttrValue(elementNode, NAME_ATTR);
        if (varName == null || varName.isEmpty()) {
            return;
        }
        SymbolTable globalTable = symbolTable.getGlobalTable();
        VarDeclaration effective = globalTable.getDeclarations().get(varName);
        if (effective == null) {
            return;
        }
        if (effective.getAstNode() != elementNode) {
            diagnostics.add(buildDuplicateVarDiagnostic(elementNode, varName, context));
        }
    }

    private void collectVarReferences(ExpressionNode node, List<ExpressionNode> references) {
        if (node == null) {
            return;
        }
        ExpressionKind kind = node.getKind();
        if (kind == ExpressionKind.VARIABLE_REF || kind == ExpressionKind.ARRAY_ACCESS) {
            references.add(node);
            if (node.getIndexExpression() != null) {
                collectVarReferences(node.getIndexExpression(), references);
            }
            return;
        }
        if (node.getChildren() != null) {
            for (ExpressionNode child : node.getChildren()) {
                collectVarReferences(child, references);
            }
        }
        if (node.getIndexExpression() != null) {
            collectVarReferences(node.getIndexExpression(), references);
        }
    }

    private Diagnostic buildUndefinedReferenceDiagnostic(ExpressionNode ref, DslElementNode hostNode,
                                                         DslContext context) {
        String docUrl = resolveDocUrl(context.getRuleRepository(), RULE_REF_001);
        String refText = ref.getPrefix() != null ? ref.getPrefix() + ref.getVariableName() : ref.getVariableName();
        int line = ref.getLine();
        int column = ref.getColumn();
        if (line == 0 && column == 0) {
            line = hostNode.getLine();
            column = hostNode.getColumn();
        }
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_REF_001)
                .message("引用未定义变量 " + refText)
                .filePath(context.getFilePath())
                .line(line)
                .column(column)
                .suggestedFixes(List.of("声明 Var name=\"" + ref.getVariableName() + "\""))
                .ruleDocUrl(docUrl)
                .build();
    }

    private Diagnostic buildDuplicateVarDiagnostic(DslElementNode varNode, String varName, DslContext context) {
        String docUrl = resolveDocUrl(context.getRuleRepository(), RULE_REF_003);
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_REF_003)
                .message("重复定义变量 " + varName)
                .filePath(context.getFilePath())
                .line(varNode.getLine())
                .column(varNode.getColumn())
                .suggestedFixes(List.of("移除重复的 Var 声明"))
                .ruleDocUrl(docUrl)
                .build();
    }

    private String resolveDocUrl(RuleRepository ruleRepo, String ruleId) {
        if (ruleRepo == null) {
            return null;
        }
        Optional<RuleSource> sourceOpt = ruleRepo.getRuleSource(ruleId);
        return sourceOpt.map(RuleSource::getDocUrl).orElse(null);
    }

    private String getAttrValue(DslElementNode elementNode, String attrName) {
        for (DslAttributeNode attr : elementNode.getAttributes()) {
            if (attrName.equals(attr.getName()) && attr.getValue() != null) {
                return attr.getValue().getRawValue();
            }
        }
        return null;
    }
}
