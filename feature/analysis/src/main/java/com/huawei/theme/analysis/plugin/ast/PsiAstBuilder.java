package com.huawei.theme.analysis.plugin.ast;

import java.util.ArrayList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.syntaxanalysis.ExpressionEmbedder;

public class PsiAstBuilder {

    public DslAstTree build(@NotNull XmlFile xmlFile, @Nullable RuleRepository ruleRepository) {
        Document document = xmlFile.getViewProvider().getDocument();
        Project project = xmlFile.getProject();
        DslAstTree.Builder mapBuilder = DslAstTree.builder();

        DslFileNode fileNode = new DslFileNode();
        // Use the full VirtualFile path (not just the name) so IncludeHandler.resolveSibling
        // can find the file's directory and load included function_*.xml sub-files relative to it.
        fileNode.setFilePath(xmlFile.getVirtualFile() != null
                ? xmlFile.getVirtualFile().getPath() : xmlFile.getName());
        String fileText = document != null ? document.getText() : "";
        fileNode.setText(fileText);
        fileNode.setLine(1);
        fileNode.setColumn(0);

        XmlTag rootTag = xmlFile.getRootTag();
        DslElementNode root = rootTag != null
                ? buildElement(rootTag, document, ruleRepository, project, mapBuilder)
                : buildErrorNode("no root element", 1, 0);
        fileNode.setRootElement(root);
        root.setParent(fileNode);

        return mapBuilder.build(fileNode);
    }

    private DslElementNode buildElement(@NotNull XmlTag psiTag, @Nullable Document document,
                                       @Nullable RuleRepository ruleRepository, @NotNull Project project,
                                       DslAstTree.Builder mapBuilder) {
        DslElementNode node = new DslElementNode();
        String tagName = psiTag.getName();
        node.setTagName(tagName);
        node.setText(tagName);
        node.setAttributes(new ArrayList<>());
        node.setChildElements(new ArrayList<>());
        applyRange(node, psiTag.getTextRange(), document);

        for (XmlAttribute psiAttr : psiTag.getAttributes()) {
            buildAttribute(psiAttr, tagName, node, document, ruleRepository);
        }

        for (PsiElement child : psiTag.getChildren()) {
            if (child instanceof XmlTag childTag) {
                DslElementNode childNode = buildElement(childTag, document, ruleRepository, project, mapBuilder);
                childNode.setParent(node);
                node.getChildElements().add(childNode);
            }
        }
        node.setSelfClosing(node.getChildElements().isEmpty());

        SmartPsiElementPointer<XmlTag> pointer =
                SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiTag);
        mapBuilder.put(node, psiTag, pointer);
        return node;
    }

    private void buildAttribute(@NotNull XmlAttribute psiAttr, @NotNull String tagName,
                                @NotNull DslElementNode parent, @Nullable Document document,
                                @Nullable RuleRepository ruleRepository) {
        DslAttributeNode attr = new DslAttributeNode();
        attr.setName(psiAttr.getName());
        String attrValue = psiAttr.getValue();
        attr.setText(attrValue != null ? attrValue : "");
        applyRange(attr, psiAttr.getTextRange(), document);

        DslAttributeValueNode value = new DslAttributeValueNode();
        XmlAttributeValue valueElement = psiAttr.getValueElement();
        int valueDocLine = attr.getLine();
        int valueDocCol = attr.getColumn();
        if (valueElement != null && attrValue != null) {
            TextRange valueRange = valueElement.getTextRange();
            int valueStart = valueRange.getStartOffset();
            valueDocLine = document != null ? document.getLineNumber(valueStart) + 1 : 1;
            valueDocCol = document != null
                    ? valueStart - document.getLineStartOffset(document.getLineNumber(valueStart))
                    : 0;
            applyRange(value, valueRange, document);
        } else {
            value.setLine(attr.getLine());
            value.setColumn(attr.getColumn());
            value.setEndLine(attr.getEndLine());
            value.setEndColumn(attr.getEndColumn());
        }
        value.setRawValue(attrValue);
        value.setText(attrValue != null ? attrValue : "");
        ExpressionEmbedder.embed(value, attrValue != null ? attrValue : "", tagName, attr.getName(),
                valueDocLine, valueDocCol, ruleRepository);
        attr.setValue(value);
        attr.setParent(parent);
        parent.getAttributes().add(attr);
    }

    private static void applyRange(@NotNull DslElementNode node, @NotNull TextRange range,
                                   @Nullable Document document) {
        int[] lc = toLineCol(range.getStartOffset(), document);
        int[] ec = toLineCol(range.getEndOffset(), document);
        node.setLine(lc[0]);
        node.setColumn(lc[1]);
        node.setEndLine(ec[0]);
        node.setEndColumn(ec[1]);
    }

    private static void applyRange(@NotNull DslAttributeNode attr, @NotNull TextRange range,
                                   @Nullable Document document) {
        int[] lc = toLineCol(range.getStartOffset(), document);
        int[] ec = toLineCol(range.getEndOffset(), document);
        attr.setLine(lc[0]);
        attr.setColumn(lc[1]);
        attr.setEndLine(ec[0]);
        attr.setEndColumn(ec[1]);
    }

    private static void applyRange(@NotNull DslAttributeValueNode value, @NotNull TextRange range,
                                   @Nullable Document document) {
        int[] lc = toLineCol(range.getStartOffset(), document);
        int[] ec = toLineCol(range.getEndOffset(), document);
        value.setLine(lc[0]);
        value.setColumn(lc[1]);
        value.setEndLine(ec[0]);
        value.setEndColumn(ec[1]);
    }

    private static int[] toLineCol(int offset, @Nullable Document document) {
        if (document == null || offset < 0 || offset > document.getTextLength()) {
            return new int[]{1, 0};
        }
        int line = document.getLineNumber(offset);
        int col = offset - document.getLineStartOffset(line);
        return new int[]{line + 1, col};
    }

    private static DslElementNode buildErrorNode(String message, int line, int column) {
        DslElementNode node = new DslElementNode();
        node.setHasError(true);
        node.setErrorMessage(message);
        node.setLine(line);
        node.setColumn(Math.max(column, 0));
        node.setEndLine(line);
        node.setEndColumn(Math.max(column, 0));
        node.setAttributes(new ArrayList<>());
        node.setChildElements(new ArrayList<>());
        return node;
    }
}
