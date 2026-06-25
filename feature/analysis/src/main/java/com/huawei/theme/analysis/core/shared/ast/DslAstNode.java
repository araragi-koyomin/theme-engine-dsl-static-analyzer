package com.huawei.theme.analysis.core.shared.ast;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class DslAstNode {
    String text;
    int line;
    int column;
}
