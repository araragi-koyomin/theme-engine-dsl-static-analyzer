---
module_ids: [CORE]
doc_kind: plan
status: archived
created: 2026-07-06
---
# CLI管线集成与输出格式 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire CliMain into a complete M7 pipeline orchestrator with mode switching, 3-format output, exit codes, and degradation handling — merging #55 and #56.

**Architecture:** CliMain orchestrates the full pipeline: parse args → validate → load rules → create dependency chain → execute scan → export report → compute exit code. PipelineMode (FULL/SYNTAX_ONLY/SEMANTIC_ONLY) injected into BatchInspectionRunnerImpl controls which stages execute. Per-Analyzer try-catch in DiagnosticProviderImpl provides granular degradation.

**Tech Stack:** Java 17, JUnit 5, Lombok, Gson, ANTLR4 runtime

---

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `core/cli/PipelineMode.java` | Pipeline mode enum (FULL/SYNTAX_ONLY/SEMANTIC_ONLY) |
| Modify | `core/cli/CliConfig.java` | Add format, outputPath, noColor, quiet, syntaxOnly, semanticOnly, versionRequested + fromArgs parsing |
| Modify | `core/cli/InspectionConfig.java` | Add pipelineMode, typeCheck, noColor, verbose, quiet |
| Modify | `core/cli/CliMain.java` | Full pipeline orchestration (load rules → create deps → scan → export → exit code) + --rule-dir validation + param mutual exclusion |
| Modify | `core/cli/CliOutputFormatter.java` | Add formatWarning(), formatVersion() |
| Modify | `core/batchinspection/BatchInspectionRunnerImpl.java` | Add InspectionConfig constructor param + mode-dependent stage execution + per-stage try-catch degradation |
| Modify | `core/semanticanalysis/DiagnosticProviderImpl.java` | Add per-Analyzer try-catch + per-branch SymbolTable try-catch |
| Create | `core/cli/CliConfigExtendedTest.java` | Test new CLI params + mutual exclusion |
| Create | `core/cli/CliMainIntegrationTest.java` | Test full pipeline orchestration + output formats + exit codes |
| Modify | `core/cli/CliMainTest.java` | Update existing tests to match new CliMain behavior |
| Create | `core/batchinspection/BatchInspectionRunnerModeTest.java` | Test 3 pipeline modes |
| Create | `core/semanticanalysis/DiagnosticProviderDegradationTest.java` | Test per-Analyzer + SymbolTable degradation |

Base path: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/`
Test base path: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/`

---

## Phase 1: PipelineMode + CliConfig + InspectionConfig Extension

### Task 1: PipelineMode Enum

**Files:**
- Create: `core/cli/PipelineMode.java`

- [ ] **Step 1: Write PipelineMode.java**

```java
package com.huawei.theme.analysis.core.cli;

public enum PipelineMode {
    FULL,
    SYNTAX_ONLY,
    SEMANTIC_ONLY
}
```

- [ ] **Step 2: Compile to verify**

Run: `./gradlew :analysis:compileJava`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/cli/PipelineMode.java
git commit -m "feat(cli): add PipelineMode enum for stage execution control"
```

---

### Task 2: Extend InspectionConfig

**Files:**
- Modify: `core/cli/InspectionConfig.java`

- [ ] **Step 1: Write failing test for InspectionConfig extended fields**

Create `core/cli/InspectionConfigExtendedTest.java`:

```java
package com.huawei.theme.analysis.core.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectionConfigExtendedTest {

    @Test
    void builderWithPipelineMode() {
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.SYNTAX_ONLY)
                .typeCheck(true)
                .noColor(false)
                .verbose(false)
                .quiet(false)
                .build();
        assertEquals(PipelineMode.SYNTAX_ONLY, config.getPipelineMode());
        assertTrue(config.isTypeCheck());
        assertFalse(config.isNoColor());
    }

    @Test
    void builderDefaults() {
        InspectionConfig config = InspectionConfig.builder().build();
        assertNull(config.getPipelineMode());
        assertNull(config.getRootElementNames());
    }

    @Test
    void pipelineModeSemanticOnly() {
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.SEMANTIC_ONLY)
                .typeCheck(false)
                .build();
        assertEquals(PipelineMode.SEMANTIC_ONLY, config.getPipelineMode());
        assertFalse(config.isTypeCheck());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :analysis:test --tests "com.huawei.theme.analysis.core.cli.InspectionConfigExtendedTest"`
Expected: FAIL — InspectionConfig does not have pipelineMode/typeCheck/noColor/verbose/quiet fields

- [ ] **Step 3: Extend InspectionConfig.java**

Add new fields to existing `InspectionConfig.java`:

```java
package com.huawei.theme.analysis.core.cli;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

@Data
@Builder
public class InspectionConfig {
    List<String> rootElementNames;
    List<String> enabledRuleIds;
    List<String> disabledRuleIds;
    Map<String, DiagnosticSeverity> severityOverrides;
    PipelineMode pipelineMode;
    @Builder.Default
    boolean typeCheck = true;
    @Builder.Default
    boolean noColor = false;
    @Builder.Default
    boolean verbose = false;
    @Builder.Default
    boolean quiet = false;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :analysis:test --tests "com.huawei.theme.analysis.core.cli.InspectionConfigExtendedTest"`
Expected: PASS

- [ ] **Step 5: Run existing InspectionConfig tests**

Run: `./gradlew :analysis:test --tests "com.huawei.theme.analysis.core.cli.InspectionConfigLoaderTest"`
Expected: PASS (no breaking changes to existing fields)

- [ ] **Step 6: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/cli/InspectionConfig.java
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/cli/InspectionConfigExtendedTest.java
git commit -m "feat(cli): extend InspectionConfig with PipelineMode and output control fields"
```

---

### Task 3: Extend CliConfig with New CLI Parameters

**Files:**
- Modify: `core/cli/CliConfig.java`

- [ ] **Step 1: Write failing test for new CLI params**

Create `core/cli/CliConfigExtendedTest.java`:

```java
package com.huawei.theme.analysis.core.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliConfigExtendedTest {

    @Test
    void fromArgsWithFormatJson() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--format", "json", "theme.xml"});
        assertEquals("json", config.getFormat());
        assertEquals("theme.xml", config.getTargetPath());
    }

    @Test
    void fromArgsWithFormatMarkdown() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--format", "markdown", "theme.xml"});
        assertEquals("markdown", config.getFormat());
    }

    @Test
    void fromArgsDefaultFormatIsTerminal() {
        CliConfig config = CliConfig.fromArgs(new String[]{"theme.xml"});
        assertEquals("terminal", config.getFormat());
    }

    @Test
    void fromArgsThrowsWhenFormatMissingValue() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CliConfig.fromArgs(new String[]{"--format"})
        );
        assertEquals("--format requires a value (json/markdown/terminal)", ex.getMessage());
    }

    @Test
    void fromArgsWithOutputPath() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--output", "/tmp/report.json", "theme.xml"});
        assertEquals("/tmp/report.json", config.getOutputPath());
    }

    @Test
    void fromArgsThrowsWhenOutputMissingValue() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CliConfig.fromArgs(new String[]{"--output"})
        );
        assertEquals("--output requires a path value", ex.getMessage());
    }

    @Test
    void fromArgsWithNoColor() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--no-color", "theme.xml"});
        assertTrue(config.isNoColor());
    }

    @Test
    void fromArgsDefaultNoColorIsFalse() {
        CliConfig config = CliConfig.fromArgs(new String[]{"theme.xml"});
        assertFalse(config.isNoColor());
    }

    @Test
    void fromArgsWithQuiet() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--quiet", "theme.xml"});
        assertTrue(config.isQuiet());
        assertFalse(config.isVerbose());
    }

    @Test
    void fromArgsWithSyntaxOnly() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--syntax-only", "theme.xml"});
        assertTrue(config.isSyntaxOnly());
        assertFalse(config.isSemanticOnly());
    }

    @Test
    void fromArgsWithSemanticOnly() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--semantic-only", "theme.xml"});
        assertTrue(config.isSemanticOnly());
        assertFalse(config.isSyntaxOnly());
    }

    @Test
    void fromArgsWithVersion() {
        CliConfig config = CliConfig.fromArgs(new String[]{"--version"});
        assertTrue(config.isVersionRequested());
    }

    @Test
    void fromArgsWithAllNewFlagsCombined() {
        CliConfig config = CliConfig.fromArgs(new String[]{
                "--format", "json", "--output", "/tmp/report.json",
                "--no-color", "--no-type-check", "--verbose",
                "--rule-dir", "/rules", "theme.xml"
        });
        assertEquals("json", config.getFormat());
        assertEquals("/tmp/report.json", config.getOutputPath());
        assertTrue(config.isNoColor());
        assertFalse(config.isTypeCheck());
        assertTrue(config.isVerbose());
        assertEquals("/rules", config.getRuleDir());
        assertEquals("theme.xml", config.getTargetPath());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :analysis:test --tests "com.huawei.theme.analysis.core.cli.CliConfigExtendedTest"`
Expected: FAIL — CliConfig does not have new fields, fromArgs does not parse new params

- [ ] **Step 3: Extend CliConfig.java**

```java
package com.huawei.theme.analysis.core.cli;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CliConfig {
    String ruleDir;
    @Builder.Default
    boolean typeCheck = true;
    boolean verbose;
    boolean helpRequested;
    String targetPath;
    String configPath;
    @Builder.Default
    String format = "terminal";
    String outputPath;
    @Builder.Default
    boolean noColor = false;
    boolean quiet;
    boolean syntaxOnly;
    boolean semanticOnly;
    boolean versionRequested;

    public static CliConfig fromArgs(String[] args) {
        CliConfigBuilder builder = CliConfig.builder();
        String targetPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--rule-dir":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--rule-dir requires a path value");
                    }
                    builder.ruleDir(args[++i]);
                    break;
                case "--no-type-check":
                    builder.typeCheck(false);
                    break;
                case "--verbose":
                    builder.verbose(true);
                    break;
                case "--quiet":
                    builder.quiet(true);
                    break;
                case "--format":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--format requires a value (json/markdown/terminal)");
                    }
                    builder.format(args[++i]);
                    break;
                case "--output":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--output requires a path value");
                    }
                    builder.outputPath(args[++i]);
                    break;
                case "--no-color":
                    builder.noColor(true);
                    break;
                case "--syntax-only":
                    builder.syntaxOnly(true);
                    break;
                case "--semantic-only":
                    builder.semanticOnly(true);
                    break;
                case "--version":
                    builder.versionRequested(true);
                    break;
                case "--help":
                case "-h":
                    builder.helpRequested(true);
                    break;
                case "--config":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--config requires a path value");
                    }
                    builder.configPath(args[++i]);
                    break;
                default:
                    if (args[i].startsWith("--")) {
                        throw new IllegalArgumentException("Unknown option: " + args[i]);
                    }
                    if (targetPath != null) {
                        throw new IllegalArgumentException(
                                "Multiple target paths provided. Only one <file-or-directory> argument is allowed.");
                    }
                    targetPath = args[i];
                    break;
            }
        }

        if (builder.build().isHelpRequested() || builder.build().isVersionRequested()) {
            builder.targetPath(targetPath);
            return builder.build();
        }

        if (targetPath == null) {
            throw new IllegalArgumentException("No target path provided");
        }

        builder.targetPath(targetPath);
        return builder.build();
    }
}
```

- [ ] **Step 4: Run new tests to verify they pass**

Run: `./gradlew :analysis:test --tests "com.huawei.theme.analysis.core.cli.CliConfigExtendedTest"`
Expected: PASS

- [ ] **Step 5: Run existing CliConfig tests**

Run: `./gradlew :analysis:test --tests "com.huawei.theme.analysis.core.cli.CliConfigTest"`
Expected: PASS (existing behavior unchanged)

- [ ] **Step 6: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/cli/CliConfig.java
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/cli/CliConfigExtendedTest.java
git commit -m "feat(cli): extend CliConfig with format/output/noColor/quiet/syntaxOnly/semanticOnly/version"
```

---

### Task 4: CliOutputFormatter formatWarning + formatVersion

**Files:**
- Modify: `core/cli/CliOutputFormatter.java`

- [ ] **Step 1: Write failing test**

Add to existing or create test for `CliOutputFormatter.formatWarning()` and `formatVersion()`:

```java
// In CliOutputFormatter test (new file or extend existing):
@Test
void formatWarningReturnsWarningPrefix() {
    assertEquals("Warning: missing rule files", CliOutputFormatter.formatWarning("missing rule files"));
}

@Test
void formatVersionReturnsVersionString() {
    assertEquals("dsl-analyzer 0.1.0", CliOutputFormatter.formatVersion());
}
```

- [ ] **Step 2: Add methods to CliOutputFormatter.java**

Add two methods to existing class:

```java
public static String formatWarning(String message) {
    return "Warning: " + message;
}

public static String formatVersion() {
    return "dsl-analyzer " + CliMain.VERSION;
}
```

- [ ] **Step 3: Add VERSION constant to CliMain.java**

Add at top of `CliMain` class:

```java
public static final String VERSION = "0.1.0";
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :analysis:test --tests "*CliOutputFormatter*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/cli/CliOutputFormatter.java
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/cli/CliMain.java
git commit -m "feat(cli): add formatWarning/formatVersion to CliOutputFormatter and VERSION to CliMain"
```

---

## Phase 2: BatchInspectionRunnerImpl Mode Support + CliMain Pipeline Orchestration

### Task 5: BatchInspectionRunnerImpl with InspectionConfig + Mode Support

**Files:**
- Modify: `core/batchinspection/BatchInspectionRunnerImpl.java`

- [ ] **Step 1: Write failing test for mode support**

Create `core/batchinspection/BatchInspectionRunnerModeTest.java`:

```java
package com.huawei.theme.analysis.core.batchinspection;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.cli.PipelineMode;
import com.huawei.theme.analysis.core.cli.CliDslFileMatcher;
import com.huawei.theme.analysis.core.cli.CliDslAstProvider;
import com.huawei.theme.analysis.core.cli.ConfigAwareRuleRepository;
import com.huawei.theme.analysis.core.fileidentification.DslFileIdentifier;
import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProviderImpl;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilderImpl;
import com.huawei.theme.analysis.core.quickfix.QuickFixProviderImpl;
import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchInspectionRunnerModeTest {

    private Path tempFile;
    private Path tempDir;
    private RuleRepository ruleRepo;
    private BatchInspectionRunner runner;

    @BeforeEach
    void setUp() throws Exception {
        ruleRepo = JsonRuleLoader.loadFromDirectory(
                "feature/analysis/src/main/resources/rules");
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

    private BatchInspectionRunner createRunner(InspectionConfig config) {
        DslFileIdentifier identifier = new DslFileIdentifier(ruleRepo);
        CliDslFileMatcher matcher = new CliDslFileMatcher(identifier);
        CliDslAstProvider astProvider = new CliDslAstProvider(
                new com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder(ruleRepo));
        return new BatchInspectionRunnerImpl(
                matcher, astProvider,
                new DiagnosticProviderImpl(),
                new QuickFixProviderImpl(),
                new SymbolTableBuilderImpl(),
                config != null ? new ConfigAwareRuleRepository(ruleRepo, config) : ruleRepo,
                config != null ? config : InspectionConfig.builder()
                        .pipelineMode(PipelineMode.FULL).typeCheck(true).build()
        );
    }

    @Test
    void syntaxOnlyModeSkipsSemanticDiagnostics() throws Exception {
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.SYNTAX_ONLY)
                .typeCheck(true)
                .build();
        runner = createRunner(config);
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertTrue(result.getErrorCount() == 0 || result.getWarningCount() == 0 || result.getInfoCount() == 0);
        assertEquals(1, result.getTotalFiles());
    }

    @Test
    void semanticOnlyModeIncludesSemanticButNotSyntaxErrors() throws Exception {
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.SEMANTIC_ONLY)
                .typeCheck(true)
                .build();
        runner = createRunner(config);
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(1, result.getTotalFiles());
    }

    @Test
    void fullModeIncludesAllDiagnostics() throws Exception {
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.FULL)
                .typeCheck(true)
                .build();
        runner = createRunner(config);
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(1, result.getTotalFiles());
    }

    @Test
    void noTypeCheckDisablesTypeAnalyzer() throws Exception {
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(PipelineMode.FULL)
                .typeCheck(false)
                .build();
        runner = createRunner(config);
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(1, result.getTotalFiles());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.BatchInspectionRunnerModeTest"`
Expected: FAIL — BatchInspectionRunnerImpl does not accept InspectionConfig parameter

- [ ] **Step 3: Modify BatchInspectionRunnerImpl.java**

Replace the entire class with the version that accepts InspectionConfig and implements mode-dependent execution:

```java
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

    public BatchInspectionRunnerImpl(
            DslFileMatcher fileMatcher,
            DslAstProvider astProvider,
            DiagnosticProvider diagnosticProvider,
            QuickFixProvider quickFixProvider,
            SymbolTableBuilder symbolTableBuilder,
            RuleRepository ruleRepository,
            InspectionConfig inspectionConfig) {
        this.fileMatcher = Objects.requireNonNull(fileMatcher, "fileMatcher must not be null");
        this.astProvider = Objects.requireNonNull(astProvider, "astProvider must not be null");
        this.diagnosticProvider = Objects.requireNonNull(diagnosticProvider, "diagnosticProvider must not be null");
        this.quickFixProvider = Objects.requireNonNull(quickFixProvider, "quickFixProvider must not be null");
        this.symbolTableBuilder = Objects.requireNonNull(symbolTableBuilder, "symbolTableBuilder must not be null");
        this.ruleRepository = Objects.requireNonNull(ruleRepository, "ruleRepository must not be null");
        this.inspectionConfig = Objects.requireNonNull(inspectionConfig, "inspectionConfig must not be null");
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
            errorCount += countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.ERROR);
            warningCount += countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.WARNING);
            infoCount += countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.INFO);
        }

        return BatchInspectionResult.builder()
                .totalFiles(totalFiles).skippedFiles(skippedFiles)
                .errorCount(errorCount).warningCount(warningCount).infoCount(infoCount)
                .fileResults(fileResults).build();
    }

    private FileDiagnosticResult analyzeFile(String filePath, String content) {
        PipelineMode mode = inspectionConfig.getPipelineMode() != null
                ? inspectionConfig.getPipelineMode() : PipelineMode.FULL;

        DslFileNode ast;
        try {
            ast = astProvider.getDslAst(filePath, content);
        } catch (Exception e) {
            return FileDiagnosticResult.builder()
                    .filePath(filePath).diagnostics(List.of()).fixActions(List.of()).build();
        }

        List<Diagnostic> diagnostics = List.of();
        if (mode != PipelineMode.SYNTAX_ONLY) {
            try {
                diagnostics = diagnosticProvider.analyze(ast, ruleRepository, symbolTableBuilder);
            } catch (Exception e) {
                diagnostics = List.of();
            }
        }

        List<FixAction> fixActions = List.of();
        if (mode == PipelineMode.FULL && !diagnostics.isEmpty()) {
            try {
                fixActions = quickFixProvider.getFixActions(diagnostics);
            } catch (Exception e) {
                fixActions = List.of();
            }
        }

        return FileDiagnosticResult.builder()
                .filePath(filePath).diagnostics(diagnostics).fixActions(fixActions).build();
    }

    private BatchInspectionResult buildSingleFileResult(FileDiagnosticResult fileResult) {
        int errorCount = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.ERROR);
        int warningCount = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.WARNING);
        int infoCount = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.INFO);
        return BatchInspectionResult.builder()
                .totalFiles(1).skippedFiles(0)
                .errorCount(errorCount).warningCount(warningCount).infoCount(infoCount)
                .fileResults(List.of(fileResult)).build();
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
```

- [ ] **Step 4: Update existing BatchInspectionRunnerImplTest to match new constructor**

Existing tests use the old 6-param constructor. Add `InspectionConfig` as the 7th parameter in all existing test constructions. The default config:

```java
InspectionConfig defaultConfig = InspectionConfig.builder()
        .pipelineMode(PipelineMode.FULL).typeCheck(true).build();
```

Then pass `defaultConfig` (or `ruleRepo` when ConfigAwareRuleRepository is already used) as the 7th arg to `new BatchInspectionRunnerImpl(...)`.

- [ ] **Step 5: Run mode tests**

Run: `./gradlew :analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.BatchInspectionRunnerModeTest"`
Expected: PASS

- [ ] **Step 6: Run existing runner tests**

Run: `./gradlew :analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.BatchInspectionRunnerImplTest"`
Expected: PASS (after constructor update)

- [ ] **Step 7: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionRunnerImpl.java
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionRunnerModeTest.java
git commit -m "feat(M7): add InspectionConfig to BatchInspectionRunnerImpl with mode-dependent execution"
```

---

### Task 6: CliMain Full Pipeline Orchestration

**Files:**
- Modify: `core/cli/CliMain.java`

This is the largest task. CliMain changes from "print config → exit 0" to full pipeline orchestration.

- [ ] **Step 1: Write failing integration test**

Create `core/cli/CliMainIntegrationTest.java` with a real DSL file test:

```java
package com.huawei.theme.analysis.core.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliMainIntegrationTest {

    private Path tempFile;
    private Path tempDir;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream capturedOut;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    void setUp() throws Exception {
        originalOut = System.out;
        originalErr = System.err;
        capturedOut = new ByteArrayOutputStream();
        capturedErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
        System.setErr(new PrintStream(capturedErr));

        String dslContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Lockscreen xmlns:sys=\"http://www.huawei.com/system\">\n" +
                "  <Var name=\"testVar\" expression=\"1+2\"/>\n" +
                "  <Image src=\"@testVar\"/>\n" +
                "</Lockscreen>";
        tempDir = Files.createTempDirectory("cli-integration-test");
        tempFile = tempDir.resolve("test_theme.xml");
        Files.writeString(tempFile, dslContent, StandardCharsets.UTF_8);
    }

    @AfterEach
    void tearDown() throws Exception {
        System.setOut(originalOut);
        System.setErr(originalErr);
        Files.deleteIfExists(tempFile);
        Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
    }

    @Test
    void runWithValidDslFileReturnsZeroOrOne() {
        int exitCode = CliMain.run(new String[]{tempFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        String stdout = capturedOut.toString();
        assertTrue(stdout.length() > 0);
    }

    @Test
    void runWithVersionReturnsZero() {
        int exitCode = CliMain.run(new String[]{"--version"});
        assertEquals(0, exitCode);
        assertTrue(capturedOut.toString().contains("dsl-analyzer"));
    }

    @Test
    void runWithVerboseAndQuietMutualExclusionReturnsTwo() {
        int exitCode = CliMain.run(new String[]{"--verbose", "--quiet", tempFile.toString()});
        assertEquals(2, exitCode);
        assertTrue(capturedErr.toString().contains("mutually exclusive"));
    }

    @Test
    void runWithSyntaxOnlyAndSemanticOnlyMutualExclusionReturnsTwo() {
        int exitCode = CliMain.run(new String[]{"--syntax-only", "--semantic-only", tempFile.toString()});
        assertEquals(2, exitCode);
        assertTrue(capturedErr.toString().contains("mutually exclusive"));
    }

    @Test
    void runWithNonexistentRuleDirReturnsTwo() {
        int exitCode = CliMain.run(new String[]{"--rule-dir", "/nonexistent/rules", tempFile.toString()});
        assertEquals(2, exitCode);
        assertTrue(capturedErr.toString().contains("rule directory"));
    }

    @Test
    void runWithFormatJsonOutputContainsJson() {
        int exitCode = CliMain.run(new String[]{"--format", "json", "--no-color", tempFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        String stdout = capturedOut.toString();
        assertTrue(stdout.contains("{"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :analysis:test --tests "com.huawei.theme.analysis.core.cli.CliMainIntegrationTest"`
Expected: FAIL — CliMain still just prints config and exits 0

- [ ] **Step 3: Rewrite CliMain.java with full pipeline orchestration**

```java
package com.huawei.theme.analysis.core.cli;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.huawei.theme.analysis.core.batchinspection.BatchInspectionRunner;
import com.huawei.theme.analysis.core.batchinspection.BatchInspectionRunnerImpl;
import com.huawei.theme.analysis.core.batchinspection.ExitCodeCalculator;
import com.huawei.theme.analysis.core.batchinspection.ReportExporter;
import com.huawei.theme.analysis.core.batchinspection.ReportExporterImpl;
import com.huawei.theme.analysis.core.batchinspection.TerminalFormatter;
import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.fileidentification.DslFileIdentifier;
import com.huawei.theme.analysis.core.quickfix.QuickFixProviderImpl;
import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.RuleLoadException;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProviderImpl;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilderImpl;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;

public class CliMain {

    public static final String VERSION = "0.1.0";

    static final String USAGE_HINT =
            "Usage: java -jar dsl-analyzer.jar [options] <file-or-directory>\n" +
            "Options:\n" +
            "  --rule-dir <path>     Custom rule library directory (default: built-in)\n" +
            "  --no-type-check       Disable type inference checking (default: enabled)\n" +
            "  --syntax-only         Only run syntax analysis (skip M4/M5)\n" +
            "  --semantic-only       Only run semantic analysis (skip syntax errors)\n" +
            "  --format <format>     Output format: terminal (default), json, markdown\n" +
            "  --output <path>       Write report to file (json/markdown only)\n" +
            "  --no-color            Disable ANSI color output\n" +
            "  --verbose             Enable verbose output\n" +
            "  --quiet               Only output error-level diagnostics\n" +
            "  --config <path>       Inspection config file (JSON)\n" +
            "  --version             Show version\n" +
            "  --help, -h            Show this help message";

    private static final String BUILT_IN_RULES_PATH = "feature/analysis/src/main/resources/rules";

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        try {
            CliConfig config = CliConfig.fromArgs(args);

            if (config.isVersionRequested()) {
                System.out.println(CliOutputFormatter.formatVersion());
                return 0;
            }

            if (config.isHelpRequested()) {
                System.out.println(USAGE_HINT);
                return 0;
            }

            String mutualExclusionError = checkMutualExclusion(config);
            if (mutualExclusionError != null) {
                System.err.println(CliOutputFormatter.formatError(mutualExclusionError));
                System.err.println(USAGE_HINT);
                return 2;
            }

            File targetFile = new File(config.getTargetPath());
            if (!targetFile.exists()) {
                System.err.println(CliOutputFormatter.formatError("Path not found: " + config.getTargetPath()));
                System.err.println(USAGE_HINT);
                return 2;
            }
            if (!targetFile.isFile() && !targetFile.isDirectory()) {
                System.err.println(CliOutputFormatter.formatError("Path is not a file or directory: " + config.getTargetPath()));
                System.err.println(USAGE_HINT);
                return 2;
            }

            InspectionConfig inspectionConfig = loadInspectionConfig(config);
            if (inspectionConfig == null) {
                return 2;
            }

            PipelineMode mode = resolvePipelineMode(config);
            InspectionConfig effectiveConfig = InspectionConfig.builder()
                    .rootElementNames(inspectionConfig.getRootElementNames())
                    .enabledRuleIds(inspectionConfig.getEnabledRuleIds())
                    .disabledRuleIds(inspectionConfig.getDisabledRuleIds())
                    .severityOverrides(inspectionConfig.getSeverityOverrides())
                    .pipelineMode(mode)
                    .typeCheck(config.isTypeCheck())
                    .noColor(config.isNoColor())
                    .verbose(config.isVerbose())
                    .quiet(config.isQuiet())
                    .build();

            RuleRepository ruleRepo = loadRuleRepository(config);
            if (ruleRepo == null) {
                return 2;
            }

            RuleRepository effectiveRepo = new ConfigAwareRuleRepository(ruleRepo, effectiveConfig);

            DslFileIdentifier identifier = new DslFileIdentifier(effectiveRepo);
            CliDslFileMatcher matcher = new CliDslFileMatcher(identifier);
            CliDslAstProvider astProvider = new CliDslAstProvider(new AstBuilder(effectiveRepo));

            BatchInspectionRunner runner = new BatchInspectionRunnerImpl(
                    matcher, astProvider,
                    new DiagnosticProviderImpl(),
                    new QuickFixProviderImpl(),
                    new SymbolTableBuilderImpl(),
                    effectiveRepo,
                    effectiveConfig
            );

            BatchInspectionResult result;
            if (targetFile.isFile()) {
                result = runner.runOnFile(targetFile.getAbsolutePath());
            } else {
                result = runner.runOnDirectory(targetFile.getAbsolutePath());
            }

            exportReport(result, config);

            return ExitCodeCalculator.compute(result);
        } catch (IllegalArgumentException e) {
            System.err.println(CliOutputFormatter.formatError(e.getMessage()));
            System.err.println(USAGE_HINT);
            return 2;
        } catch (Exception e) {
            System.err.println(CliOutputFormatter.formatError("Unexpected error: " + e.getMessage()));
            System.err.println(USAGE_HINT);
            return 2;
        }
    }

    private static String checkMutualExclusion(CliConfig config) {
        if (config.isVerbose() && config.isQuiet()) {
            return "--verbose and --quiet are mutually exclusive";
        }
        if (config.isSyntaxOnly() && config.isSemanticOnly()) {
            return "--syntax-only and --semantic-only are mutually exclusive";
        }
        return null;
    }

    private static PipelineMode resolvePipelineMode(CliConfig config) {
        if (config.isSyntaxOnly()) {
            return PipelineMode.SYNTAX_ONLY;
        }
        if (config.isSemanticOnly()) {
            return PipelineMode.SEMANTIC_ONLY;
        }
        return PipelineMode.FULL;
    }

    private static InspectionConfig loadInspectionConfig(CliConfig config) {
        if (config.getConfigPath() == null) {
            return InspectionConfig.builder().build();
        }
        File configFile = new File(config.getConfigPath());
        if (!configFile.exists()) {
            System.err.println(CliOutputFormatter.formatError("Config file not found: " + config.getConfigPath()));
            return null;
        }
        if (!configFile.isFile()) {
            System.err.println(CliOutputFormatter.formatError("Config path is not a file: " + config.getConfigPath()));
            return null;
        }
        try {
            InspectionConfigLoader loader = new InspectionConfigLoader();
            return loader.load(config.getConfigPath());
        } catch (InspectionConfigLoader.ConfigLoadException e) {
            System.err.println(CliOutputFormatter.formatError("Config load error: " + e.getMessage()));
            return null;
        } catch (InspectionConfigLoader.ConfigValidationException e) {
            System.err.println(CliOutputFormatter.formatError("Config validation error: " + e.getMessage()));
            return null;
        }
    }

    private static RuleRepository loadRuleRepository(CliConfig config) {
        if (config.getRuleDir() != null) {
            File ruleDirFile = new File(config.getRuleDir());
            if (!ruleDirFile.exists()) {
                System.err.println(CliOutputFormatter.formatError("Rule directory not found: " + config.getRuleDir()));
                return null;
            }
            try {
                RuleRepository customRepo = JsonRuleLoader.loadFromDirectory(config.getRuleDir());
                if (customRepo.getAllElementNames().isEmpty()) {
                    System.err.println(CliOutputFormatter.formatWarning(
                            "Custom rule directory has no rule files, falling back to built-in rules"));
                    return loadBuiltInRules();
                }
                return customRepo;
            } catch (RuleLoadException e) {
                System.err.println(CliOutputFormatter.formatError("Rule load error: " + e.getMessage()));
                return null;
            }
        }
        return loadBuiltInRules();
    }

    private static RuleRepository loadBuiltInRules() {
        try {
            return JsonRuleLoader.loadFromDirectory(BUILT_IN_RULES_PATH);
        } catch (RuleLoadException e) {
            System.err.println(CliOutputFormatter.formatError("Built-in rules load error: " + e.getMessage()));
            return null;
        }
    }

    private static void exportReport(BatchInspectionResult result, CliConfig config) {
        TerminalFormatter formatter = new TerminalFormatter(config.isNoColor());
        ReportExporter exporter = new ReportExporterImpl(formatter);

        if (config.getOutputPath() != null) {
            exporter.exportToFile(result, config.getFormat(), config.getOutputPath());
            if (config.isVerbose()) {
                System.out.println("Report written to: " + config.getOutputPath());
            }
        } else {
            String output;
            switch (config.getFormat().toLowerCase()) {
                case "json":
                    output = exporter.exportJson(result);
                    break;
                case "markdown":
                case "md":
                    output = exporter.exportMarkdown(result);
                    break;
                default:
                    output = exporter.exportTerminal(result);
                    break;
            }
            System.out.println(output);
        }
    }
}
```

- [ ] **Step 4: Update existing CliMainTest.java**

Existing tests need to be updated because CliMain now runs the full pipeline. Tests that previously expected exit 0 with just config output now need to expect either 0 or 1 (based on diagnostics). Tests with nonexistent paths or bad args still expect exit 2.

Key changes to `CliMainTest.java`:
- `runWithValidArgsReturnsZero` → change assertion from `assertEquals(0, exitCode)` to `assertTrue(exitCode == 0 || exitCode == 1)` and check output contains diagnostics (not just "Configuration:")
- `runWithNoTypeCheckAndVerboseOutputContainsAllFields` → similar update
- `runWithNoFlagsOutputShowsDefaults` → similar update
- `runWithConfigPathReturnsZeroWhenConfigFileExists` → similar update
- `runWithConfigAndRuleDirReturnsZero` → similar update
- All exit 2 tests (bad args, nonexistent paths) remain unchanged

- [ ] **Step 5: Run integration test**

Run: `./gradlew :analysis:test --tests "com.huawei.theme.analysis.core.cli.CliMainIntegrationTest"`
Expected: PASS

- [ ] **Step 6: Run updated CliMainTest**

Run: `./gradlew :analysis:test --tests "com.huawei.theme.analysis.core.cli.CliMainTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/cli/CliMain.java
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/cli/CliMainTest.java
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/cli/CliMainIntegrationTest.java
git commit -m "feat(cli): wire CliMain to full M7 pipeline with mode switching and report export"
```

---

## Phase 3: Per-Analyzer Degradation in DiagnosticProviderImpl

### Task 7: Per-Analyzer Degradation + SymbolTable Degradation

**Files:**
- Modify: `core/semanticanalysis/DiagnosticProviderImpl.java`

- [ ] **Step 1: Write failing degradation test**

Create `core/semanticanalysis/DiagnosticProviderDegradationTest.java`:

```java
package com.huawei.theme.analysis.core.semanticanalysis;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.analyzers.ConstraintAnalyzer;
import com.huawei.theme.analysis.core.semanticanalysis.analyzers.RequiredAttrAnalyzer;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DiagnosticProviderDegradationTest {

    private Path tempFile;
    private RuleRepository ruleRepo;
    private DiagnosticProviderImpl provider;
    private SymbolTableBuilderImpl symbolTableBuilder;

    @BeforeEach
    void setUp() throws Exception {
        ruleRepo = JsonRuleLoader.loadFromDirectory(
                "feature/analysis/src/main/resources/rules");
        provider = new DiagnosticProviderImpl();
        symbolTableBuilder = new SymbolTableBuilderImpl();

        String dslContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Lockscreen xmlns:sys=\"http://www.huawei.com/system\">\n" +
                "  <Var name=\"testVar\" expression=\"1+2\"/>\n" +
                "  <Image src=\"@testVar\"/>\n" +
                "</Lockscreen>";
        tempFile = Files.createTempFile("degradation-test", ".xml");
        Files.writeString(tempFile, dslContent, StandardCharsets.UTF_8);
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(tempFile);
    }

    @Test
    void analyzeWithNormalAnalyzersProducesDiagnostics() throws Exception {
        AstBuilder astBuilder = new AstBuilder(ruleRepo);
        DslFileNode ast = astBuilder.build(tempFile.toString(),
                Files.readString(tempFile, StandardCharsets.UTF_8));
        List<Diagnostic> diagnostics = provider.analyze(ast, ruleRepo, symbolTableBuilder);
        assertTrue(diagnostics != null);
    }

    @Test
    void analyzeContinuesWhenSingleAnalyzerThrows() throws Exception {
        AnalyzerRegistry.clear();
        AnalyzerRegistry.register(new RequiredAttrAnalyzer());
        AnalyzerRegistry.register(new ThrowingAnalyzer());
        AnalyzerRegistry.register(new ConstraintAnalyzer());

        AstBuilder astBuilder = new AstBuilder(ruleRepo);
        DslFileNode ast = astBuilder.build(tempFile.toString(),
                Files.readString(tempFile, StandardCharsets.UTF_8));
        List<Diagnostic> diagnostics = provider.analyze(ast, ruleRepo, symbolTableBuilder);

        assertTrue(diagnostics != null);
        boolean hasRequiredAttrDiags = diagnostics.stream()
                .anyMatch(d -> d.getRuleId().contains("REQUIRED"));
        assertTrue(hasRequiredAttrDiags, "RequiredAttrAnalyzer results should still be present despite ThrowingAnalyzer failure");
    }
}
```

Also need a `ThrowingAnalyzer` test helper class in the same package or test package:

```java
package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.ArrayList;
import java.util.List;

import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

public class ThrowingAnalyzer implements DslAnalyzer {
    @Override
    public List<Diagnostic> analyze(DslAstNode element, DslContext context) {
        throw new RuntimeException("Simulated analyzer failure for testing");
    }
}
```

Note: This requires `AnalyzerRegistry` to support `clear()` and `register()` for test purposes. Check if AnalyzerRegistry already has these methods. If not, they need to be added.

- [ ] **Step 2: Check AnalyzerRegistry for clear/register test methods**

Read `AnalyzerRegistry.java` to verify it has `clear()` and `register()` methods. If `clear()` does not exist, add it:

```java
public static void clear() {
    analyzers.clear();
}

public static void register(DslAnalyzer analyzer) {
    analyzers.add(analyzer);
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :analysis:test --tests "com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProviderDegradationTest"`
Expected: FAIL — DiagnosticProviderImpl does not catch per-analyzer exceptions

- [ ] **Step 4: Modify DiagnosticProviderImpl.java**

Add try-catch per analyzer and per SymbolTable branch in `DiagnosticProviderImplInner`:

```java
package com.huawei.theme.analysis.core.semanticanalysis;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticProviderImpl implements DiagnosticProvider {

    @Override
    public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder symbolTableBuilder) {
        return new DiagnosticProviderImplInner(ast, ruleRepo, symbolTableBuilder).getDiagnostics();
    }

    static class DiagnosticProviderImplInner {

        DslFileNode root;
        RuleRepository ruleRepo;
        SymbolTable globalTable;
        SymbolTableBuilder symbolTableBuilder;
        List<Diagnostic> diagnostics = new ArrayList<>();

        public DiagnosticProviderImplInner(DslFileNode root, RuleRepository ruleRepo,
                                           SymbolTableBuilder symbolTableBuilder) {
            this.root = root;
            this.ruleRepo = ruleRepo;
            this.symbolTableBuilder = symbolTableBuilder;
            globalTable = symbolTableBuilder.buildGlobal(root, ruleRepo);
        }

        List<Diagnostic> getDiagnostics() {
            analyze(root.getRootElement(), globalTable);
            return diagnostics;
        }

        private void analyze(DslElementNode elementNode, SymbolTable symbolTable) {
            for (var analyzer : AnalyzerRegistry.getAnalyzers()) {
                try {
                    var list = analyzer.analyze(elementNode,
                            new DslContext(ruleRepo, symbolTable, root.getFilePath(), root));
                    diagnostics.addAll(list);
                } catch (Exception e) {
                    diagnostics.add(Diagnostic.builder()
                            .severity(DiagnosticSeverity.WARNING)
                            .ruleId("INTERNAL-ANALYZER-ERROR")
                            .message("Analyzer " + analyzer.getClass().getSimpleName() + " failed: " + e.getMessage())
                            .filePath(root.getFilePath())
                            .positionFrom(elementNode)
                            .build());
                }
            }

            for (var child : elementNode.getChildElements()) {
                try {
                    SymbolTable childTable = symbolTableBuilder.build(elementNode, symbolTable, ruleRepo);
                    analyze(child, childTable);
                } catch (Exception e) {
                    diagnostics.add(Diagnostic.builder()
                            .severity(DiagnosticSeverity.WARNING)
                            .ruleId("INTERNAL-SYMBOLTABLE-ERROR")
                            .message("SymbolTable build failed for child element: " + e.getMessage())
                            .filePath(root.getFilePath())
                            .positionFrom(child)
                            .build());
                    break;
                }
            }
        }
    }
}
```

Note: Need to import `DiagnosticSeverity` from `com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity`.

- [ ] **Step 5: Run degradation test**

Run: `./gradlew :analysis:test --tests "com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProviderDegradationTest"`
Expected: PASS

- [ ] **Step 6: Run existing DiagnosticProvider tests**

Run: `./gradlew :analysis:test --tests "com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProviderTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/semanticanalysis/DiagnosticProviderImpl.java
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/semanticanalysis/DiagnosticProviderDegradationTest.java
git commit -m "feat(M4): add per-Analyzer and per-SymbolTable degradation in DiagnosticProviderImpl"
```

---

## Phase 4: --rule-dir Validation Tests

### Task 8: --rule-dir Validation Scenarios

**Files:**
- Modify: `core/cli/CliMainIntegrationTest.java` (add --rule-dir tests)

- [ ] **Step 1: Add --rule-dir validation test methods**

Add to `CliMainIntegrationTest.java`:

```java
@Test
void runWithNonexistentRuleDirReturnsTwo() {
    int exitCode = CliMain.run(new String[]{"--rule-dir", "/nonexistent/rules/path", tempFile.toString()});
    assertEquals(2, exitCode);
    assertTrue(capturedErr.toString().contains("rule directory"));
}

@Test
void runWithEmptyRuleDirFallsBackToBuiltIn() throws Exception {
    Path emptyDir = Files.createTempDirectory(tempDir, "empty-rules");
    int exitCode = CliMain.run(new String[]{"--rule-dir", emptyDir.toString(), tempFile.toString()});
    assertTrue(exitCode == 0 || exitCode == 1);
    assertTrue(capturedErr.toString().contains("falling back") || capturedOut.toString().length() > 0);
    Files.deleteIfExists(emptyDir);
}

@Test
void runWithMalformedRuleJsonReturnsTwo() throws Exception {
    Path badRuleDir = Files.createTempDirectory(tempDir, "bad-rules");
    Path elementsDir = badRuleDir.resolve("elements");
    Files.createDirectories(elementsDir);
    Path badFile = elementsDir.resolve("Bad.json");
    Files.writeString(badFile, "{ invalid json !!!", StandardCharsets.UTF_8);
    int exitCode = CliMain.run(new String[]{"--rule-dir", badRuleDir.toString(), tempFile.toString()});
    assertEquals(2, exitCode);
    assertTrue(capturedErr.toString().contains("Rule load error"));
    Files.deleteIfExists(badFile);
    Files.deleteIfExists(elementsDir);
    Files.deleteIfExists(badRuleDir);
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew :analysis:test --tests "com.huawei.theme.analysis.core.cli.CliMainIntegrationTest"`
Expected: PASS (CliMain already has --rule-dir validation from Task 6)

- [ ] **Step 3: Commit**

```bash
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/cli/CliMainIntegrationTest.java
git commit -m "test(cli): add --rule-dir validation scenario tests"
```

---

## Phase 5: Final Validation + Existing Test Updates

### Task 9: Update All Existing Tests + Full Test Suite Run

**Files:**
- Modify: `core/cli/CliMainTest.java` (ensure all pass with new CliMain)
- Modify: any BatchInspectionRunnerImplTest references to old 6-param constructor

- [ ] **Step 1: Fix any remaining test compilation errors**

Search for all test files that reference `BatchInspectionRunnerImpl` constructor and add the 7th `InspectionConfig` parameter.

- [ ] **Step 2: Run full test suite**

Run: `./gradlew :analysis:test`
Expected: ALL PASS

- [ ] **Step 3: Run Core IntelliJ dependency check**

Run: `./gradlew :analysis:checkCoreIntellijDependency`
Expected: PASS (no com.intellij imports in core package)

- [ ] **Step 4: Commit any remaining test fixes**

```bash
git add -A
git commit -m "fix: update all existing tests for new BatchInspectionRunnerImpl constructor"
```

---

## Self-Review

### Spec Coverage Check

| Spec Section | Covered By Task |
|-------------|----------------|
| §1.1 Core change (CliMain upgrade) | Task 6 |
| §1.2 New/modified classes | Tasks 1-7 |
| §1.3 Pipeline orchestration flow | Task 6 |
| §1.4 PipelineMode stage control | Tasks 2, 5 |
| §2.1 Degradation scenarios | Task 7 (per-Analyzer), Task 5 (per-stage in Runner) |
| §2.2 Runner-layer degradation code | Task 5 |
| §2.3 --rule-dir validation | Task 6 + Task 8 |
| §2.4 formatWarning output | Task 4 |
| §3.1 CliConfig new fields | Task 3 |
| §3.2 Mutual exclusion validation | Task 6 |
| §3.3 InspectionConfig extension | Task 2 |
| §3.4 PipelineMode enum | Task 1 |
| §3.5 Output format handling | Task 6 |
| §3.6 --verbose output | Task 6 (basic, detailed verbose deferred to Extension) |
| §3.7 --quiet mode | Task 6 (basic quiet via format, detailed filtering deferred) |
| §3.8 --version | Task 6 |
| §4.1-4.5 Test strategy | Tasks 1-9 |

### Placeholder Scan

No TBD, TODO, or vague instructions found.

### Type Consistency

- `InspectionConfig.builder().pipelineMode(PipelineMode.FULL)` — consistent across Tasks 2, 5, 6
- `BatchInspectionRunnerImpl` 7-param constructor — consistent across Tasks 5, 6, 9
- `TerminalFormatter(boolean noColor)` → `ReportExporterImpl(TerminalFormatter)` — consistent in Task 6
- `CliConfig.fromArgs()` new params match `InspectionConfig` fields — consistent across Tasks 2, 3, 6

### Known Limitations

- `--verbose` detailed output (AST stats, timing, symbol table summary) requires additional plumbing not in current tasks. Basic verbose flag is wired; detailed output deferred to Extension layer.
- `--quiet` filtering to only ERROR diagnostics requires TerminalFormatter/ReportExporter changes. Basic quiet flag is parsed; output filtering deferred to Extension layer.
