package com.huawei.theme.analysis.core.expression;

import java.util.List;
import java.util.Optional;

import com.huawei.theme.analysis.core.expression.model.FunctionSignature;

public interface FunctionSignatureLibrary {
    Optional<FunctionSignature> getSignature(String name, String expressionKind);
    List<FunctionSignature> getSignatures(String name);
    boolean hasFunction(String name);
}
