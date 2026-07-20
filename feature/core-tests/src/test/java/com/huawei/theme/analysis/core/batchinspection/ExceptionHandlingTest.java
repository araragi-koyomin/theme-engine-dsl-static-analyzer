package com.huawei.theme.analysis.core.batchinspection;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.batchinspection.model.FileDiagnosticResult;
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
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProvider;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilder;
import com.huawei.theme.analysis.core.semanticanalysis.VerboseCollector;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.syntaxanalysis.DslAstProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExceptionHandlingTest {

    private InspectionConfig defaultConfig;

    @BeforeEach
    void setUp() {
        defaultConfig = InspectionConfig.builder()
                .pipelineMode(PipelineMode.FULL).typeCheck(true).build();
    }

    @Test
    void astFailureProducesInternalAstErrorDiagnostic() {
        DslAstProvider throwingAst = (filePath, content) -> {
            throw new RuntimeException("AST build boom");
        };
        BatchInspectionRunnerImpl runner = newRunner(throwingAst,
                new NormalDiagnosticProvider(), new NormalQuickFixProvider());

        BatchInspectionResult result = runner.runOnFile(createTempDslFile().toString());

        FileDiagnosticResult fileResult = result.getFileResults().get(0);
        assertTrue(fileResult.isHasInternalError(),
                "AST failure should set hasInternalError");
        boolean hasInternalAstError = fileResult.getDiagnostics().stream()
                .anyMatch(d -> d.getRuleId().equals("INTERNAL-AST-ERROR"));
        assertTrue(hasInternalAstError,
                "should contain INTERNAL-AST-ERROR diagnostic");
    }

    @Test
    void astFailureYieldsExitCode2() {
        DslAstProvider throwingAst = (filePath, content) -> {
            throw new RuntimeException("AST build boom");
        };
        BatchInspectionRunnerImpl runner = newRunner(throwingAst,
                new NormalDiagnosticProvider(), new NormalQuickFixProvider());

        BatchInspectionResult result = runner.runOnFile(createTempDslFile().toString());

        assertEquals(2, ExitCodeCalculator.compute(result),
                "internal error should yield exit code 2");
    }

    @Test
    void diagnosticProviderFailureProducesInternalAnalyzerErrorDiagnostic() {
        DiagnosticProvider throwingDiag = (ast, ruleRepo, stb, mode, config, collector) -> {
            throw new RuntimeException("Analyzer boom");
        };
        BatchInspectionRunnerImpl runner = newRunner(new NormalAstProvider(),
                throwingDiag, new NormalQuickFixProvider());

        BatchInspectionResult result = runner.runOnFile(createTempDslFile().toString());

        FileDiagnosticResult fileResult = result.getFileResults().get(0);
        assertTrue(fileResult.isHasInternalError(),
                "analyzer failure should set hasInternalError");
        boolean hasInternalAnalyzerError = fileResult.getDiagnostics().stream()
                .anyMatch(d -> d.getRuleId().equals("INTERNAL-ANALYZER-ERROR"));
        assertTrue(hasInternalAnalyzerError,
                "should contain INTERNAL-ANALYZER-ERROR diagnostic");
    }

    @Test
    void diagnosticProviderFailureYieldsExitCode2() {
        DiagnosticProvider throwingDiag = (ast, ruleRepo, stb, mode, config, collector) -> {
            throw new RuntimeException("Analyzer boom");
        };
        BatchInspectionRunnerImpl runner = newRunner(new NormalAstProvider(),
                throwingDiag, new NormalQuickFixProvider());

        BatchInspectionResult result = runner.runOnFile(createTempDslFile().toString());

        assertEquals(2, ExitCodeCalculator.compute(result));
    }

    @Test
    void quickFixProviderFailureSetsHasInternalErrorAndPreservesDiagnostics() {
        QuickFixProvider throwingFix = diagnostic -> {
            throw new RuntimeException("Fix boom");
        };
        BatchInspectionRunnerImpl runner = newRunner(new NormalAstProvider(),
                new NormalDiagnosticProvider(), throwingFix);

        BatchInspectionResult result = runner.runOnFile(createTempDslFile().toString());

        FileDiagnosticResult fileResult = result.getFileResults().get(0);
        assertTrue(fileResult.isHasInternalError(),
                "fix provider failure should set hasInternalError");
        assertFalse(fileResult.getDiagnostics().isEmpty(),
                "diagnostics should be preserved from analyze step");
        assertEquals(0, fileResult.getFixActions().size(),
                "fixActions should be empty on failure");
    }

    @Test
    void quickFixProviderFailureYieldsExitCode2() {
        QuickFixProvider throwingFix = diagnostic -> {
            throw new RuntimeException("Fix boom");
        };
        BatchInspectionRunnerImpl runner = newRunner(new NormalAstProvider(),
                new NormalDiagnosticProvider(), throwingFix);

        BatchInspectionResult result = runner.runOnFile(createTempDslFile().toString());

        assertEquals(2, ExitCodeCalculator.compute(result));
    }

    @Test
    void normalFileHasNoInternalErrorAndExitCodeNotTwo() {
        BatchInspectionRunnerImpl runner = newRunner(new NormalAstProvider(),
                new NormalDiagnosticProvider(), new NormalQuickFixProvider());

        BatchInspectionResult result = runner.runOnFile(createTempDslFile().toString());

        FileDiagnosticResult fileResult = result.getFileResults().get(0);
        assertFalse(fileResult.isHasInternalError(),
                "normal file should not have internal error");
        assertTrue(ExitCodeCalculator.compute(result) != 2,
                "normal file should not yield exit code 2");
    }

    @Test
    void directoryWithInternalErrorAggregatesHasInternalErrors() throws Exception {
        Path dir = Files.createTempDirectory("exc-dir-throw");
        Files.writeString(dir.resolve("a.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("b.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);

        DslAstProvider throwingAst = (filePath, content) -> {
            throw new RuntimeException("AST build boom");
        };
        BatchInspectionRunnerImpl runner = newRunner(throwingAst,
                new NormalDiagnosticProvider(), new NormalQuickFixProvider());

        BatchInspectionResult result = runner.runOnDirectory(dir.toString());

        assertTrue(result.isHasInternalErrors(),
                "directory with internal errors should aggregate hasInternalErrors");
        assertEquals(2, ExitCodeCalculator.compute(result));
    }

    @Test
    void directoryWithoutInternalErrorsHasNoHasInternalErrors() throws Exception {
        Path dir = Files.createTempDirectory("exc-dir-normal");
        Files.writeString(dir.resolve("a.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);

        BatchInspectionRunnerImpl runner = newRunner(new NormalAstProvider(),
                new NormalDiagnosticProvider(), new NormalQuickFixProvider());

        BatchInspectionResult result = runner.runOnDirectory(dir.toString());

        assertFalse(result.isHasInternalErrors(),
                "normal directory should not have hasInternalErrors");
        assertTrue(ExitCodeCalculator.compute(result) != 2);
    }

    private BatchInspectionRunnerImpl newRunner(DslAstProvider astProvider,
                                                DiagnosticProvider diagProvider,
                                                QuickFixProvider fixProvider) {
        DslFileMatcher alwaysDsl = (filePath, content) -> true;
        return new BatchInspectionRunnerImpl(
                alwaysDsl, astProvider, diagProvider, fixProvider,
                new EmptySymbolTableBuilder(), new EmptyRuleRepository(),
                defaultConfig);
    }

    private Path createTempDslFile() {
        try {
            Path dir = Files.createTempDirectory("exc-test");
            Path file = dir.resolve("test.xml");
            Files.writeString(file, "<Lockscreen/>", StandardCharsets.UTF_8);
            return file;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class NormalAstProvider implements DslAstProvider {
        @Override
        public DslFileNode getDslAst(String filePath, String content) {
            DslFileNode node = new DslFileNode();
            node.setFilePath(filePath);
            node.setText(content);
            return node;
        }
    }

    private static class NormalDiagnosticProvider implements DiagnosticProvider {
        @Override
        public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo,
                                        SymbolTableBuilder stb,
                                        com.huawei.theme.analysis.core.cli.PipelineMode mode,
                                        com.huawei.theme.analysis.core.cli.InspectionConfig config,
                                        VerboseCollector collector) {
            return List.of(Diagnostic.builder()
                    .severity(DiagnosticSeverity.ERROR)
                    .ruleId("SEM-REF-001")
                    .message("test diagnostic")
                    .filePath(ast.getFilePath())
                    .line(1)
                    .column(0)
                    .build());
        }
    }

    private static class NormalQuickFixProvider implements QuickFixProvider {
        @Override
        public List<FixAction> getFixActions(Diagnostic diagnostic) {
            return List.of();
        }
    }

    private static class EmptySymbolTableBuilder implements SymbolTableBuilder {
        @Override
        public SymbolTable buildGlobal(DslFileNode fileNode, RuleRepository ruleRepository) {
            return SymbolTable.builder().build();
        }

        @Override
        public SymbolTable build(DslElementNode elementNode, SymbolTable parent,
                                RuleRepository ruleRepository) {
            return parent;
        }
    }

    private static class EmptyRuleRepository implements RuleRepository {
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
