package com.huawei.theme.analysis.core.shared.ast;

public enum ExpressionKind {
    LITERAL,
    VARIABLE_REF,
    FUNCTION_CALL,
    BINARY_EXPR,
    UNARY_EXPR,
    CONDITIONAL,
    ARRAY_ACCESS,
    UNKNOWN
}
