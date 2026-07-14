package com.huawei.theme.analysis.core.shared.ast;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false, exclude = {"parent"})
@ToString(exclude = {"parent"})
public class DslAttributeNode extends DslAstNode {
    String name;
    DslAttributeValueNode value;
    DslAstNode parent;
}
