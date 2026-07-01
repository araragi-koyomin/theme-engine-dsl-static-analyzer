package com.huawei.theme.analysis.core.function;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary;
import com.huawei.theme.analysis.core.expression.model.FunctionParam;
import com.huawei.theme.analysis.core.expression.model.FunctionSignature;
import com.huawei.theme.analysis.core.shared.type.DslType;
import com.huawei.theme.analysis.core.shared.type.adapter.DslTypeAdapter;

public class JsonFunctionSignatureLoader implements FunctionSignatureLibrary {

    private static final String DEFAULT_FUNCTIONS_DIR = "functions";
    private static final String FUNCTIONS_FILE = "dsl_functions.json";

    private final Map<String, List<FunctionSignature>> signatureIndex;

    private final Gson gson;

    public JsonFunctionSignatureLoader() {
        this.gson = createGson();
        this.signatureIndex = new HashMap<>();
    }

    private JsonFunctionSignatureLoader(Map<String, List<FunctionSignature>> signatureIndex) {
        this.gson = createGson();
        this.signatureIndex = signatureIndex;
    }

    private static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(DslType.class, new DslTypeAdapter())
                .setPrettyPrinting()
                .create();
    }

    public JsonFunctionSignatureLoader loadFromDirectory(String dir) {
        Path filePath = Path.of(dir, FUNCTIONS_FILE);
        if (!Files.exists(filePath)) {
            throw new FunctionLoadException("Functions file not found: " + filePath);
        }

        try (FileReader reader = new FileReader(filePath.toFile(), StandardCharsets.UTF_8)) {
            FunctionFileWrapper wrapper = gson.fromJson(reader, FunctionFileWrapper.class);
            if (wrapper == null || wrapper.functions == null) {
                return new JsonFunctionSignatureLoader(Collections.emptyMap());
            }

            Map<String, List<FunctionSignature>> index = wrapper.functions.stream()
                    .filter(s -> s.getName() != null)
                    .peek(JsonFunctionSignatureLoader::normalizeSignature)
                    .collect(Collectors.groupingBy(FunctionSignature::getName));

            return new JsonFunctionSignatureLoader(index);
        } catch (IOException e) {
            throw new FunctionLoadException("Failed to read functions file: " + filePath, e);
        } catch (JsonSyntaxException e) {
            throw new FunctionLoadException("JSON syntax error in functions file: " + filePath, e);
        }
    }

    public JsonFunctionSignatureLoader loadFromClasspath() {
        String moduleDir = System.getProperty("user.dir");
        return loadFromDirectory(moduleDir + "/src/main/resources/" + DEFAULT_FUNCTIONS_DIR);
    }

    private static void normalizeSignature(FunctionSignature signature) {
        if (signature.getParams() == null) {
            signature.setParams(Collections.emptyList());
        }
        for (FunctionParam param : signature.getParams()) {
            if (param.getType() == null) {
                param.setType(new com.huawei.theme.analysis.core.shared.type.DslNumberType());
            }
        }
    }

    @Override
    public Optional<FunctionSignature> getSignature(String name, String expressionKind) {
        List<FunctionSignature> signatures = signatureIndex.getOrDefault(name, Collections.emptyList());
        return signatures.stream()
                .filter(s -> expressionKind.equals(s.getExpressionKind()))
                .findFirst();
    }

    @Override
    public List<FunctionSignature> getSignatures(String name) {
        return signatureIndex.getOrDefault(name, Collections.emptyList());
    }

    @Override
    public boolean hasFunction(String name) {
        return signatureIndex.containsKey(name);
    }

    public static class FunctionLoadException extends RuntimeException {
        public FunctionLoadException(String message) {
            super(message);
        }

        public FunctionLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class FunctionFileWrapper {
        List<FunctionSignature> functions;
    }
}
