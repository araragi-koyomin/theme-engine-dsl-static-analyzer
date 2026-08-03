package com.huawei.theme.analysis.plugin.ast;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;

import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

public final class DslAstTree {

    private final DslFileNode ast;
    private final Map<DslElementNode, SmartPsiElementPointer<XmlTag>> nodeToTag;
    private final Map<XmlTag, DslElementNode> tagToNode;

    DslAstTree(DslFileNode ast,
               Map<DslElementNode, SmartPsiElementPointer<XmlTag>> nodeToTag,
               Map<XmlTag, DslElementNode> tagToNode) {
        this.ast = ast;
        this.nodeToTag = nodeToTag;
        this.tagToNode = tagToNode;
    }

    public DslFileNode getAst() {
        return ast;
    }

    public Optional<XmlTag> getTag(@Nullable DslElementNode node) {
        if (node == null) {
            return Optional.empty();
        }
        SmartPsiElementPointer<XmlTag> pointer = nodeToTag.get(node);
        if (pointer == null) {
            return Optional.empty();
        }
        XmlTag tag = pointer.getElement();
        return Optional.ofNullable(tag);
    }

    public Optional<DslElementNode> getNode(@Nullable XmlTag tag) {
        if (tag == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tagToNode.get(tag));
    }

    public int size() {
        return nodeToTag.size();
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private final Map<DslElementNode, SmartPsiElementPointer<XmlTag>> nodeToTag = new IdentityHashMap<>();
        private final Map<XmlTag, DslElementNode> tagToNode = new IdentityHashMap<>();

        void put(@NotNull DslElementNode node, @NotNull XmlTag tag,
                 @NotNull SmartPsiElementPointer<XmlTag> pointer) {
            nodeToTag.put(node, pointer);
            tagToNode.put(tag, node);
        }

        DslAstTree build(DslFileNode ast) {
            return new DslAstTree(ast, nodeToTag, tagToNode);
        }
    }
}
