package com.huawei.theme.analysis.core.shared.ast;

import java.util.Optional;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class DslAttributeValueNode extends DslAstNode {
    String rawValue;
    Optional<ExpressionAstNode> expression;
    boolean isLiteral;
}
