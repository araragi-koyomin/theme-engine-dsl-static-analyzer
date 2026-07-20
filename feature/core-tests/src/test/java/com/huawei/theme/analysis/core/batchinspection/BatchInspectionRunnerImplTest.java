package com.huawei.theme.analysis.core.batchinspection;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.concurrent.atomic.AtomicReference;

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
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProvider;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilder;
import com.huawei.theme.analysis.core.semanticanalysis.VerboseCollector;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.shared.model.FixActionType;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;
import com.huawei.theme.analysis.core.syntaxanalysis.DslAstProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

class BatchInspectionRunnerImplTest {

    private StubDslFileMatcher stubMatcher;
    private StubAstProvider stubAstProvider;
    private StubDiagnosticProvider stubDiagnosticProvider;
    private StubQuickFixProvider stubQuickFixProvider;
    private StubSymbolTableBuilder stubSymbolTableBuilder;
    private StubRuleRepository stubRuleRepository;
    private InspectionConfig defaultConfig;
    private BatchInspectionRunnerImpl runner;

    @BeforeEach
    void setUp() {
        stubMatcher = new StubDslFileMatcher(true);
        stubAstProvider = new StubAstProvider();
        stubDiagnosticProvider = new StubDiagnosticProvider();
        stubQuickFixProvider = new StubQuickFixProvider();
        stubSymbolTableBuilder = new StubSymbolTableBuilder();
        stubRuleRepository = new StubRuleRepository();
        defaultConfig = InspectionConfig.builder()
                .pipelineMode(PipelineMode.FULL).typeCheck(true).build();
        runner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, stubDiagnosticProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
    }

    @Test
    void constructorRejectsNullFileMatcher() {
        assertThrows(NullPointerException.class, () ->
                new BatchInspectionRunnerImpl(
                        null, stubAstProvider, stubDiagnosticProvider,
                        stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                        defaultConfig));
    }

    @Test
    void constructorRejectsNullAstProvider() {
        assertThrows(NullPointerException.class, () ->
                new BatchInspectionRunnerImpl(
                        stubMatcher, null, stubDiagnosticProvider,
                        stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                        defaultConfig));
    }

    @Test
    void constructorRejectsNullDiagnosticProvider() {
        assertThrows(NullPointerException.class, () ->
                new BatchInspectionRunnerImpl(
                        stubMatcher, stubAstProvider, null,
                        stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                        defaultConfig));
    }

    @Test
    void constructorRejectsNullQuickFixProvider() {
        assertThrows(NullPointerException.class, () ->
                new BatchInspectionRunnerImpl(
                        stubMatcher, stubAstProvider, stubDiagnosticProvider,
                        null, stubSymbolTableBuilder, stubRuleRepository,
                        defaultConfig));
    }

    @Test
    void constructorRejectsNullSymbolTableBuilder() {
        assertThrows(NullPointerException.class, () ->
                new BatchInspectionRunnerImpl(
                        stubMatcher, stubAstProvider, stubDiagnosticProvider,
                        stubQuickFixProvider, null, stubRuleRepository,
                        defaultConfig));
    }

    @Test
    void constructorRejectsNullRuleRepository() {
        assertThrows(NullPointerException.class, () ->
                new BatchInspectionRunnerImpl(
                        stubMatcher, stubAstProvider, stubDiagnosticProvider,
                        stubQuickFixProvider, stubSymbolTableBuilder, null,
                        defaultConfig));
    }

    @Test
    void constructorNullParamHasDescriptiveMessage() {
        NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new BatchInspectionRunnerImpl(
                        null, stubAstProvider, stubDiagnosticProvider,
                        stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                        defaultConfig));
        assertTrue(ex.getMessage().contains("fileMatcher"));
    }

    @Test
    void runOnFileRejectsNullFilePath() {
        assertThrows(NullPointerException.class, () -> runner.runOnFile(null));
    }

    @Test
    void runOnDirectoryRejectsNullDirectoryPath() {
        assertThrows(NullPointerException.class, () -> runner.runOnDirectory(null));
    }

    @Test
    void runOnFileSkipsNonDslFile() {
        Path tempFile = createTempXmlFile("<html><body>not dsl</body></html>");
        StubDslFileMatcher nonDslMatcher = new StubDslFileMatcher(false);
        BatchInspectionRunnerImpl nonDslRunner = new BatchInspectionRunnerImpl(
                nonDslMatcher, stubAstProvider, stubDiagnosticProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        BatchInspectionResult result = nonDslRunner.runOnFile(tempFile.toString());
        assertEquals(0, result.getTotalFiles());
        assertEquals(1, result.getSkippedFiles());
        assertEquals(0, result.getFileResults().size());
        assertEquals(0, result.getErrorCount());
        assertEquals(0, result.getWarningCount());
        assertEquals(0, result.getInfoCount());
    }

    @Test
    void runOnFileSkipsNonDslFileDoesNotInvokeAstOrDiagnostic() {
        Path tempFile = createTempXmlFile("<html/>");
        AtomicInteger astCount = new AtomicInteger(0);
        AtomicInteger diagCount = new AtomicInteger(0);
        StubAstProvider trackingAst = new StubAstProvider() {
            @Override
            public DslFileNode getDslAst(String filePath, String content) {
                astCount.incrementAndGet();
                return super.getDslAst(filePath, content);
            }
        };
        StubDiagnosticProvider trackingDiag = new StubDiagnosticProvider() {
            @Override
            public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder stb, PipelineMode mode, InspectionConfig config, VerboseCollector collector) {
                diagCount.incrementAndGet();
                return super.analyze(ast, ruleRepo, stb, mode, config, collector);
            }
        };
        StubDslFileMatcher falseMatcher = new StubDslFileMatcher(false);
        BatchInspectionRunnerImpl skipRunner = new BatchInspectionRunnerImpl(
                falseMatcher, trackingAst, trackingDiag,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        skipRunner.runOnFile(tempFile.toString());
        assertEquals(0, astCount.get());
        assertEquals(0, diagCount.get());
    }

    @Test
    void runOnFileAnalyzesDslFile() {
        Path tempFile = createTempXmlFile("<Lockscreen/>");
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(1, result.getTotalFiles());
        assertEquals(0, result.getSkippedFiles());
        assertEquals(1, result.getFileResults().size());
        assertEquals("SEM-REF-001", result.getFileResults().get(0).getDiagnostics().get(0).getRuleId());
    }

    @Test
    void runOnFileCountsSeverityCorrectly() {
        Path tempFile = createTempXmlFile("<Lockscreen/>");
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(1, result.getErrorCount());
        assertEquals(0, result.getWarningCount());
        assertEquals(0, result.getInfoCount());
    }

    @Test
    void runOnFileThrowsExceptionForUnreadableFile() {
        assertThrows(BatchInspectionException.class, () -> runner.runOnFile("/nonexistent/path/test.xml"));
    }

    @Test
    void runOnDirectoryScansXmlFiles() {
        Path dir = createTempDirWithFiles();
        StubDslFileMatcher contentMatcher = new StubDslFileMatcher(true) {
            @Override
            public boolean isDslFile(String filePath, String content) {
                return content.contains("Lockscreen");
            }
        };
        BatchInspectionRunnerImpl dirRunner = new BatchInspectionRunnerImpl(
                contentMatcher, stubAstProvider, stubDiagnosticProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        BatchInspectionResult result = dirRunner.runOnDirectory(dir.toString());
        assertEquals(1, result.getTotalFiles());
        assertEquals(1, result.getSkippedFiles());
        assertEquals(1, result.getErrorCount());
    }

    @Test
    void runOnDirectoryThrowsExceptionForNonexistentDir() {
        assertThrows(BatchInspectionException.class, () -> runner.runOnDirectory("/nonexistent/directory"));
    }

    @Test
    void runOnFileWithMixedSeverityDiagnostics() {
        AtomicInteger invocationCount = new AtomicInteger(0);
        StubDiagnosticProvider mixedProvider = new StubDiagnosticProvider() {
            @Override
            public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder stb, PipelineMode mode, InspectionConfig config, VerboseCollector collector) {
                invocationCount.incrementAndGet();
                return List.of(
                        Diagnostic.builder().severity(DiagnosticSeverity.ERROR).ruleId("SEM-REF-001")
                                .message("ref error").filePath(ast.getFilePath()).line(10).column(5).build(),
                        Diagnostic.builder().severity(DiagnosticSeverity.WARNING).ruleId("SEM-SCOPE-001")
                                .message("scope warning").filePath(ast.getFilePath()).line(20).column(3).build(),
                        Diagnostic.builder().severity(DiagnosticSeverity.INFO).ruleId("SEM-INFO-001")
                                .message("info note").filePath(ast.getFilePath()).line(30).column(1).build(),
                        Diagnostic.builder().severity(DiagnosticSeverity.ERROR).ruleId("SEM-TYPE-001")
                                .message("type error").filePath(ast.getFilePath()).line(40).column(0).build()
                );
            }
        };
        BatchInspectionRunnerImpl mixedRunner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, mixedProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        Path tempFile = createTempXmlFile("<Lockscreen><Var name=\"x\"/></Lockscreen>");
        BatchInspectionResult result = mixedRunner.runOnFile(tempFile.toString());
        assertEquals(1, result.getTotalFiles());
        assertEquals(2, result.getErrorCount());
        assertEquals(1, result.getWarningCount());
        assertEquals(1, result.getInfoCount());
        assertEquals(4, result.getFileResults().get(0).getDiagnostics().size());
        assertEquals(1, invocationCount.get());
    }

    @Test
    void runOnFilePreservesFilePathInResult() {
        Path tempFile = createTempXmlFile("<Lockscreen/>");
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(tempFile.toString(), result.getFileResults().get(0).getFilePath());
    }

    @Test
    void runOnFileWithFixActions() {
        StubQuickFixProvider fixProvider = new StubQuickFixProvider() {
            @Override
            public List<FixAction> getFixActions(Diagnostic diagnostic) {
                return List.of(
                        FixAction.builder()
                                .fixType(FixActionType.ADD_ATTR)
                                .targetRange(TextRange.builder().startLine(1).startColumn(0).endLine(1).endColumn(10).build())
                                .replacementText("type=\"string\"")
                                .description("add type attribute")
                                .build(),
                        FixAction.builder()
                                .fixType(FixActionType.SET_VALUE)
                                .targetRange(TextRange.builder().startLine(2).startColumn(5).endLine(2).endColumn(15).build())
                                .replacementText("255")
                                .description("clamp value to range")
                                .build()
                );
            }
        };
        BatchInspectionRunnerImpl fixRunner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, stubDiagnosticProvider,
                fixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        Path tempFile = createTempXmlFile("<Lockscreen/>");
        BatchInspectionResult result = fixRunner.runOnFile(tempFile.toString());
        assertEquals(2, result.getFileResults().get(0).getFixActions().size());
        assertEquals(FixActionType.ADD_ATTR, result.getFileResults().get(0).getFixActions().get(0).getFixType());
        assertEquals(FixActionType.SET_VALUE, result.getFileResults().get(0).getFixActions().get(1).getFixType());
        assertEquals("add type attribute", result.getFileResults().get(0).getFixActions().get(0).getDescription());
        assertEquals("clamp value to range", result.getFileResults().get(0).getFixActions().get(1).getDescription());
    }

    @Test
    void runOnFileWithZeroDiagnostics() {
        StubDiagnosticProvider emptyProvider = new StubDiagnosticProvider() {
            @Override
            public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder stb, PipelineMode mode, InspectionConfig config, VerboseCollector collector) {
                return List.of();
            }
        };
        BatchInspectionRunnerImpl emptyRunner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, emptyProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        Path tempFile = createTempXmlFile("<Lockscreen/>");
        BatchInspectionResult result = emptyRunner.runOnFile(tempFile.toString());
        assertEquals(1, result.getTotalFiles());
        assertEquals(0, result.getErrorCount());
        assertEquals(0, result.getWarningCount());
        assertEquals(0, result.getInfoCount());
        assertEquals(0, result.getFileResults().get(0).getDiagnostics().size());
        assertEquals(0, result.getFileResults().get(0).getFixActions().size());
    }

    @Test
    void runOnDirectoryWithNestedSubdirectories() throws Exception {
        Path rootDir = Files.createTempDirectory("batch-nested-test");
        Path subDir = rootDir.resolve("subdir");
        Files.createDirectories(subDir);
        Files.writeString(rootDir.resolve("root_dsl.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        Files.writeString(rootDir.resolve("nondsl.xml"), "<html/>", StandardCharsets.UTF_8);
        Files.writeString(subDir.resolve("sub_dsl.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        Files.writeString(subDir.resolve("sub_nondsl.xml"), "<html/>", StandardCharsets.UTF_8);

        StubDslFileMatcher contentMatcher = new StubDslFileMatcher(true) {
            @Override
            public boolean isDslFile(String filePath, String content) {
                return content.contains("Lockscreen");
            }
        };
        BatchInspectionRunnerImpl dirRunner = new BatchInspectionRunnerImpl(
                contentMatcher, stubAstProvider, stubDiagnosticProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        BatchInspectionResult result = dirRunner.runOnDirectory(rootDir.toString());
        assertEquals(2, result.getTotalFiles());
        assertEquals(2, result.getSkippedFiles());
        assertEquals(2, result.getErrorCount());
        assertEquals(2, result.getFileResults().size());
    }

    @Test
    void runOnDirectoryWithNoXmlFiles() throws Exception {
        Path emptyDir = Files.createTempDirectory("batch-empty-dir");
        Files.writeString(emptyDir.resolve("readme.txt"), "this is not xml", StandardCharsets.UTF_8);
        Files.writeString(emptyDir.resolve("data.json"), "{}", StandardCharsets.UTF_8);
        BatchInspectionResult result = runner.runOnDirectory(emptyDir.toString());
        assertEquals(0, result.getTotalFiles());
        assertEquals(0, result.getSkippedFiles());
        assertEquals(0, result.getErrorCount());
        assertEquals(0, result.getFileResults().size());
    }

    @Test
    void runOnDirectoryWithAllDslFiles() throws Exception {
        Path dir = Files.createTempDirectory("batch-all-dsl");
        Files.writeString(dir.resolve("file1.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("file2.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("file3.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        BatchInspectionResult result = runner.runOnDirectory(dir.toString());
        assertEquals(3, result.getTotalFiles());
        assertEquals(0, result.getSkippedFiles());
        assertEquals(3, result.getErrorCount());
        assertEquals(3, result.getFileResults().size());
    }

    @Test
    void runOnDirectoryWithAllNonDslFiles() throws Exception {
        Path dir = Files.createTempDirectory("batch-all-nondsl");
        Files.writeString(dir.resolve("a.xml"), "<html/>", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("b.xml"), "<svg/>", StandardCharsets.UTF_8);
        StubDslFileMatcher falseMatcher = new StubDslFileMatcher(false);
        BatchInspectionRunnerImpl falseRunner = new BatchInspectionRunnerImpl(
                falseMatcher, stubAstProvider, stubDiagnosticProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        BatchInspectionResult result = falseRunner.runOnDirectory(dir.toString());
        assertEquals(0, result.getTotalFiles());
        assertEquals(2, result.getSkippedFiles());
        assertEquals(0, result.getErrorCount());
        assertEquals(0, result.getFileResults().size());
    }

    @Test
    void runOnDirectoryWithMixedSeverityAcrossFiles() throws Exception {
        Path dir = Files.createTempDirectory("batch-mixed-sev");
        Files.writeString(dir.resolve("f1.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("f2.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        AtomicInteger fileIndex = new AtomicInteger(0);
        StubDiagnosticProvider alternatingProvider = new StubDiagnosticProvider() {
            @Override
            public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder stb, PipelineMode mode, InspectionConfig config, VerboseCollector collector) {
                int idx = fileIndex.getAndIncrement();
                if (idx == 0) {
                    return List.of(
                            Diagnostic.builder().severity(DiagnosticSeverity.ERROR).ruleId("E1")
                                    .message("error").filePath(ast.getFilePath()).line(1).column(0).build(),
                            Diagnostic.builder().severity(DiagnosticSeverity.WARNING).ruleId("W1")
                                    .message("warn").filePath(ast.getFilePath()).line(2).column(0).build()
                    );
                } else {
                    return List.of(
                            Diagnostic.builder().severity(DiagnosticSeverity.INFO).ruleId("I1")
                                    .message("info").filePath(ast.getFilePath()).line(3).column(0).build()
                    );
                }
            }
        };
        BatchInspectionRunnerImpl mixedRunner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, alternatingProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        BatchInspectionResult result = mixedRunner.runOnDirectory(dir.toString());
        assertEquals(2, result.getTotalFiles());
        assertEquals(1, result.getErrorCount());
        assertEquals(1, result.getWarningCount());
        assertEquals(1, result.getInfoCount());
    }

    @Test
    void runOnFileDelegatesCorrectlyToAstProvider() throws Exception {
        Path tempFile = createTempXmlFile("<Lockscreen><Var name=\"x\"/></Lockscreen>");
        String expectedContent = Files.readString(tempFile, StandardCharsets.UTF_8);
        AtomicInteger astCallCount = new AtomicInteger(0);
        StubAstProvider trackingAstProvider = new StubAstProvider() {
            @Override
            public DslFileNode getDslAst(String filePath, String content) {
                astCallCount.incrementAndGet();
                assertEquals(tempFile.toString(), filePath);
                assertEquals(expectedContent, content);
                DslFileNode node = new DslFileNode();
                node.setFilePath(filePath);
                node.setText(content);
                return node;
            }
        };
        BatchInspectionRunnerImpl trackingRunner = new BatchInspectionRunnerImpl(
                stubMatcher, trackingAstProvider, stubDiagnosticProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        trackingRunner.runOnFile(tempFile.toString());
        assertEquals(1, astCallCount.get());
    }

    @Test
    void runOnFileDelegatesCorrectlyToDiagnosticProvider() throws Exception {
        Path tempFile = createTempXmlFile("<Lockscreen/>");
        AtomicInteger diagCallCount = new AtomicInteger(0);
        StubDiagnosticProvider trackingDiagProvider = new StubDiagnosticProvider() {
            @Override
            public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder stb, PipelineMode mode, InspectionConfig config, VerboseCollector collector) {
                diagCallCount.incrementAndGet();
                assertNotNull(ast);
                assertEquals(tempFile.toString(), ast.getFilePath());
                assertNotNull(ruleRepo);
                assertNotNull(stb);
                return super.analyze(ast, ruleRepo, stb, mode, config, collector);
            }
        };
        BatchInspectionRunnerImpl trackingRunner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, trackingDiagProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        trackingRunner.runOnFile(tempFile.toString());
        assertEquals(1, diagCallCount.get());
    }

    @Test
    void runOnFileDelegatesCorrectlyToQuickFixProvider() throws Exception {
        Path tempFile = createTempXmlFile("<Lockscreen/>");
        AtomicInteger fixCallCount = new AtomicInteger(0);
        StubQuickFixProvider trackingFixProvider = new StubQuickFixProvider() {
            @Override
            public List<FixAction> getFixActions(Diagnostic diagnostic) {
                fixCallCount.incrementAndGet();
                assertNotNull(diagnostic);
                return List.of();
            }
        };
        BatchInspectionRunnerImpl trackingRunner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, stubDiagnosticProvider,
                trackingFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        trackingRunner.runOnFile(tempFile.toString());
        assertEquals(1, fixCallCount.get());
    }

    @Test
    void runOnDirectorySkipsNonXmlExtensionFiles() throws Exception {
        Path dir = Files.createTempDirectory("batch-ext-filter");
        Files.writeString(dir.resolve("theme.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("config.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("readme.md"), "# readme", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("style.css"), "body {}", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("script.js"), "var x = 1;", StandardCharsets.UTF_8);
        BatchInspectionResult result = runner.runOnDirectory(dir.toString());
        assertEquals(1, result.getTotalFiles());
        assertEquals(0, result.getSkippedFiles());
        assertEquals(1, result.getFileResults().size());
    }

    @Test
    void runOnDirectoryCaseInsensitiveXmlExtension() throws Exception {
        Path dir = Files.createTempDirectory("batch-case-ext");
        Files.writeString(dir.resolve("lower.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        Path upperFile = dir.resolve("upper.XML");
        Files.writeString(upperFile, "<Lockscreen/>", StandardCharsets.UTF_8);
        BatchInspectionResult result = runner.runOnDirectory(dir.toString());
        assertEquals(2, result.getTotalFiles());
        assertEquals(2, result.getFileResults().size());
    }

    @Test
    void runOnDirectoryEmptyDirectory() throws Exception {
        Path emptyDir = Files.createTempDirectory("batch-truly-empty");
        BatchInspectionResult result = runner.runOnDirectory(emptyDir.toString());
        assertEquals(0, result.getTotalFiles());
        assertEquals(0, result.getSkippedFiles());
        assertEquals(0, result.getFileResults().size());
    }

    @Test
    void exceptionMessageIncludesFilePath() {
        String badPath = "/nonexistent/path/test.xml";
        BatchInspectionException ex = assertThrows(BatchInspectionException.class,
                () -> runner.runOnFile(badPath));
        assertTrue(ex.getMessage().contains(badPath));
    }

    @Test
    void exceptionForDirectoryIncludesPathAndCause() {
        String badDir = "/nonexistent/directory";
        BatchInspectionException ex = assertThrows(BatchInspectionException.class,
                () -> runner.runOnDirectory(badDir));
        assertTrue(ex.getMessage().contains(badDir));
        assertNotNull(ex.getCause());
    }

    @Test
    void runOnDirectoryDoesNotCountSkippedInSeverityTotals() throws Exception {
        Path dir = Files.createTempDirectory("batch-skip-sev");
        Files.writeString(dir.resolve("dsl.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("nondsl1.xml"), "<html/>", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("nondsl2.xml"), "<svg/>", StandardCharsets.UTF_8);
        StubDslFileMatcher contentMatcher = new StubDslFileMatcher(true) {
            @Override
            public boolean isDslFile(String filePath, String content) {
                return content.contains("Lockscreen");
            }
        };
        BatchInspectionRunnerImpl dirRunner = new BatchInspectionRunnerImpl(
                contentMatcher, stubAstProvider, stubDiagnosticProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        BatchInspectionResult result = dirRunner.runOnDirectory(dir.toString());
        assertEquals(1, result.getTotalFiles());
        assertEquals(2, result.getSkippedFiles());
        assertEquals(1, result.getErrorCount());
        assertEquals(0, result.getWarningCount());
    }

    @Test
    void runOnFilePipelineCallsEachComponentOnce() {
        Path tempFile = createTempXmlFile("<Lockscreen/>");
        AtomicInteger astCount = new AtomicInteger(0);
        AtomicInteger diagCount = new AtomicInteger(0);
        AtomicInteger fixCount = new AtomicInteger(0);
        AtomicInteger matchCount = new AtomicInteger(0);
        StubDslFileMatcher trackingMatcher = new StubDslFileMatcher(true) {
            @Override
            public boolean isDslFile(String filePath, String content) {
                matchCount.incrementAndGet();
                return true;
            }
        };
        StubAstProvider trackingAst = new StubAstProvider() {
            @Override
            public DslFileNode getDslAst(String filePath, String content) {
                astCount.incrementAndGet();
                return super.getDslAst(filePath, content);
            }
        };
        StubDiagnosticProvider trackingDiag = new StubDiagnosticProvider() {
            @Override
            public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder stb, PipelineMode mode, InspectionConfig config, VerboseCollector collector) {
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
        BatchInspectionRunnerImpl pipelineRunner = new BatchInspectionRunnerImpl(
                trackingMatcher, trackingAst, trackingDiag,
                trackingFix, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        pipelineRunner.runOnFile(tempFile.toString());
        assertEquals(1, matchCount.get());
        assertEquals(1, astCount.get());
        assertEquals(1, diagCount.get());
        assertEquals(1, fixCount.get());
    }

    @Test
    void runOnDirectoryPipelineCallsCorrectTimes() throws Exception {
        Path dir = Files.createTempDirectory("batch-pipeline-dir");
        Files.writeString(dir.resolve("a.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("b.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        AtomicInteger diagCount = new AtomicInteger(0);
        StubDiagnosticProvider trackingDiag = new StubDiagnosticProvider() {
            @Override
            public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder stb, PipelineMode mode, InspectionConfig config, VerboseCollector collector) {
                diagCount.incrementAndGet();
                return super.analyze(ast, ruleRepo, stb, mode, config, collector);
            }
        };
        BatchInspectionRunnerImpl pipelineRunner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, trackingDiag,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        pipelineRunner.runOnDirectory(dir.toString());
        assertEquals(2, diagCount.get());
    }

    @Test
    void runOnFileContentPassedCorrectlyToMatcher() throws Exception {
        Path tempFile = createTempXmlFile("<Lockscreen attr=\"value\"/>");
        String expectedContent = Files.readString(tempFile, StandardCharsets.UTF_8);
        AtomicReference<String> capturedContent = new AtomicReference<>();
        StubDslFileMatcher capturingMatcher = new StubDslFileMatcher(true) {
            @Override
            public boolean isDslFile(String filePath, String content) {
                capturedContent.set(content);
                return true;
            }
        };
        BatchInspectionRunnerImpl capturingRunner = new BatchInspectionRunnerImpl(
                capturingMatcher, stubAstProvider, stubDiagnosticProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        capturingRunner.runOnFile(tempFile.toString());
        assertEquals(expectedContent, capturedContent.get());
    }

    @Test
    void runOnFileContentPassedCorrectlyToAstProvider() throws Exception {
        Path tempFile = createTempXmlFile("<Lockscreen><Var/></Lockscreen>");
        String expectedContent = Files.readString(tempFile, StandardCharsets.UTF_8);
        AtomicReference<String> capturedContent = new AtomicReference<>();
        StubAstProvider capturingAst = new StubAstProvider() {
            @Override
            public DslFileNode getDslAst(String filePath, String content) {
                capturedContent.set(content);
                return super.getDslAst(filePath, content);
            }
        };
        BatchInspectionRunnerImpl capturingRunner = new BatchInspectionRunnerImpl(
                stubMatcher, capturingAst, stubDiagnosticProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        capturingRunner.runOnFile(tempFile.toString());
        assertEquals(expectedContent, capturedContent.get());
    }

    @Test
    void runOnDirectoryWithDeeplyNestedStructure() throws Exception {
        Path root = Files.createTempDirectory("batch-deep");
        Path l1 = root.resolve("level1");
        Path l2 = l1.resolve("level2");
        Path l3 = l2.resolve("level3");
        Files.createDirectories(l3);
        Files.writeString(root.resolve("root.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        Files.writeString(l1.resolve("l1.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        Files.writeString(l2.resolve("l2.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        Files.writeString(l3.resolve("l3.xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        BatchInspectionResult result = runner.runOnDirectory(root.toString());
        assertEquals(4, result.getTotalFiles());
        assertEquals(4, result.getFileResults().size());
    }

    @Test
    void runOnDirectoryWithOnlyDslFilesNoThrows() throws Exception {
        Path dir = Files.createTempDirectory("batch-only-dsl");
        StubDiagnosticProvider multiDiagProvider = new StubDiagnosticProvider() {
            @Override
            public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder stb, PipelineMode mode, InspectionConfig config, VerboseCollector collector) {
                return List.of(
                        Diagnostic.builder().severity(DiagnosticSeverity.ERROR).ruleId("E1")
                                .message("err").filePath(ast.getFilePath()).line(1).column(0).build(),
                        Diagnostic.builder().severity(DiagnosticSeverity.WARNING).ruleId("W1")
                                .message("warn").filePath(ast.getFilePath()).line(2).column(0).build()
                );
            }
        };
        BatchInspectionRunnerImpl onlyDslRunner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, multiDiagProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        for (int i = 0; i < 5; i++) {
            Files.writeString(dir.resolve("dsl" + i + ".xml"), "<Lockscreen/>", StandardCharsets.UTF_8);
        }
        BatchInspectionResult result = onlyDslRunner.runOnDirectory(dir.toString());
        assertEquals(5, result.getTotalFiles());
        assertEquals(0, result.getSkippedFiles());
        assertEquals(5, result.getErrorCount());
        assertEquals(5, result.getWarningCount());
        assertEquals(0, result.getInfoCount());
    }

    @Test
    void skippedFileResultHasEmptyDiagnostics() {
        Path tempFile = createTempXmlFile("<html/>");
        StubDslFileMatcher falseMatcher = new StubDslFileMatcher(false);
        BatchInspectionRunnerImpl skipRunner = new BatchInspectionRunnerImpl(
                falseMatcher, stubAstProvider, stubDiagnosticProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository,
                defaultConfig);
        BatchInspectionResult result = skipRunner.runOnFile(tempFile.toString());
        assertEquals(0, result.getFileResults().size());
        assertEquals(0, result.getErrorCount());
        assertEquals(0, result.getWarningCount());
        assertEquals(0, result.getInfoCount());
    }

    private Path createTempXmlFile(String content) {
        try {
            Path dir = Files.createTempDirectory("batch-test");
            Path file = dir.resolve("test.xml");
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Path createTempDirWithFiles() {
        try {
            Path dir = Files.createTempDirectory("batch-dir-test");
            Path dslFile = dir.resolve("dsl.xml");
            Files.writeString(dslFile, "<Lockscreen/>", StandardCharsets.UTF_8);
            Path nonDslFile = dir.resolve("nondsl.xml");
            Files.writeString(nonDslFile, "<html/>", StandardCharsets.UTF_8);
            return dir;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        @Override
        public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder symbolTableBuilder,
                                        PipelineMode mode, InspectionConfig config, VerboseCollector collector) {
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
