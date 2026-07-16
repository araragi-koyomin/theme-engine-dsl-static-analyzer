package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VerboseCollector {

    private final Map<String, Long> stageTimes = new LinkedHashMap<>();
    private int astElements = 0;
    private int astAttrs = 0;
    private int astExprs = 0;
    private int symGlobals = 0;
    private int symUserVars = 0;
    private int symDups = 0;
    private final Map<String, Integer> analyzerCounts = new LinkedHashMap<>();
    private final List<String> typeInferences = new ArrayList<>();

    public void recordStageTime(String stage, long ms) {
        stageTimes.put(stage, ms);
    }

    public void recordAstStats(int elements, int attrs, int exprs) {
        this.astElements = elements;
        this.astAttrs = attrs;
        this.astExprs = exprs;
    }

    public void recordSymbolStats(int globals, int userVars, int dups) {
        this.symGlobals = globals;
        this.symUserVars = userVars;
        this.symDups = dups;
    }

    public void recordAnalyzerCount(String analyzerName, int count) {
        analyzerCounts.merge(analyzerName, count, Integer::sum);
    }

    public void recordTypeInference(String attrDesc, String inferred, String expected, boolean match) {
        String entry = "attr " + attrDesc + " \u2192 inferred: " + inferred
                + ", expected: " + expected + ", match: " + (match ? "OK" : "MISMATCH");
        typeInferences.add(entry);
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("[verbose] ");
        if (stageTimes.isEmpty()) {
            sb.append("(no stage times recorded)");
        } else {
            sb.append(stageTimes.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue() + "ms")
                    .collect(Collectors.joining(", ")));
        }
        sb.append("\n");
        sb.append("[verbose] AST: ").append(astElements).append(" elements, ")
                .append(astAttrs).append(" attributes, ").append(astExprs).append(" expressions\n");
        sb.append("[verbose] Symbols: ").append(symGlobals).append(" globals, ")
                .append(symUserVars).append(" user vars, ").append(symDups).append(" duplicates\n");
        sb.append("[verbose] Diagnostics: ");
        if (analyzerCounts.isEmpty()) {
            sb.append("(none)");
        } else {
            sb.append(analyzerCounts.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", ")));
        }
        sb.append("\n");
        sb.append("[verbose] Type inference: ");
        if (typeInferences.isEmpty()) {
            sb.append("(none)");
        } else {
            sb.append(String.join("; ", typeInferences));
        }
        return sb.toString();
    }
}
