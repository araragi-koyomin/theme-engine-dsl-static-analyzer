package com.huawei.theme.analysis.core.function;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.theme.analysis.core.expression.model.FunctionParam;
import com.huawei.theme.analysis.core.expression.model.FunctionSignature;
import com.huawei.theme.analysis.core.shared.type.DslArrayType;
import com.huawei.theme.analysis.core.shared.type.DslNumberType;
import com.huawei.theme.analysis.core.shared.type.DslStringType;

class JsonFunctionSignatureLoaderTest {

    private JsonFunctionSignatureLoader loader;

    @BeforeEach
    void setUp() {
        loader = new JsonFunctionSignatureLoader().loadFromClasspath();
    }

    @Test
    void loadFromClasspath() {
        assertTrue(loader.hasFunction("sin"));
        assertTrue(loader.hasFunction("substr"));
        assertTrue(loader.hasFunction("argb"));
    }

    @Test
    void getSignatureSin() {
        Optional<FunctionSignature> sig = loader.getSignature("sin", "number");
        assertTrue(sig.isPresent());
        assertEquals("sin", sig.get().getName());
        assertEquals(1, sig.get().getParams().size());
        assertEquals("number", sig.get().getReturnType().getName());
        assertEquals("number", sig.get().getExpressionKind());
    }

    @Test
    void getSignatureIfelseNumber() {
        Optional<FunctionSignature> sig = loader.getSignature("ifelse", "number");
        assertTrue(sig.isPresent());
        assertEquals("ifelse", sig.get().getName());
        assertEquals(3, sig.get().getParams().size());
        assertEquals("number", sig.get().getReturnType().getName());

        FunctionParam lastParam = sig.get().getParams().get(2);
        assertTrue(lastParam.isVariadic());
        assertEquals("z", lastParam.getName());
    }

    @Test
    void getSignatureIfelseString() {
        Optional<FunctionSignature> sig = loader.getSignature("ifelse", "string");
        assertTrue(sig.isPresent());
        assertEquals("ifelse", sig.get().getName());
        assertEquals(3, sig.get().getParams().size());
        assertEquals("string", sig.get().getReturnType().getName());

        FunctionParam condParam = sig.get().getParams().get(0);
        assertEquals("number", condParam.getType().getName());

        FunctionParam yParam = sig.get().getParams().get(1);
        assertEquals("string", yParam.getType().getName());

        FunctionParam lastParam = sig.get().getParams().get(2);
        assertTrue(lastParam.isVariadic());
        assertEquals("string", lastParam.getType().getName());
    }

    @Test
    void getSignatureSubstr() {
        Optional<FunctionSignature> sig = loader.getSignature("substr", "string");
        assertTrue(sig.isPresent());
        assertEquals("substr", sig.get().getName());
        assertEquals(3, sig.get().getParams().size());
        assertEquals("string", sig.get().getParams().get(0).getType().getName());
        assertEquals("number", sig.get().getParams().get(1).getType().getName());
        assertEquals("number", sig.get().getParams().get(2).getType().getName());
    }

    @Test
    void getSignatureArgb() {
        Optional<FunctionSignature> sig = loader.getSignature("argb", "string");
        assertTrue(sig.isPresent());
        assertEquals("argb", sig.get().getName());
        assertEquals(4, sig.get().getParams().size());
        assertEquals("string", sig.get().getReturnType().getName());
        assertEquals("string", sig.get().getExpressionKind());

        for (FunctionParam param : sig.get().getParams()) {
            assertEquals("number", param.getType().getName());
            assertFalse(param.isVariadic());
        }
    }

    @Test
    void getSignaturesIfelseOverloads() {
        List<FunctionSignature> sigs = loader.getSignatures("ifelse");
        assertEquals(2, sigs.size());
    }

    @Test
    void getSignaturesIsnullOverloads() {
        List<FunctionSignature> sigs = loader.getSignatures("isnull");
        assertEquals(2, sigs.size());

        Optional<FunctionSignature> numberIsNull = loader.getSignature("isnull", "number");
        assertTrue(numberIsNull.isPresent());
        assertEquals("number", numberIsNull.get().getParams().get(0).getType().getName());

        Optional<FunctionSignature> stringIsNull = loader.getSignature("isnull", "string");
        assertTrue(stringIsNull.isPresent());
        assertEquals("string", stringIsNull.get().getParams().get(0).getType().getName());
    }

    @Test
    void hasFunctionExisting() {
        assertTrue(loader.hasFunction("sin"));
        assertTrue(loader.hasFunction("cos"));
        assertTrue(loader.hasFunction("substr"));
        assertTrue(loader.hasFunction("ifelse"));
    }

    @Test
    void hasFunctionNonExisting() {
        assertFalse(loader.hasFunction("nonexistent"));
        assertFalse(loader.hasFunction("foo"));
    }

    @Test
    void getSignatureNonExistingReturnsEmpty() {
        Optional<FunctionSignature> sig = loader.getSignature("nonexistent", "number");
        assertTrue(sig.isEmpty());
    }

    @Test
    void dslNumberTypeDeserialization() {
        Optional<FunctionSignature> sig = loader.getSignature("sin", "number");
        assertTrue(sig.isPresent());
        assertEquals(DslNumberType.class, sig.get().getReturnType().getClass());
        assertEquals("number", sig.get().getReturnType().getName());
    }

    @Test
    void dslStringTypeDeserialization() {
        Optional<FunctionSignature> sig = loader.getSignature("substr", "string");
        assertTrue(sig.isPresent());
        assertEquals(DslStringType.class, sig.get().getReturnType().getClass());
        assertEquals("string", sig.get().getReturnType().getName());
    }

    @Test
    void dslArrayTypeDeserialization() {
        Optional<FunctionSignature> sig = loader.getSignature("sin", "number");
        assertTrue(sig.isPresent());
        FunctionParam param = sig.get().getParams().get(0);
        assertEquals(DslNumberType.class, param.getType().getClass());
    }

    @Test
    void isVariadicFlagForIfelse() {
        Optional<FunctionSignature> numberIfelse = loader.getSignature("ifelse", "number");
        assertTrue(numberIfelse.isPresent());
        assertFalse(numberIfelse.get().getParams().get(0).isVariadic());
        assertFalse(numberIfelse.get().getParams().get(1).isVariadic());
        assertTrue(numberIfelse.get().getParams().get(2).isVariadic());

        Optional<FunctionSignature> stringIfelse = loader.getSignature("ifelse", "string");
        assertTrue(stringIfelse.isPresent());
        assertFalse(stringIfelse.get().getParams().get(0).isVariadic());
        assertFalse(stringIfelse.get().getParams().get(1).isVariadic());
        assertTrue(stringIfelse.get().getParams().get(2).isVariadic());
    }

    @Test
    void randHasNoParams() {
        Optional<FunctionSignature> sig = loader.getSignature("rand", "number");
        assertTrue(sig.isPresent());
        assertEquals(0, sig.get().getParams().size());
    }

    @Test
    void preciseevalHasMixedTypes() {
        Optional<FunctionSignature> sig = loader.getSignature("preciseeval", "number");
        assertTrue(sig.isPresent());
        assertEquals("string", sig.get().getParams().get(0).getType().getName());
        assertEquals("number", sig.get().getParams().get(1).getType().getName());
    }
}
