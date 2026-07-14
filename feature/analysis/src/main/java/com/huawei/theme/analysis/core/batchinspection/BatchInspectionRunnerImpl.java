package com.huawei.theme.analysis.core.batchinspection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.batchinspection.model.FileDiagnosticResult;
import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.cli.PipelineMode;
import com.huawei.theme.analysis.core.fileidentification.DslFileMatcher;
import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.QuickFixProvider;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProvider;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilder;
import com.huawei.theme.analysis.core.semanticanalysis.VerboseCollector;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.syntaxanalysis.DslAstProvider;

public class BatchInspectionRunnerImpl implements BatchInspectionRunner {

    private final DslFileMatcher fileMatcher;
    private final DslAstProvider astProvider;
    private final DiagnosticProvider diagnosticProvider;
    private final QuickFixProvider quickFixProvider;
    private final SymbolTableBuilder symbolTableBuilder;
    private final RuleRepository ruleRepository;
    private final InspectionConfig inspectionConfig;
    private final VerboseCollector verboseCollector;

    public BatchInspectionRunnerImpl(
            DslFileMatcher fileMatcher,
            DslAstProvider astProvider,
            DiagnosticProvider diagnosticProvider,
            QuickFixProvider quickFixProvider,
            SymbolTableBuilder symbolTableBuilder,
            RuleRepository ruleRepository,
            InspectionConfig inspectionConfig) {
        this(fileMatcher, astProvider, diagnosticProvider, quickFixProvider,
                symbolTableBuilder, ruleRepository, inspectionConfig, null);
    }

    public BatchInspectionRunnerImpl(
            DslFileMatcher fileMatcher,
            DslAstProvider astProvider,
            DiagnosticProvider diagnosticProvider,
            QuickFixProvider quickFixProvider,
            SymbolTableBuilder symbolTableBuilder,
            RuleRepository ruleRepository,
            InspectionConfig inspectionConfig,
            VerboseCollector verboseCollector) {
        this.fileMatcher = Objects.requireNonNull(fileMatcher, "fileMatcher must not be null");
        this.astProvider = Objects.requireNonNull(astProvider, "astProvider must not be null");
        this.diagnosticProvider = Objects.requireNonNull(diagnosticProvider, "diagnosticProvider must not be null");
        this.quickFixProvider = Objects.requireNonNull(quickFixProvider, "quickFixProvider must not be null");
        this.symbolTableBuilder = Objects.requireNonNull(symbolTableBuilder, "symbolTableBuilder must not be null");
        this.ruleRepository = Objects.requireNonNull(ruleRepository, "ruleRepository must not be null");
        this.inspectionConfig = Objects.requireNonNull(inspectionConfig, "inspectionConfig must not be null");
        this.verboseCollector = verboseCollector;
    }

    @Override
    public BatchInspectionResult runOnFile(String filePath) {
        Objects.requireNonNull(filePath, "filePath must not be null");
        String content = readFileContent(filePath);
        if (content == null) {
            throw new BatchInspectionException("File not found or unreadable: " + filePath);
        }
        if (!fileMatcher.isDslFile(filePath, content)) {
            return BatchInspectionResult.builder()
                    .totalFiles(0).skippedFiles(1).errorCount(0).warningCount(0).infoCount(0)
                    .fileResults(List.of()).build();
        }
        FileDiagnosticResult fileResult = analyzeFile(filePath, content);
        return buildSingleFileResult(fileResult);
    }

    @Override
    public BatchInspectionResult runOnDirectory(String directoryPath) {
        Objects.requireNonNull(directoryPath, "directoryPath must not be null");
        List<Path> xmlFiles = collectXmlFiles(directoryPath);
        List<FileDiagnosticResult> fileResults = new ArrayList<>();
        int totalFiles = 0;
        int skippedFiles = 0;
        int errorCount = 0;
        int warningCount = 0;
        int infoCount = 0;
        boolean hasInternalErrors = false;

        for (Path path : xmlFiles) {
            String filePath = path.toString();
            String content = readFileContent(filePath);
            if (content == null) {
                fileResults.add(FileDiagnosticResult.builder()
                        .filePath(filePath).diagnostics(List.of()).fixActions(List.of()).build());
                totalFiles++;
                continue;
            }
            if (!fileMatcher.isDslFile(filePath, content)) {
                skippedFiles++;
                continue;
            }
            FileDiagnosticResult fileResult = analyzeFile(filePath, content);
            fileResults.add(fileResult);
            totalFiles++;
            if (fileResult.isHasInternalError()) {
                hasInternalErrors = true;
            }
            errorCount += countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.ERROR);
            warningCount += countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.WARNING);
            infoCount += countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.INFO);
        }

        return BatchInspectionResult.builder()
                .totalFiles(totalFiles).skippedFiles(skippedFiles)
                .errorCount(errorCount).warningCount(warningCount).infoCount(infoCount)
                .fileResults(fileResults).hasInternalErrors(hasInternalErrors).build();
    }

    private FileDiagnosticResult analyzeFile(String filePath, String content) {
        PipelineMode mode = inspectionConfig.getPipelineMode() != null
                ? inspectionConfig.getPipelineMode() : PipelineMode.FULL;

        DslFileNode ast;
        try {
            long astStart = verboseCollector != null ? System.currentTimeMillis() : 0;
            ast = astProvider.getDslAst(filePath, content);
            if (verboseCollector != null) {
                verboseCollector.recordStageTime("AST build", System.currentTimeMillis() - astStart);
            }
        } catch (Exception e) {
            Diagnostic internalError = Diagnostic.builder()
                    .severity(DiagnosticSeverity.ERROR)
                    .ruleId("INTERNAL-AST-ERROR")
                    .message("AST build failed: " + e.getMessage())
                    .filePath(filePath)
                    .line(0)
                    .column(0)
                    .build();
            return FileDiagnosticResult.builder()
                    .filePath(filePath)
                    .diagnostics(List.of(internalError))
                    .fixActions(List.of())
                    .hasInternalError(true)
                    .build();
        }

        List<Diagnostic> diagnostics;
        try {
            diagnostics = diagnosticProvider.analyze(ast, ruleRepository, symbolTableBuilder,
                    mode, inspectionConfig, verboseCollector);
        } catch (Exception e) {
            Diagnostic internalError = Diagnostic.builder()
                    .severity(DiagnosticSeverity.ERROR)
                    .ruleId("INTERNAL-ANALYZER-ERROR")
                    .message("Diagnostic analysis failed: " + e.getMessage())
                    .filePath(filePath)
                    .line(0)
                    .column(0)
                    .build();
            return FileDiagnosticResult.builder()
                    .filePath(filePath)
                    .diagnostics(List.of(internalError))
                    .fixActions(List.of())
                    .hasInternalError(true)
                    .build();
        }

        if (inspectionConfig.isQuiet()) {
            diagnostics = new ArrayList<>(diagnostics.stream()
                    .filter(d -> d.getSeverity() == DiagnosticSeverity.ERROR)
                    .toList());
        }

        List<FixAction> fixActions = List.of();
        boolean hasInternalError = false;
        if (mode == PipelineMode.FULL && !diagnostics.isEmpty()) {
            try {
                fixActions = quickFixProvider.getFixActions(diagnostics);
            } catch (Exception e) {
                fixActions = List.of();
                hasInternalError = true;
            }
        }

        return FileDiagnosticResult.builder()
                .filePath(filePath)
                .diagnostics(diagnostics)
                .fixActions(fixActions)
                .hasInternalError(hasInternalError)
                .build();
    }

    private BatchInspectionResult buildSingleFileResult(FileDiagnosticResult fileResult) {
        int errorCount = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.ERROR);
        int warningCount = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.WARNING);
        int infoCount = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.INFO);
        return BatchInspectionResult.builder()
                .totalFiles(1).skippedFiles(0)
                .errorCount(errorCount).warningCount(warningCount).infoCount(infoCount)
                .fileResults(List.of(fileResult))
                .hasInternalErrors(fileResult.isHasInternalError())
                .build();
    }

    private String readFileContent(String filePath) {
        try {
            return Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private List<Path> collectXmlFiles(String directoryPath) {
        List<Path> result = new ArrayList<>();
        try (java.util.stream.Stream<Path> paths = Files.walk(Path.of(directoryPath))) {
            paths.filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                    .filter(Files::isRegularFile)
                    .forEach(result::add);
        } catch (IOException e) {
            throw new BatchInspectionException("Directory not found or unreadable: " + directoryPath, e);
        }
        return result;
    }

    private int countBySeverity(List<Diagnostic> diagnostics, DiagnosticSeverity severity) {
        if (diagnostics == null) {
            return 0;
        }
        return (int) diagnostics.stream()
                .filter(d -> d.getSeverity() == severity)
                .count();
    }
}
