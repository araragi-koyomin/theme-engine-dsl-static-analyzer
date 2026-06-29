package com.huawei.theme.analysis.core.expression;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.expression.model.FunctionParam;
import com.huawei.theme.analysis.core.expression.model.FunctionSignature;
import com.huawei.theme.analysis.core.shared.type.DslNumberType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionSignatureLibraryTest {

    @Test
    void getSignatureReturnsPresentWhenMatched() {
        FunctionSignatureLibrary library = new StubFunctionSignatureLibrary();
        Optional<FunctionSignature> sig = library.getSignature("abs", "number");
        assertTrue(sig.isPresent());
        assertEquals("abs", sig.get().getName());
    }

    @Test
    void getSignatureReturnsEmptyWhenAbsent() {
        FunctionSignatureLibrary library = new StubFunctionSignatureLibrary();
        Optional<FunctionSignature> sig = library.getSignature("nonexistent", "number");
        assertFalse(sig.isPresent());
    }

    @Test
    void getSignaturesReturnsAllOverloads() {
        FunctionSignatureLibrary library = new StubFunctionSignatureLibrary();
        List<FunctionSignature> sigs = library.getSignatures("abs");
        assertEquals(1, sigs.size());
    }

    @Test
    void hasFunctionReturnsTrueWhenPresent() {
        FunctionSignatureLibrary library = new StubFunctionSignatureLibrary();
        assertTrue(library.hasFunction("abs"));
    }

    @Test
    void hasFunctionReturnsFalseWhenAbsent() {
        FunctionSignatureLibrary library = new StubFunctionSignatureLibrary();
        assertFalse(library.hasFunction("nonexistent"));
    }

    private static class StubFunctionSignatureLibrary implements FunctionSignatureLibrary {

        private final Map<String, FunctionSignature> signatures = new HashMap<>();

        StubFunctionSignatureLibrary() {
            signatures.put("abs", FunctionSignature.builder()
                    .name("abs")
                    .params(List.of(FunctionParam.builder()
                            .name("value")
                            .type(new DslNumberType())
                            .isVariadic(false)
                            .build()))
                    .returnType(new DslNumberType())
                    .expressionKind("number")
                    .build());
        }

        @Override
        public Optional<FunctionSignature> getSignature(String name, String expressionKind) {
            return Optional.ofNullable(signatures.get(name));
        }

        @Override
        public List<FunctionSignature> getSignatures(String name) {
            FunctionSignature sig = signatures.get(name);
            if (sig == null) {
                return List.of();
            }
            return List.of(sig);
        }

        @Override
        public boolean hasFunction(String name) {
            return signatures.containsKey(name);
        }
    }
}
