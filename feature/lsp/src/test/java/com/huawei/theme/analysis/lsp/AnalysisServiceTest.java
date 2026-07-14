package com.huawei.theme.analysis.lsp;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.eclipse.lsp4j.Range;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.AnalyzerRegistry;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisServiceTest {

    @Test
    void loadsRulesAndAnalyzesWithoutException() {
        RuleRepository repo = new RuleRepositoryFactory(null).create();
        assertFalse(repo.getAllElementNames().isEmpty(),
                "built-in rule repository should load element rules");

        AnalysisService svc = new AnalysisService(repo);
        String content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<Widget screenWidth=\"1080\" screenHeight=\"530\">\n"
                + "  <Group name=\"g\" x=\"0\" y=\"0\" w=\"1080\" h=\"530\"/>\n"
                + "</Widget>";
        List<Diagnostic> diags = svc.analyze("script.xml", content);
        assertNotNull(diags, "analysis must return a non-null diagnostic list");
    }

    @Test
    void analyzesMalformedXmlGracefully() {
        RuleRepository repo = new RuleRepositoryFactory(null).create();
        AnalysisService svc = new AnalysisService(repo);
        // Unclosed <Var> and <Lockscreen>; the SAX parser surfaces this as an
        // error node. AnalysisService must not throw.
        String content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<Lockscreen frameRate=\"60\" screenWidth=\"1080\">\n"
                + "  <Var name=\"testVar\" expression=\"1\" type=\"number\">\n"
                + "  <Group name=\"testGroup\" x=\"0\" y=\"0\" w=\"1080\" h=\"1920\"/>\n";
        List<Diagnostic> diags = svc.analyze("script_error.xml", content);
        assertNotNull(diags, "malformed XML must not crash the analyzer");
    }

    /**
     * Regression: undefined-variable diagnostics (SEM-REF-001) are built by
     * {@code VarRefAnalyzer} with explicit line/endLine but no astNode, so
     * {@link PositionMapper#toRange} previously rendered them zero-width and
     * the IntelliJ annotator dropped them. The LSP diagnostic range must now
     * be non-zero-width so the error is visible.
     */
    @Test
    void undefinedVariableDiagnosticHasNonZeroWidthRange() {
        RuleRepository repo = new RuleRepositoryFactory(null).create();
        AnalysisService svc = new AnalysisService(repo);
        // AnalysisService relies on AnalyzerRegistry being initialized (the
        // LSP server does this in DslLanguageServer's constructor); required
        // here so VarRefAnalyzer is registered and SEM-REF-001 is produced.
        AnalyzerRegistry.init();
        // #undefinedVar references an undeclared variable -> VarRefAnalyzer
        // emits SEM-REF-001 with position set directly (no astNode).
        String content = "<Lockscreen><Image name=\"img\" x=\"#undefinedVar\"/></Lockscreen>";
        List<Diagnostic> core = svc.analyze("script.xml", content);
        assertFalse(core.isEmpty(), "expected at least one diagnostic for #undefinedVar");
        boolean foundRef = false;
        for (Diagnostic d : core) {
            if ("SEM-REF-001".equals(d.getRuleId())) {
                foundRef = true;
                assertTrue(d.getEndLine() > 0 || d.getEndColumn() > d.getColumn(),
                        "SEM-REF-001 must carry a non-zero-width end position; got line="
                                + d.getLine() + " col=" + d.getColumn()
                                + " endLine=" + d.getEndLine() + " endCol=" + d.getEndColumn());
            }
        }
        assertTrue(foundRef, "expected SEM-REF-001 for #undefinedVar; got: "
                + core.stream().map(Diagnostic::getRuleId).reduce((a, b) -> a + "," + b).orElse("(none)"));
        DiagnosticPublisher publisher = new DiagnosticPublisher();
        PositionMapper mapper = new PositionMapper(content);
        List<org.eclipse.lsp4j.Diagnostic> lsp = publisher.toLspDiagnostics(core, mapper);
        boolean hasNonZeroWidthRefDiag = false;
        for (org.eclipse.lsp4j.Diagnostic d : lsp) {
            if (!"SEM-REF-001".equals(d.getCode().getLeft())) {
                continue;
            }
            Range r = d.getRange();
            if (r.getEnd().getLine() > r.getStart().getLine()
                    || r.getEnd().getCharacter() > r.getStart().getCharacter()) {
                hasNonZeroWidthRefDiag = true;
                break;
            }
        }
        assertTrue(hasNonZeroWidthRefDiag,
                "SEM-REF-001 LSP diagnostic must have a non-zero-width range so it renders");
    }
}
