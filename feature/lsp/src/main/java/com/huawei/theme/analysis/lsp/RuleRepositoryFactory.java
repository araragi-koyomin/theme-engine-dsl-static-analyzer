package com.huawei.theme.analysis.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary;
import com.huawei.theme.analysis.core.function.JsonFunctionSignatureLoader;
import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;

/**
 * Assembles the {@link RuleRepository} for the LSP server.
 *
 * <p>By default the built-in rule resources (packaged under {@code /rules}
 * and {@code /functions} on the classpath) are extracted to a temporary
 * directory and loaded via {@link JsonRuleLoader}. An external rule directory
 * supplied through the {@code --rule-dir} launch argument overrides the
 * built-in rules.</p>
 */
final class RuleRepositoryFactory {

    private static final Logger LOG = Logger.getLogger(RuleRepositoryFactory.class.getName());

    private final String externalRuleDir;

    RuleRepositoryFactory(String externalRuleDir) {
        this.externalRuleDir = externalRuleDir;
    }

    RuleRepository create() {
        Path builtinRoot = null;
        try {
            builtinRoot = ClasspathResourceExtractor.extractBuiltinResources();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to extract built-in rule resources", e);
        }

        String ruleDir = (externalRuleDir != null && !externalRuleDir.isEmpty())
                ? externalRuleDir
                : (builtinRoot != null ? builtinRoot.resolve("rules").toString() : null);

        FunctionSignatureLibrary functionLibrary = loadFunctionLibrary(builtinRoot);

        JsonRuleLoader loader = new JsonRuleLoader();
        if (ruleDir == null) {
            LOG.warning("No rule directory available; analysis will run with an empty rule set");
            return loader.buildRuleRepository(
                    Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap(), functionLibrary);
        }
        try {
            return loader.loadFromDirectory(ruleDir, functionLibrary);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to load rules from " + ruleDir, e);
            return loader.buildRuleRepository(
                    Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap(), functionLibrary);
        }
    }

    private static FunctionSignatureLibrary loadFunctionLibrary(Path builtinRoot) {
        if (builtinRoot == null) {
            return null;
        }
        Path functionsDir = builtinRoot.resolve("functions");
        if (!Files.isDirectory(functionsDir)) {
            return null;
        }
        try {
            return new JsonFunctionSignatureLoader()
                    .loadFromDirectory(functionsDir.toString());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to load function signatures", e);
            return null;
        }
    }
}
