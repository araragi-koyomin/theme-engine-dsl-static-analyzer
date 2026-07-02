package com.huawei.theme.analysis.core.semanticanalysis.analyzers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
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

public class VarRefAnalyzer implements DslAnalyzer {

    private static final String VAR_TAG = "Var";
    private static final String COMMAND_TAG = "Command";
    private static final String NAME_ATTR = "name";
    private static final String TARGET_ATTR = "target";
    private static final String RULE_REF_001 = "SEM-REF-001";
    private static final String RULE_REF_002 = "SEM-REF-002";
    private static final String RULE_REF_003 = "SEM-REF-003";
    private static final String ELEMENT_SCOPE = "element";
    private static final Set<String> ALLOWED_TARGET_PROPERTIES = Set.of("visibility", "animation");

    @Override
    public List<Diagnostic> analyze(DslAstNode element, DslContext context) {
        if (!(element instanceof DslElementNode elementNode)) {
            return Collections.emptyList();
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<String, Pattern> elementTemplates = compileElementTemplates(context.getRuleRepository());
        collectUndefinedReferences(elementNode, context, elementTemplates, diagnostics);
        detectCommandTargetRef(elementNode, context, diagnostics);
        detectDuplicateVarDeclaration(elementNode, context, diagnostics);
        return diagnostics;
    }

    private void collectUndefinedReferences(DslElementNode elementNode, DslContext context,
                                            Map<String, Pattern> elementTemplates,
                                            List<Diagnostic> diagnostics) {
        SymbolTable symbolTable = context.getSymbolTable();
        if (symbolTable == null) {
            return;
        }
        Set<String> elementNames = symbolTable.getGlobalTable().getElementNames();
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
                String elementName = matchTemplate(varName, elementTemplates);
                if (elementName != null) {
                    if (!elementNames.contains(elementName)) {
                        diagnostics.add(buildUndefinedElementRefDiagnostic(ref, elementName, elementNode, context));
                    }
                } else if (symbolTable.lookup(varName).isEmpty()) {
                    diagnostics.add(buildUndefinedReferenceDiagnostic(ref, elementNode, context));
                }
            }
        }
    }

    private void detectCommandTargetRef(DslElementNode elementNode, DslContext context,
                                        List<Diagnostic> diagnostics) {
        if (!COMMAND_TAG.equals(elementNode.getTagName())) {
            return;
        }
        SymbolTable symbolTable = context.getSymbolTable();
        if (symbolTable == null) {
            return;
        }
        String target = getAttrValue(elementNode, TARGET_ATTR);
        if (target == null || target.isEmpty()) {
            return;
        }
        int dot = target.indexOf('.');
        if (dot <= 0 || dot == target.length() - 1) {
            return;
        }
        if (target.indexOf('.', dot + 1) >= 0) {
            return;
        }
        String elemName = target.substring(0, dot);
        String property = target.substring(dot + 1);
        Set<String> elementNames = symbolTable.getGlobalTable().getElementNames();
        if (!elementNames.contains(elemName)) {
            diagnostics.add(buildCommandTargetNameDiagnostic(elementNode, elemName, context));
        }
        if (!ALLOWED_TARGET_PROPERTIES.contains(property)) {
            diagnostics.add(buildCommandTargetPropertyDiagnostic(elementNode, property, context));
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

    private static Map<String, Pattern> compileElementTemplates(RuleRepository ruleRepo) {
        if (ruleRepo == null) {
            return Collections.emptyMap();
        }
        Map<String, Pattern> compiled = new HashMap<>();
        for (DslGlobalVar var : ruleRepo.getAllGlobalVars()) {
            if (var.getName() == null || !ELEMENT_SCOPE.equals(var.getScope())) {
                continue;
            }
            if (var.getName().indexOf('{') < 0) {
                continue;
            }
            compiled.put(var.getName(), Pattern.compile(buildTemplateRegex(var.getName())));
        }
        return compiled;
    }

    private static String buildTemplateRegex(String template) {
        StringBuilder sb = new StringBuilder("^");
        int i = 0;
        int segStart = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{') {
                if (i > segStart) {
                    sb.append(Pattern.quote(template.substring(segStart, i)));
                }
                int end = template.indexOf('}', i);
                if (end > 0) {
                    sb.append("(.+?)");
                    i = end + 1;
                    segStart = i;
                    continue;
                }
            }
            i++;
        }
        if (i > segStart) {
            sb.append(Pattern.quote(template.substring(segStart, i)));
        }
        sb.append("$");
        return sb.toString();
    }

    private static String matchTemplate(String varName, Map<String, Pattern> elementTemplates) {
        for (Pattern pattern : elementTemplates.values()) {
            Matcher m = pattern.matcher(varName);
            if (m.matches()) {
                return m.group(1);
            }
        }
        return null;
    }

    private Diagnostic buildUndefinedReferenceDiagnostic(ExpressionNode ref, DslElementNode hostNode,
                                                         DslContext context) {
        String docUrl = resolveDocUrl(context, RULE_REF_001);
        String refText = ref.getPrefix() != null ? ref.getPrefix() + ref.getVariableName() : ref.getVariableName();
        int line = ref.getLine();
        int column = ref.getColumn();
        int endLine = 0;
        int endColumn = 0;
        if (line == 0 && column == 0) {
            line = hostNode.getLine();
            column = hostNode.getColumn();
            endLine = hostNode.getEndLine();
            endColumn = hostNode.getEndColumn();
        }
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_REF_001)
                .message("引用未定义变量 " + refText)
                .filePath(context.getFilePath())
                .line(line)
                .column(column)
                .endLine(endLine)
                .endColumn(endColumn)
                .suggestedFixes(List.of("声明 Var name=\"" + ref.getVariableName() + "\""))
                .ruleDocUrl(docUrl)
                .build();
    }

    private Diagnostic buildUndefinedElementRefDiagnostic(ExpressionNode ref, String elementName,
                                                          DslElementNode hostNode, DslContext context) {
        String docUrl = resolveDocUrl(context, RULE_REF_002);
        int line = ref.getLine();
        int column = ref.getColumn();
        int endLine = 0;
        int endColumn = 0;
        if (line == 0 && column == 0) {
            line = hostNode.getLine();
            column = hostNode.getColumn();
            endLine = hostNode.getEndLine();
            endColumn = hostNode.getEndColumn();
        }
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_REF_002)
                .message("引用未定义元素 " + elementName)
                .filePath(context.getFilePath())
                .line(line)
                .column(column)
                .endLine(endLine)
                .endColumn(endColumn)
                .suggestedFixes(List.of("声明带 name=\"" + elementName + "\" 的元素"))
                .ruleDocUrl(docUrl)
                .build();
    }

    private Diagnostic buildCommandTargetNameDiagnostic(DslElementNode cmdNode, String elemName,
                                                        DslContext context) {
        String docUrl = resolveDocUrl(context, RULE_REF_002);
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_REF_002)
                .message("Command target 引用未定义元素 " + elemName)
                .filePath(context.getFilePath())
                .positionFrom(cmdNode)
                .suggestedFixes(List.of("声明元素 name=\"" + elemName + "\""))
                .ruleDocUrl(docUrl)
                .build();
    }

    private Diagnostic buildCommandTargetPropertyDiagnostic(DslElementNode cmdNode, String property,
                                                            DslContext context) {
        String docUrl = resolveDocUrl(context, RULE_REF_002);
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_REF_002)
                .message("Command target 属性 '" + property + "' 不合法，合法值: visibility, animation")
                .filePath(context.getFilePath())
                .positionFrom(cmdNode)
                .suggestedFixes(List.of("修改 target 属性为 name.visibility 或 name.animation"))
                .ruleDocUrl(docUrl)
                .build();
    }

    private Diagnostic buildDuplicateVarDiagnostic(DslElementNode varNode, String varName, DslContext context) {
        String docUrl = resolveDocUrl(context, RULE_REF_003);
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_REF_003)
                .message("重复定义变量 " + varName)
                .filePath(context.getFilePath())
                .positionFrom(varNode)
                .suggestedFixes(List.of("移除重复的 Var 声明"))
                .ruleDocUrl(docUrl)
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

    private String getAttrValue(DslElementNode elementNode, String attrName) {
        for (DslAttributeNode attr : elementNode.getAttributes()) {
            if (attrName.equals(attr.getName()) && attr.getValue() != null) {
                return attr.getValue().getRawValue();
            }
        }
        return null;
    }
}
