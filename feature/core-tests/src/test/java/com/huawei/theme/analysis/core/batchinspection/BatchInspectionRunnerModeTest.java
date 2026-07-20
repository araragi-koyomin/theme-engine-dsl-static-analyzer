package com.huawei.theme.analysis.core.batchinspection;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.cli.PipelineMode;
import com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary;
import com.huawei.theme.analysis.core.fileidentification.DslFileMatcher;
import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.QuickFixProvider;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProvider;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilder;
import com.huawei.theme.analysis.core.semanticanalysis.VerboseCollector;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.syntaxanalysis.DslAstProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchInspectionRunnerModeTest {

    private Path tempFile;
    private Path tempDir;
    private StubDslFileMatcher stubMatcher;
    private StubAstProvider stubAstProvider;
    private StubDiagnosticProvider stubDiagnosticProvider;
    private StubQuickFixProvider stubQuickFixProvider;
    private StubSymbolTableBuilder stubSymbolTableBuilder;
    private StubRuleRepository stubRuleRepository;

    @BeforeEach
    void setUp() throws Exception {
        stubMatcher = new StubDslFileMatcher(true);
        stubAstProvider = new StubAstProvider();
        stubDiagnosticProvider = new StubDiagnosticProvider();
        stubQuickFixProvider = new StubQuickFixProvider();
        stubSymbolTableBuilder = new StubSymbolTableBuilder();
        stubRuleRepository = new StubRuleRepository();

        tempDir = Files.createTempDirectory("mode-test");
        String dslContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Lockscreen xmlns:sys=\"http://www.huawei.com/system\">\n" +
                "  <Var name=\"testVar\" expression=\"1+2\"/>\n" +
                "  <Image src=\"@testVar\"/>\n" +
                "</Lockscreen>";
        tempFile = tempDir.resolve("test_theme.xml");
        Files.writeString(tempFile, dslContent, StandardCharsets.UTF_8);
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(tempFile);
        Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
    }

    private BatchInspectionRunnerImpl createRunner(InspectionConfig config) {
        return new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, stubDiagnosticProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                config);
    }

    private InspectionConfig defaultConfig() {
        return InspectionConfig.builder()
                .pipelineMode(PipelineMode.FULL).typeCheck(true).build();
    }

    @Test
    void syntaxOnlyModeSkipsSemanticDiagnostics() throws Exception {
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.SYNTAX_ONLY)
                .typeCheck(true)
                .build();
        BatchInspectionRunnerImpl runner = createRunner(config);
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(1, result.getTotalFiles());
        assertEquals(0, result.getErrorCount());
        assertEquals(0, result.getWarningCount());
        assertEquals(0, result.getInfoCount());
    }

    @Test
    void semanticOnlyModeIncludesSemanticButNotSyntaxErrors() throws Exception {
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.SEMANTIC_ONLY)
                .typeCheck(true)
                .build();
        BatchInspectionRunnerImpl runner = createRunner(config);
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(1, result.getTotalFiles());
        assertTrue(result.getFileResults().get(0).getDiagnostics().size() > 0);
    }

    @Test
    void fullModeIncludesAllDiagnostics() throws Exception {
        // FIX004 b2 P4: was theater — `getFixActions().size() >= 0` is
        // trivially true (any list size is >= 0). StubQuickFixProvider
        // returns List.of() (empty), so assert the real expected value 0.
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.FULL)
                .typeCheck(true)
                .build();
        BatchInspectionRunnerImpl runner = createRunner(config);
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(1, result.getTotalFiles());
        assertTrue(result.getFileResults().get(0).getDiagnostics().size() > 0);
        assertEquals(0, result.getFileResults().get(0).getFixActions().size(),
                "StubQuickFixProvider returns empty list; expected size 0");
    }

    @Test
    void noTypeCheckDisablesTypeAnalyzer() throws Exception {
        // FIX004 b2 P4: was theater — only asserted `totalFiles == 1`, never
        // verified that TypeAnalyzer was actually disabled (StubDiagnosticProvider
        // didn't branch on typeCheck, returning the same diagnostic regardless).
        // The test's claim "DisablesTypeAnalyzer" was untested. Now: stub
        // branches on config.isTypeCheck() (returning SEM-TYPE-001 only when
        // typeCheck=true to simulate TypeAnalyzer), and test asserts no
        // SEM-TYPE-* present when typeCheck=false.
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.FULL)
                .typeCheck(false)
                .build();
        BatchInspectionRunnerImpl runner = createRunner(config);
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(1, result.getTotalFiles());
        List<Diagnostic> diagnostics = result.getFileResults().get(0).getDiagnostics();
        boolean hasTypeDiag = diagnostics.stream()
                .anyMatch(d -> d.getRuleId().startsWith("SEM-TYPE"));
        assertFalse(hasTypeDiag,
                "typeCheck=false should disable TypeAnalyzer (no SEM-TYPE-* diagnostics); got: "
                        + diagnostics.stream().map(Diagnostic::getRuleId).toList());
    }

    @Test
    void syntaxOnlyModeInvokesDiagnosticProvider() throws Exception {
        AtomicInteger diagCount = new AtomicInteger(0);
        StubDiagnosticProvider trackingDiag = new StubDiagnosticProvider() {
            @Override
            public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder stb,
                                           PipelineMode mode, InspectionConfig config, VerboseCollector collector) {
                diagCount.incrementAndGet();
                return super.analyze(ast, ruleRepo, stb, mode, config, collector);
            }
        };
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.SYNTAX_ONLY)
                .typeCheck(true)
                .build();
        BatchInspectionRunnerImpl runner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, trackingDiag,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                config);
        runner.runOnFile(tempFile.toString());
        assertEquals(1, diagCount.get());
    }

    @Test
    void syntaxOnlyModeDoesNotInvokeQuickFixProvider() throws Exception {
        AtomicInteger fixCount = new AtomicInteger(0);
        StubQuickFixProvider trackingFix = new StubQuickFixProvider() {
            @Override
            public List<FixAction> getFixActions(Diagnostic diagnostic) {
                fixCount.incrementAndGet();
                return List.of();
            }
        };
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.SYNTAX_ONLY)
                .typeCheck(true)
                .build();
        BatchInspectionRunnerImpl runner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, stubDiagnosticProvider,
                trackingFix, stubSymbolTableBuilder, stubRuleRepository,
                config);
        runner.runOnFile(tempFile.toString());
        assertEquals(0, fixCount.get());
    }

    @Test
    void semanticOnlyModeDoesNotInvokeQuickFixProvider() throws Exception {
        AtomicInteger fixCount = new AtomicInteger(0);
        StubQuickFixProvider trackingFix = new StubQuickFixProvider() {
            @Override
            public List<FixAction> getFixActions(Diagnostic diagnostic) {
                fixCount.incrementAndGet();
                return List.of();
            }
        };
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.SEMANTIC_ONLY)
                .typeCheck(true)
                .build();
        BatchInspectionRunnerImpl runner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, stubDiagnosticProvider,
                trackingFix, stubSymbolTableBuilder, stubRuleRepository,
                config);
        runner.runOnFile(tempFile.toString());
        assertEquals(0, fixCount.get());
    }

    @Test
    void fullModeInvokesAllStages() throws Exception {
        AtomicInteger diagCount = new AtomicInteger(0);
        AtomicInteger fixCount = new AtomicInteger(0);
        StubDiagnosticProvider trackingDiag = new StubDiagnosticProvider() {
            @Override
            public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder stb,
                                           PipelineMode mode, InspectionConfig config, VerboseCollector collector) {
                diagCount.incrementAndGet();
                return super.analyze(ast, ruleRepo, stb, mode, config, collector);
            }
        };
        StubQuickFixProvider trackingFix = new StubQuickFixProvider() {
            @Override
            public List<FixAction> getFixActions(Diagnostic diagnostic) {
                fixCount.incrementAndGet();
                return List.of();
            }
        };
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.FULL)
                .typeCheck(true)
                .build();
        BatchInspectionRunnerImpl runner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, trackingDiag,
                trackingFix, stubSymbolTableBuilder, stubRuleRepository,
                config);
        runner.runOnFile(tempFile.toString());
        assertEquals(1, diagCount.get());
        // FIX004 b2: fixCount is 2 because StubDiagnosticProvider now returns
        // 2 diagnostics when typeCheck=true (SEM-REF-001 + SEM-TYPE-001 to
        // simulate TypeAnalyzer; see noTypeCheckDisablesTypeAnalyzer fix).
        assertEquals(2, fixCount.get());
    }

    @Test
    void constructorRejectsNullInspectionConfig() {
        try {
            new BatchInspectionRunnerImpl(
                    stubMatcher, stubAstProvider, stubDiagnosticProvider,
                    stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                    null);
            assertTrue(false, "Should have thrown NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("inspectionConfig"));
        }
    }

    @Test
    void nullPipelineModeDefaultsToFull() throws Exception {
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(null)
                .typeCheck(true)
                .build();
        BatchInspectionRunnerImpl runner = createRunner(config);
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(1, result.getTotalFiles());
        assertTrue(result.getFileResults().get(0).getDiagnostics().size() > 0);
    }

    @Test
    void astFailureProducesInternalAstError() throws Exception {
        StubAstProvider failingAst = new StubAstProvider() {
            @Override
            public DslFileNode getDslAst(String filePath, String content) {
                throw new RuntimeException("AST build failed");
            }
        };
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.FULL)
                .typeCheck(true)
                .build();
        BatchInspectionRunnerImpl runner = new BatchInspectionRunnerImpl(
                stubMatcher, failingAst, stubDiagnosticProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                config);
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(1, result.getTotalFiles());
        assertEquals(1, result.getErrorCount());
        assertEquals(1, result.getFileResults().get(0).getDiagnostics().size());
        assertEquals("INTERNAL-AST-ERROR",
                result.getFileResults().get(0).getDiagnostics().get(0).getRuleId());
        assertTrue(result.getFileResults().get(0).isHasInternalError());
    }

    @Test
    void diagnosticFailureProducesInternalAnalyzerError() throws Exception {
        StubDiagnosticProvider failingDiag = new StubDiagnosticProvider() {
            @Override
            public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder stb,
                                           PipelineMode mode, InspectionConfig config, VerboseCollector collector) {
                throw new RuntimeException("Diagnostic analysis failed");
            }
        };
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.FULL)
                .typeCheck(true)
                .build();
        BatchInspectionRunnerImpl runner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, failingDiag,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                config);
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(1, result.getTotalFiles());
        assertEquals(1, result.getErrorCount());
        assertEquals(1, result.getFileResults().get(0).getDiagnostics().size());
        assertEquals("INTERNAL-ANALYZER-ERROR",
                result.getFileResults().get(0).getDiagnostics().get(0).getRuleId());
        assertTrue(result.getFileResults().get(0).isHasInternalError());
    }

    @Test
    void quickFixFailureGracefullyDegraded() throws Exception {
        StubQuickFixProvider failingFix = new StubQuickFixProvider() {
            @Override
            public List<FixAction> getFixActions(Diagnostic diagnostic) {
                throw new RuntimeException("Quick fix failed");
            }
        };
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.FULL)
                .typeCheck(true)
                .build();
        BatchInspectionRunnerImpl runner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, stubDiagnosticProvider,
                failingFix, stubSymbolTableBuilder, stubRuleRepository,
                config);
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(1, result.getTotalFiles());
        assertTrue(result.getFileResults().get(0).getDiagnostics().size() > 0);
        assertEquals(0, result.getFileResults().get(0).getFixActions().size());
        assertTrue(result.getFileResults().get(0).isHasInternalError());
    }

    private static class StubDslFileMatcher implements DslFileMatcher {
        private final boolean result;

        StubDslFileMatcher(boolean result) {
            this.result = result;
        }

        @Override
        public boolean isDslFile(String filePath, String content) {
            return result;
        }
    }

    private static class StubAstProvider implements DslAstProvider {
        @Override
        public DslFileNode getDslAst(String filePath, String content) {
            DslFileNode node = new DslFileNode();
            node.setFilePath(filePath);
            node.setText(content);
            return node;
        }
    }

    private static class StubDiagnosticProvider implements DiagnosticProvider {
        // FIX004 b2 P4: added typeCheck branch so noTypeCheckDisablesTypeAnalyzer
        // can actually verify TypeAnalyzer is filtered out when typeCheck=false.
        // Returns SEM-REF-001 (non-TypeAnalyzer) always, plus SEM-TYPE-001 only
        // when typeCheck=true (simulating TypeAnalyzer contribution).
        @Override
        public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder symbolTableBuilder,
                                        PipelineMode mode, InspectionConfig config, VerboseCollector collector) {
            if (mode == PipelineMode.SYNTAX_ONLY) {
                return List.of();
            }
            Diagnostic refDiag = Diagnostic.builder()
                    .severity(DiagnosticSeverity.ERROR)
                    .ruleId("SEM-REF-001")
                    .message("test diagnostic")
                    .filePath(ast.getFilePath())
                    .line(1)
                    .column(0)
                    .build();
            if (config != null && !config.isTypeCheck()) {
                return List.of(refDiag);
            }
            Diagnostic typeDiag = Diagnostic.builder()
                    .severity(DiagnosticSeverity.ERROR)
                    .ruleId("SEM-TYPE-001")
                    .message("test type diagnostic")
                    .filePath(ast.getFilePath())
                    .line(2)
                    .column(0)
                    .build();
            return List.of(refDiag, typeDiag);
        }
    }

    private static class StubQuickFixProvider implements QuickFixProvider {
        @Override
        public List<FixAction> getFixActions(Diagnostic diagnostic) {
            return List.of();
        }
    }

    private static class StubSymbolTableBuilder implements SymbolTableBuilder {
        @Override
        public SymbolTable buildGlobal(DslFileNode fileNode, RuleRepository ruleRepository) {
            return SymbolTable.builder().build();
        }

        @Override
        public SymbolTable build(DslElementNode elementNode, SymbolTable parent, RuleRepository ruleRepository) {
            return parent;
        }
    }

    private static class StubRuleRepository implements RuleRepository {
        @Override
        public Optional<DslElementRule> getElementRule(String elementName) {
            return Optional.empty();
        }

        @Override
        public List<DslElementRule> getAllElementRules() {
            return List.of();
        }

        @Override
        public List<String> getAllElementNames() {
            return List.of();
        }

        @Override
        public List<String> getRootElementNames() {
            return List.of("Lockscreen");
        }

        @Override
        public Optional<AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName) {
            return Optional.empty();
        }

        @Override
        public Optional<String> resolveAttrAlias(String elementName, String attrName) {
            return Optional.empty();
        }

        @Override
        public Set<String> getCanonicalAttrNames(String elementName) {
            return Collections.emptySet();
        }

        @Override
        public List<String> getAllowedParents(String elementName) {
            return List.of();
        }

        @Override
        public List<String> getAllowedChildren(String elementName) {
            return List.of();
        }

        @Override
        public List<RuleConstraint> getConstraints(String elementName) {
            return List.of();
        }

        @Override
        public Optional<DslGlobalVar> getGlobalVar(String varName) {
            return Optional.empty();
        }

        @Override
        public List<DslGlobalVar> getAllGlobalVars() {
            return List.of();
        }

        @Override
        public Optional<RuleSource> getRuleSource(String ruleId) {
            return Optional.empty();
        }

        @Override
        public FunctionSignatureLibrary getFunctionSignatureLibrary() {
            return null;
        }
    }
}
