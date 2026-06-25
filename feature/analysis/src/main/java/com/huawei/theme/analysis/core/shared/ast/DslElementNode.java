package com.huawei.theme.analysis.core.shared.ast;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class DslElementNode extends DslAstNode {
    String tagName;
    List<DslAttributeNode> attributes;
    List<DslElementNode> childElements;
    boolean selfClosing;
    boolean hasError;
    String errorMessage;
}
