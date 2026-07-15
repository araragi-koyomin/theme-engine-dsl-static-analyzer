package com.huawei.theme.analysis.core.semanticanalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class VerboseCollectorTest {

    @Test
    void emptyCollectorRendersFiveVerboseLines() {
        VerboseCollector collector = new VerboseCollector();
        String rendered = collector.render();
        long lineCount = rendered.lines().count();
        assertEquals(5L, lineCount, "empty collector must render 5 [verbose] lines");
        rendered.lines().forEach(line -> assertTrue(line.startsWith("[verbose]"),
                "each line must start with [verbose]: " + line));
    }

    @Test
    void renderContainsAllFiveKeywordsAfterRecording() {
        VerboseCollector collector = new VerboseCollector();
        collector.recordStageTime("AST build", 12L);
        collector.recordStageTime("semantic analysis", 34L);
        collector.recordStageTime("type inference", 56L);
        collector.recordAstStats(10, 20, 5);
        collector.recordSymbolStats(3, 7, 1);
        collector.recordAnalyzerCount("SyntaxErrorAnalyzer", 4);
        collector.recordAnalyzerCount("TypeAnalyzer", 9);
        collector.recordAnalyzerCount("ConstraintAnalyzer", 2);
        collector.recordTypeInference("Image.src=\"@drawable/ic\"", "STRING", "STRING", true);
        collector.recordTypeInference("Text.size=\"big\"", "STRING", "INTEGER", false);

        String rendered = collector.render();

        assertTrue(rendered.contains("[verbose]"), "render output must contain [verbose] prefix");
        assertTrue(rendered.contains("AST build"), "must contain stage 'AST build' keyword");
        assertTrue(rendered.contains("AST:"), "must contain 'AST:' keyword");
        assertTrue(rendered.contains("Symbols:"), "must contain 'Symbols:' keyword");
        assertTrue(rendered.contains("Diagnostics:"), "must contain 'Diagnostics:' keyword");
        assertTrue(rendered.contains("Type inference:"), "must contain 'Type inference:' keyword");
    }

    @Test
    void stageTimeLineRendersRecordedStagesWithMsSuffix() {
        VerboseCollector collector = new VerboseCollector();
        collector.recordStageTime("AST build", 12L);
        collector.recordStageTime("type inference", 56L);

        String rendered = collector.render();
        String stageLine = rendered.lines().findFirst().orElse("");

        assertTrue(stageLine.startsWith("[verbose]"));
        assertTrue(stageLine.contains("AST build: 12ms"), stageLine);
        assertTrue(stageLine.contains("type inference: 56ms"), stageLine);
    }

    @Test
    void astStatsLineRendersCounts() {
        VerboseCollector collector = new VerboseCollector();
        collector.recordAstStats(10, 20, 5);

        String astLine = collector.render().lines().skip(1).findFirst().orElse("");

        assertTrue(astLine.contains("AST: 10 elements, 20 attributes, 5 expressions"), astLine);
    }

    @Test
    void emptyAstStatsRenderAsZero() {
        VerboseCollector collector = new VerboseCollector();
        String astLine = collector.render().lines().skip(1).findFirst().orElse("");

        assertTrue(astLine.contains("AST: 0 elements, 0 attributes, 0 expressions"), astLine);
    }

    @Test
    void symbolStatsLineRendersCounts() {
        VerboseCollector collector = new VerboseCollector();
        collector.recordSymbolStats(3, 7, 1);

        String symLine = collector.render().lines().skip(2).findFirst().orElse("");

        assertTrue(symLine.contains("Symbols: 3 globals, 7 user vars, 1 duplicates"), symLine);
    }

    @Test
    void emptySymbolStatsRenderAsZero() {
        VerboseCollector collector = new VerboseCollector();
        String symLine = collector.render().lines().skip(2).findFirst().orElse("");

        assertTrue(symLine.contains("Symbols: 0 globals, 0 user vars, 0 duplicates"), symLine);
    }

    @Test
    void analyzerCountLineRendersNameEqualsCountPairs() {
        VerboseCollector collector = new VerboseCollector();
        collector.recordAnalyzerCount("SyntaxErrorAnalyzer", 4);
        collector.recordAnalyzerCount("TypeAnalyzer", 9);

        String diagLine = collector.render().lines().skip(3).findFirst().orElse("");

        assertTrue(diagLine.startsWith("[verbose] Diagnostics:"), diagLine);
        assertTrue(diagLine.contains("SyntaxErrorAnalyzer=4"), diagLine);
        assertTrue(diagLine.contains("TypeAnalyzer=9"), diagLine);
    }

    @Test
    void typeInferenceLineRendersAttrDescAndMatchFlag() {
        VerboseCollector collector = new VerboseCollector();
        collector.recordTypeInference("Image.src=\"@drawable/ic\"", "STRING", "STRING", true);
        collector.recordTypeInference("Text.size=\"big\"", "STRING", "INTEGER", false);

        String infLine = collector.render().lines().skip(4).findFirst().orElse("");

        assertTrue(infLine.startsWith("[verbose] Type inference:"), infLine);
        assertTrue(infLine.contains("attr Image.src=\"@drawable/ic\""), infLine);
        assertTrue(infLine.contains("inferred: STRING"), infLine);
        assertTrue(infLine.contains("expected: STRING"), infLine);
        assertTrue(infLine.contains("match: OK"), infLine);
        assertTrue(infLine.contains("expected: INTEGER"), infLine);
        assertTrue(infLine.contains("match: MISMATCH"), infLine);
    }

    @Test
    void recordStageTimeOverwriteSameStageKeepsLatest() {
        VerboseCollector collector = new VerboseCollector();
        collector.recordStageTime("AST build", 5L);
        collector.recordStageTime("AST build", 42L);

        String stageLine = collector.render().lines().findFirst().orElse("");

        assertTrue(stageLine.contains("AST build: 42ms"), stageLine);
        assertTrue(!stageLine.contains("AST build: 5ms"), stageLine);
    }

    @Test
    void recordAnalyzerCountAccumulatesAcrossCalls() {
        VerboseCollector collector = new VerboseCollector();
        collector.recordAnalyzerCount("TypeAnalyzer", 3);
        collector.recordAnalyzerCount("TypeAnalyzer", 5);

        String diagLine = collector.render().lines().skip(3).findFirst().orElse("");

        assertTrue(diagLine.contains("TypeAnalyzer=8"), diagLine);
    }

    @Test
    void analyzerCountsPreserveInsertionOrder() {
        VerboseCollector collector = new VerboseCollector();
        collector.recordAnalyzerCount("ConstraintAnalyzer", 2);
        collector.recordAnalyzerCount("SyntaxErrorAnalyzer", 4);
        collector.recordAnalyzerCount("TypeAnalyzer", 9);

        String diagLine = collector.render().lines().skip(3).findFirst().orElse("");
        int cPos = diagLine.indexOf("ConstraintAnalyzer");
        int sPos = diagLine.indexOf("SyntaxErrorAnalyzer");
        int tPos = diagLine.indexOf("TypeAnalyzer");

        assertTrue(cPos < sPos && sPos < tPos, "insertion order must be preserved: " + diagLine);
    }

    @Test
    void stageTimesPreserveInsertionOrder() {
        VerboseCollector collector = new VerboseCollector();
        collector.recordStageTime("semantic analysis", 10L);
        collector.recordStageTime("AST build", 20L);

        String stageLine = collector.render().lines().findFirst().orElse("");
        int semPos = stageLine.indexOf("semantic analysis");
        int astPos = stageLine.indexOf("AST build");

        assertTrue(semPos < astPos, "insertion order must be preserved: " + stageLine);
    }
}
