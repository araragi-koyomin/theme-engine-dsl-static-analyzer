package com.huawei.theme.analysis.core.macro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.syntaxanalysis.ExpressionEmbedder;

public final class MacroExpander {

    private final RuleRepository ruleRepository;
    private final List<MacroHandler> handlers;

    public MacroExpander(@Nullable RuleRepository ruleRepository) {
        this(ruleRepository, List.of(new ForHandler()));
    }

    public MacroExpander(@Nullable RuleRepository ruleRepository, @NotNull List<MacroHandler> handlers) {
        this.ruleRepository = ruleRepository;
        this.handlers = handlers;
    }

    @NotNull
    public DemacroedAst expand(@NotNull DslFileNode normal) {
        DemacroedAst.Builder builder = DemacroedAst.builder(normal.getFilePath());
        DslElementNode root = normal.getRootElement();
        DslFileNode demacroedFile = cloneFile(normal);
        if (root == null) {
            return builder.build(demacroedFile);
        }
        List<DslElementNode> expandedRoots = expandElement(root, new HashMap<>(), builder);
        if (expandedRoots.isEmpty()) {
            return builder.build(demacroedFile);
        }
        DslElementNode demacroedRoot = expandedRoots.get(0);
        demacroedFile.setRootElement(demacroedRoot);
        demacroedRoot.setParent(demacroedFile);
        return builder.build(demacroedFile);
    }

    @NotNull
    public List<DslElementNode> expandElement(@NotNull DslElementNode node,
                                                @NotNull Map<String, Object> scope,
                                                @NotNull DemacroedAst.Builder builder) {
        for (MacroHandler handler : handlers) {
            if (handler.recognize(node)) {
                return handler.expand(node, scope, this, builder);
            }
        }
        DslElementNode clone = cloneElementShallow(node, scope, builder);
        builder.put(clone, node);
        List<DslElementNode> expandedChildren = new ArrayList<>();
        if (node.getChildElements() != null) {
            for (DslElementNode child : node.getChildElements()) {
                expandedChildren.addAll(expandElement(child, scope, builder));
            }
        }
        clone.setChildElements(expandedChildren);
        for (DslElementNode c : expandedChildren) {
            c.setParent(clone);
        }
        return List.of(clone);
    }

    private DslFileNode cloneFile(@NotNull DslFileNode original) {
        DslFileNode f = new DslFileNode();
        f.setFilePath(original.getFilePath());
        f.setText(original.getText());
        f.setLine(original.getLine());
        f.setColumn(original.getColumn());
        f.setEndLine(original.getEndLine());
        f.setEndColumn(original.getEndColumn());
        f.setXmlDeclaration(original.getXmlDeclaration());
        return f;
    }

    private DslElementNode cloneElementShallow(@NotNull DslElementNode node,
                                                @NotNull Map<String, Object> scope,
                                                @NotNull DemacroedAst.Builder builder) {
        DslElementNode clone = new DslElementNode();
        clone.setTagName(node.getTagName());
        clone.setText(node.getText());
        clone.setLine(node.getLine());
        clone.setColumn(node.getColumn());
        clone.setEndLine(node.getEndLine());
        clone.setEndColumn(node.getEndColumn());
        clone.setSelfClosing(node.isSelfClosing());
        clone.setHasError(node.isHasError());
        clone.setErrorMessage(node.getErrorMessage());
        clone.setAttributes(new ArrayList<>());
        clone.setChildElements(new ArrayList<>());
        if (node.getAttributes() != null) {
            for (DslAttributeNode a : node.getAttributes()) {
                DslAttributeNode clonedAttr = cloneAttribute(a, node.getTagName(), scope, builder);
                clonedAttr.setParent(clone);
                clone.getAttributes().add(clonedAttr);
            }
        }
        return clone;
    }

    private DslAttributeNode cloneAttribute(@NotNull DslAttributeNode attr, @NotNull String tagName,
                                            @NotNull Map<String, Object> scope,
                                            @NotNull DemacroedAst.Builder builder) {
        DslAttributeNode a = new DslAttributeNode();
        a.setName(attr.getName());
        a.setText(attr.getText());
        a.setLine(attr.getLine());
        a.setColumn(attr.getColumn());
        a.setEndLine(attr.getEndLine());
        a.setEndColumn(attr.getEndColumn());

        DslAttributeValueNode orig = attr.getValue();
        String raw = orig != null ? orig.getRawValue() : null;
        String interpolated = CompileTimeInterpolator.interpolate(
                raw, scope, builder.diagnostics(), a, builder.filePath());

        DslAttributeValueNode v = new DslAttributeValueNode();
        v.setRawValue(interpolated);
        v.setText(interpolated);
        int vLine = orig != null ? orig.getLine() : a.getLine();
        int vCol = orig != null ? orig.getColumn() : a.getColumn();
        int vEndLine = orig != null ? orig.getEndLine() : a.getEndLine();
        int vEndCol = orig != null ? orig.getEndColumn() : a.getEndColumn();
        v.setLine(vLine);
        v.setColumn(vCol);
        v.setEndLine(vEndLine);
        v.setEndColumn(vEndCol);
        ExpressionEmbedder.embed(v, interpolated != null ? interpolated : "", tagName, attr.getName(),
                vLine, vCol, ruleRepository);
        a.setValue(v);
        return a;
    }
}
