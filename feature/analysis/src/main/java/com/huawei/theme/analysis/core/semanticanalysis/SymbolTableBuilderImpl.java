package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.semanticanalysis.model.VarDeclaration;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.type.DslArrayType;
import com.huawei.theme.analysis.core.shared.type.DslNumberType;
import com.huawei.theme.analysis.core.shared.type.DslStringType;
import com.huawei.theme.analysis.core.shared.type.DslType;

public class SymbolTableBuilderImpl implements SymbolTableBuilder {

    private static final String VAR_TAG = "Var";
    private static final String TYPE_ATTR = "type";
    private static final String NAME_ATTR = "name";
    private static final String CONST_ATTR = "const";
    private static final String EXPRESSION_ATTR = "expression";
    private static final String DEFAULT_VAR_TYPE = "number";
    private static final String ARRAY_TAG = "Array";
    private static final String CYCLE_COMMAND_TAG = "CycleCommand";
    private static final String INDEX_FLAG_ATTR = "indexFlag";

    @Override
    public SymbolTable buildGlobal(DslFileNode fileNode, RuleRepository ruleRepository) {
        Map<String, VarDeclaration> declarations = new HashMap<>();
        Set<String> elementNames = new HashSet<>();
        addPresetGlobalVars(ruleRepository, declarations);
        if (fileNode != null && fileNode.getRootElement() != null) {
            collectVarDeclarations(fileNode.getRootElement(), declarations);
            collectElementNames(fileNode.getRootElement(), elementNames);
        }
        return SymbolTable.builder()
                .parent(null)
                .declarations(declarations)
                .elementNames(elementNames)
                .build();
    }

    private static void addPresetGlobalVars(RuleRepository ruleRepository,
                                            Map<String, VarDeclaration> declarations) {
        List<DslGlobalVar> globalVars = ruleRepository.getAllGlobalVars();
        for (DslGlobalVar globalVar : globalVars) {
            VarDeclaration declaration = VarDeclaration.builder()
                    .name(globalVar.getName())
                    .type(toDslType(globalVar.getType()))
                    .expression(null)
                    .isConstAttr(false)
                    .isGlobal(true)
                    .astNode(null)
                    .build();
            declarations.put(globalVar.getName(), declaration);
        }
    }

    /**
     * 深度优先遍历整棵元素树，收集所有 &lt;Var&gt; 元素作为全局变量声明。
     * 无论 Var 出现在树中哪个位置，均作为全局变量处理（见 themes-engine Var 文档）。
     */
    private static void collectVarDeclarations(DslElementNode elementNode,
                                               Map<String, VarDeclaration> declarations) {
        if (elementNode == null) {
            return;
        }
        if (VAR_TAG.equals(elementNode.getTagName())) {
            addVarDeclaration(elementNode, declarations);
        }
        List<DslElementNode> children = elementNode.getChildElements();
        if (children != null) {
            for (DslElementNode child : children) {
                collectVarDeclarations(child, declarations);
            }
        }
    }

    /**
     * 深度优先遍历整棵元素树，收集所有元素的 name 属性值。
     *
     * <p>用于 SEM-REF-002 元素 name 引用存在性检测：表达式 #&lt;name&gt;.&lt;prop&gt;
     * 与 Command target="name.property" 中的 name 部分需在此集合中存在。</p>
     */
    private static void collectElementNames(DslElementNode elementNode, Set<String> elementNames) {
        if (elementNode == null) {
            return;
        }
        String name = getAttrValue(elementNode, NAME_ATTR);
        if (name != null && !name.isEmpty()) {
            elementNames.add(name);
        }
        List<DslElementNode> children = elementNode.getChildElements();
        if (children != null) {
            for (DslElementNode child : children) {
                collectElementNames(child, elementNames);
            }
        }
    }

    private static void addVarDeclaration(DslElementNode varNode,
                                          Map<String, VarDeclaration> declarations) {
        String name = getAttrValue(varNode, NAME_ATTR);
        if (name == null || name.isEmpty()) {
            return;
        }
        String type = getAttrValue(varNode, TYPE_ATTR);
        if (type == null || type.isEmpty()) {
            type = DEFAULT_VAR_TYPE;
        }
        boolean isConstAttr = "true".equals(getAttrValue(varNode, CONST_ATTR));
        ExpressionAstNode expression = getAttrExpression(varNode, EXPRESSION_ATTR);
        VarDeclaration declaration = VarDeclaration.builder()
                .name(name)
                .type(toDslType(type))
                .expression(expression)
                .isConstAttr(isConstAttr)
                .isGlobal(false)
                .astNode(varNode)
                .build();
        declarations.put(name, declaration);
    }

    private static String getAttrValue(DslElementNode elementNode, String attrName) {
        List<DslAttributeNode> attributes = elementNode.getAttributes();
        if (attributes == null) {
            return null;
        }
        for (DslAttributeNode attribute : attributes) {
            if (attrName.equals(attribute.getName()) && attribute.getValue() != null) {
                return attribute.getValue().getRawValue();
            }
        }
        return null;
    }

    private static ExpressionAstNode getAttrExpression(DslElementNode elementNode, String attrName) {
        List<DslAttributeNode> attributes = elementNode.getAttributes();
        if (attributes == null) {
            return null;
        }
        for (DslAttributeNode attribute : attributes) {
            if (attrName.equals(attribute.getName()) && attribute.getValue() != null) {
                return attribute.getValue().getExpression().orElse(null);
            }
        }
        return null;
    }

    @Override
    public SymbolTable build(DslElementNode elementNode, SymbolTable parent, RuleRepository ruleRepository) {
        if (elementNode == null) {
            return parent;
        }
        String indexFlag = null;
        String tagName = elementNode.getTagName();
        if (ARRAY_TAG.equals(tagName) || CYCLE_COMMAND_TAG.equals(tagName)) {
            indexFlag = getAttrValue(elementNode, INDEX_FLAG_ATTR);
        }
        if (indexFlag == null || indexFlag.isEmpty()) {
            return parent;
        }
        VarDeclaration declaration = VarDeclaration.builder()
                .name(indexFlag)
                .type(new DslNumberType())
                .expression(null)
                .isConstAttr(false)
                .isGlobal(false)
                .astNode(elementNode)
                .build();
        Map<String, VarDeclaration> declarations = new HashMap<>();
        declarations.put(indexFlag, declaration);
        return SymbolTable.builder()
                .parent(parent)
                .declarations(declarations)
                .build();
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
}
