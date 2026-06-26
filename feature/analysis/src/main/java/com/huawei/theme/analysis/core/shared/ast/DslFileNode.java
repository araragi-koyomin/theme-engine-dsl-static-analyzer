package com.huawei.theme.analysis.core.shared.ast;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class DslFileNode extends DslAstNode {
    String xmlDeclaration;
    DslElementNode rootElement;
}
