package com.huawei.theme.analysis.core.rulecenter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import com.huawei.theme.analysis.core.ruledsl.generated.DslRuleConditionLexer;
import com.huawei.theme.analysis.core.ruledsl.generated.DslRuleConditionParser;

public class StrictConditionAcceptor {
    private final ConditionCapabilityRegistry capabilityRegistry;

    public StrictConditionAcceptor(ConditionCapabilityRegistry capabilityRegistry) {
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry);
    }

    public ConditionAcceptance accept(String condition) {
        ConditionCapabilityAnalysis capabilityAnalysis = capabilityRegistry.analyze(condition);
        if (!capabilityAnalysis.isExtensionShapeSupported()) {
            return rejected(condition, null, Set.of(), List.of("UNREGISTERED_EXTENSION"));
        }

        List<String> syntaxErrors = parse(capabilityAnalysis.getNormalizedCondition());
        if (!syntaxErrors.isEmpty()) {
            return rejected(
                    condition,
                    capabilityAnalysis.getNormalizedCondition(),
                    capabilityAnalysis.getCapabilities(),
                    syntaxErrors);
        }
        return ConditionAcceptance.builder()
                .accepted(true)
                .status(ConditionAcceptanceStatus.ACCEPTED)
                .originalCondition(condition)
                .normalizedCondition(capabilityAnalysis.getNormalizedCondition())
                .capabilities(capabilityAnalysis.getCapabilities())
                .syntaxErrors(List.of())
                .build();
    }

    private List<String> parse(String condition) {
        List<String> syntaxErrors = new ArrayList<>();
        CollectingErrorListener errorListener = new CollectingErrorListener(syntaxErrors);
        DslRuleConditionLexer lexer = new DslRuleConditionLexer(CharStreams.fromString(condition));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DslRuleConditionParser parser = new DslRuleConditionParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        parser.condition();
        if (tokens.LA(1) != Token.EOF) {
            syntaxErrors.add("trailing token: " + tokens.LT(1).getText());
        }
        return List.copyOf(syntaxErrors);
    }

    private ConditionAcceptance rejected(
            String original,
            String normalized,
            Set<ConditionCapability> capabilities,
            List<String> syntaxErrors) {
        return ConditionAcceptance.builder()
                .accepted(false)
                .status(ConditionAcceptanceStatus.UNSUPPORTED_CONDITION_GRAMMAR)
                .originalCondition(original)
                .normalizedCondition(normalized)
                .capabilities(capabilities)
                .syntaxErrors(List.copyOf(syntaxErrors))
                .build();
    }

    private static class CollectingErrorListener extends BaseErrorListener {
        private final List<String> syntaxErrors;

        private CollectingErrorListener(List<String> syntaxErrors) {
            this.syntaxErrors = syntaxErrors;
        }

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String message,
                RecognitionException exception) {
            syntaxErrors.add("line " + line + ":" + charPositionInLine + " " + message);
        }
    }
}
