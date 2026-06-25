package com.huawei.theme.analysis.core.shared.ast;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class DslElementNode extends DslAstNode {
    String tagName;
    java.util.List<DslAttributeNode> attributes;
    java.util.List<DslElementNode> childElements;
    boolean selfClosing;
    boolean hasError;
    String errorMessage;
}
