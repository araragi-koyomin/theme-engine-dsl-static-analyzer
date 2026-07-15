# M7 批量检查功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 M7 批量检查引擎（BatchInspectionRunner）+ Terminal 彩色输出格式化（TerminalFormatter），不包括 CLI 集成和报告文件导出。

**Architecture:** 构造器注入所有上游依赖（M1-M5接口），BatchInspectionRunnerImpl 组合 M1→M3→M4→M5 管线，TerminalFormatter 提供 gcc/clang 风格终端输出。Core 层无 IDEA SDK 依赖。

**Tech Stack:** Java 17, JUnit 5, Lombok (@Data/@Builder), GSON, ANTLR4 runtime

---

### Task 1: 数据模型 (BatchInspectionResult + FileDiagnosticResult)

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/model/BatchInspectionResult.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/model/FileDiagnosticResult.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/model/BatchInspectionResultTest.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/model/FileDiagnosticResultTest.java`

- [ ] **Step 1: 创建 BatchInspectionResult 数据模型**

```java
package com.huawei.theme.analysis.core.batchinspection.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BatchInspectionResult {
    int totalFiles;
    int skippedFiles;
    int errorCount;
    int warningCount;
    int infoCount;
    List<FileDiagnosticResult> fileResults;
}
```

- [ ] **Step 2: 创建 FileDiagnosticResult 数据模型**

```java
package com.huawei.theme.analysis.core.batchinspection.model;

import java.util.List;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileDiagnosticResult {
    String filePath;
    List<Diagnostic> diagnostics;
    List<FixAction> fixActions;
}
```

- [ ] **Step 3: 创建 BatchInspectionResultTest**

```java
package com.huawei.theme.analysis.core.batchinspection.model;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchInspectionResultTest {

    @Test
    void builderCreatesResultWithAllFields() {
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(3)
                .skippedFiles(1)
                .errorCount(5)
                .warningCount(2)
                .infoCount(1)
                .fileResults(List.of())
                .build();
        assertEquals(3, result.getTotalFiles());
        assertEquals(1, result.getSkippedFiles());
        assertEquals(5, result.getErrorCount());
        assertEquals(2, result.getWarningCount());
        assertEquals(1, result.getInfoCount());
        assertEquals(0, result.getFileResults().size());
    }

    @Test
    void builderDefaultValues() {
        BatchInspectionResult result = BatchInspectionResult.builder().build();
        assertEquals(0, result.getTotalFiles());
        assertEquals(0, result.getSkippedFiles());
        assertEquals(0, result.getErrorCount());
    }
}
```

- [ ] **Step 4: 创建 FileDiagnosticResultTest**

```java
package com.huawei.theme.analysis.core.batchinspection.model;

import java.util.List;

import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FileDiagnosticResultTest {

    @Test
    void builderCreatesResultWithAllFields() {
        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REF-001")
                .message("test message")
                .filePath("test.xml")
                .line(1)
                .column(0)
                .build();
        FileDiagnosticResult result = FileDiagnosticResult.builder()
                .filePath("test.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        assertEquals("test.xml", result.getFilePath());
        assertEquals(1, result.getDiagnostics().size());
        assertEquals(0, result.getFixActions().size());
    }

    @Test
    void builderDefaultValues() {
        FileDiagnosticResult result = FileDiagnosticResult.builder()
                .filePath("test.xml")
                .build();
        assertNotNull(result.getFilePath());
    }
}
```

- [ ] **Step 5: 运行数据模型测试**

Run: `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.model.*"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/model/
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/model/
git commit -m "feat(m7): add BatchInspectionResult and FileDiagnosticResult data models"
```

---

### Task 2: BatchInspectionException

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionException.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionExceptionTest.java`

- [ ] **Step 1: 创建 BatchInspectionException**

```java
package com.huawei.theme.analysis.core.batchinspection;

public class BatchInspectionException extends RuntimeException {

    public BatchInspectionException(String message) {
        super(message);
    }

    public BatchInspectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 2: 创建 BatchInspectionExceptionTest**

```java
package com.huawei.theme.analysis.core.batchinspection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BatchInspectionExceptionTest {

    @Test
    void constructorWithMessage() {
        BatchInspectionException ex = new BatchInspectionException("test error");
        assertEquals("test error", ex.getMessage());
        assertNotNull(ex);
    }

    @Test
    void constructorWithMessageAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("root cause");
        BatchInspectionException ex = new BatchInspectionException("test error", cause);
        assertEquals("test error", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
}
```

- [ ] **Step 3: 运行异常类测试**

Run: `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.BatchInspectionExceptionTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionException.java
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionExceptionTest.java
git commit -m "feat(m7): add BatchInspectionException"
```

---

### Task 3: BatchInspectionRunner 接口

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionRunner.java`

- [ ] **Step 1: 创建 BatchInspectionRunner 接口**

```java
package com.huawei.theme.analysis.core.batchinspection;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;

public interface BatchInspectionRunner {
    BatchInspectionResult runOnFile(String filePath);
    BatchInspectionResult runOnDirectory(String directoryPath);
}
```

- [ ] **Step 2: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionRunner.java
git commit -m "feat(m7): add BatchInspectionRunner interface"
```

---

### Task 4: BatchInspectionRunnerImpl - TDD

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionRunnerImpl.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionRunnerImplTest.java`

- [ ] **Step 1: 编写 BatchInspectionRunnerImplTest 测试类（构造器 null 检查 + runOnFile null 检查）**

```java
package com.huawei.theme.analysis.core.batchinspection;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.batchinspection.model.FileDiagnosticResult;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchInspectionRunnerImplTest {

    private StubDslFileMatcher stubMatcher;
    private StubAstProvider stubAstProvider;
    private StubDiagnosticProvider stubDiagnosticProvider;
    private StubQuickFixProvider stubQuickFixProvider;
    private StubSymbolTableBuilder stubSymbolTableBuilder;
    private StubRuleRepository stubRuleRepository;
    private BatchInspectionRunnerImpl runner;

    @BeforeEach
    void setUp() {
        stubMatcher = new StubDslFileMatcher(true);
        stubAstProvider = new StubAstProvider();
        stubDiagnosticProvider = new StubDiagnosticProvider();
        stubQuickFixProvider = new StubQuickFixProvider();
        stubSymbolTableBuilder = new StubSymbolTableBuilder();
        stubRuleRepository = new StubRuleRepository();
        runner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, stubDiagnosticProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository);
    }

    @Test
    void constructorRejectsNullFileMatcher() {
        assertThrows(NullPointerException.class, () ->
                new BatchInspectionRunnerImpl(
                        null, stubAstProvider, stubDiagnosticProvider,
                        stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository));
    }

    @Test
    void constructorRejectsNullAstProvider() {
        assertThrows(NullPointerException.class, () ->
                new BatchInspectionRunnerImpl(
                        stubMatcher, null, stubDiagnosticProvider,
                        stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository));
    }

    @Test
    void constructorRejectsNullDiagnosticProvider() {
        assertThrows(NullPointerException.class, () ->
                new BatchInspectionRunnerImpl(
                        stubMatcher, stubAstProvider, null,
                        stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository));
    }

    @Test
    void constructorRejectsNullQuickFixProvider() {
        assertThrows(NullPointerException.class, () ->
                new BatchInspectionRunnerImpl(
                        stubMatcher, stubAstProvider, stubDiagnosticProvider,
                        null, stubSymbolTableBuilder, stubRuleRepository));
    }

    @Test
    void constructorRejectsNullSymbolTableBuilder() {
        assertThrows(NullPointerException.class, () ->
                new BatchInspectionRunnerImpl(
                        stubMatcher, stubAstProvider, stubDiagnosticProvider,
                        stubQuickFixProvider, null, stubRuleRepository));
    }

    @Test
    void constructorRejectsNullRuleRepository() {
        assertThrows(NullPointerException.class, () ->
                new BatchInspectionRunnerImpl(
                        stubMatcher, stubAstProvider, stubDiagnosticProvider,
                        stubQuickFixProvider, stubSymbolTableBuilder, null));
    }

    @Test
    void runOnFileRejectsNullFilePath() {
        assertThrows(NullPointerException.class, () -> runner.runOnFile(null));
    }

    @Test
    void runOnDirectoryRejectsNullDirectoryPath() {
        assertThrows(NullPointerException.class, () -> runner.runOnDirectory(null));
    }

    // --- Stub implementations ---

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
        public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder symbolTableBuilder) {
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
        public com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable buildGlobal(DslFileNode fileNode, RuleRepository ruleRepository) {
            return com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable.builder().build();
        }

        @Override
        public com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable build(com.huawei.theme.analysis.core.shared.ast.DslElementNode elementNode, com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable parent, RuleRepository ruleRepository) {
            return parent;
        }
    }

    private static class StubRuleRepository implements RuleRepository {
        @Override
        public java.util.Optional<com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule> getElementRule(String elementName) {
            return java.util.Optional.empty();
        }

        @Override
        public List<com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule> getAllElementRules() {
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
        public java.util.Optional<com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<String> resolveAttrAlias(String elementName, String attrName) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Set<String> getCanonicalAttrNames(String elementName) {
            return java.util.Collections.emptySet();
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
        public List<com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint> getConstraints(String elementName) {
            return List.of();
        }

        @Override
        public java.util.Optional<com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar> getGlobalVar(String varName) {
            return java.util.Optional.empty();
        }

        @Override
        public List<com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar> getAllGlobalVars() {
            return List.of();
        }

        @Override
        public java.util.Optional<com.huawei.theme.analysis.core.rulelibrary.model.RuleSource> getRuleSource(String ruleId) {
            return java.util.Optional.empty();
        }

        @Override
        public com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary getFunctionSignatureLibrary() {
            return null;
        }
    }
}
```

- [ ] **Step 2: 创建空 BatchInspectionRunnerImpl（仅构造器），运行测试验证 null 检查**

```java
package com.huawei.theme.analysis.core.batchinspection;

import java.util.Objects;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.fileidentification.DslFileMatcher;
import com.huawei.theme.analysis.core.quickfix.QuickFixProvider;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProvider;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilder;
import com.huawei.theme.analysis.core.syntaxanalysis.DslAstProvider;

public class BatchInspectionRunnerImpl implements BatchInspectionRunner {

    private final DslFileMatcher fileMatcher;
    private final DslAstProvider astProvider;
    private final DiagnosticProvider diagnosticProvider;
    private final QuickFixProvider quickFixProvider;
    private final SymbolTableBuilder symbolTableBuilder;
    private final RuleRepository ruleRepository;

    public BatchInspectionRunnerImpl(
            DslFileMatcher fileMatcher,
            DslAstProvider astProvider,
            DiagnosticProvider diagnosticProvider,
            QuickFixProvider quickFixProvider,
            SymbolTableBuilder symbolTableBuilder,
            RuleRepository ruleRepository) {
        this.fileMatcher = Objects.requireNonNull(fileMatcher, "fileMatcher must not be null");
        this.astProvider = Objects.requireNonNull(astProvider, "astProvider must not be null");
        this.diagnosticProvider = Objects.requireNonNull(diagnosticProvider, "diagnosticProvider must not be null");
        this.quickFixProvider = Objects.requireNonNull(quickFixProvider, "quickFixProvider must not be null");
        this.symbolTableBuilder = Objects.requireNonNull(symbolTableBuilder, "symbolTableBuilder must not be null");
        this.ruleRepository = Objects.requireNonNull(ruleRepository, "ruleRepository must not be null");
    }

    @Override
    public BatchInspectionResult runOnFile(String filePath) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public BatchInspectionResult runOnDirectory(String directoryPath) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
```

Run: `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.BatchInspectionRunnerImplTest"`
Expected: 构造器 null 检查测试 PASS，runOnFile/runOnDirectory null 检查测试 FAIL（因为 UnsupportedOperationException，不是 NullPointerException）

- [ ] **Step 3: 添加 runOnFile/runOnDirectory null 检查到实现，让所有现有测试 PASS**

更新 `BatchInspectionRunnerImpl.java` 中的方法签名：

```java
    @Override
    public BatchInspectionResult runOnFile(String filePath) {
        Objects.requireNonNull(filePath, "filePath must not be null");
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public BatchInspectionResult runOnDirectory(String directoryPath) {
        Objects.requireNonNull(directoryPath, "directoryPath must not be null");
        throw new UnsupportedOperationException("Not yet implemented");
    }
```

Run: `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.BatchInspectionRunnerImplTest"`
Expected: 所有 null 检查测试 PASS

- [ ] **Step 4: 添加 runOnFile 测试（非DSL文件 → skippedFiles=1, totalFiles=0）**

在 `BatchInspectionRunnerImplTest` 中添加：

```java
    @Test
    void runOnFileSkipsNonDslFile() {
        Path tempFile = createTempXmlFile("<html><body>not dsl</body></html>");
        stubMatcher = new StubDslFileMatcher(false);
        runner = new BatchInspectionRunnerImpl(
                stubMatcher, stubAstProvider, stubDiagnosticProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository);
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(0, result.getTotalFiles());
        assertEquals(1, result.getSkippedFiles());
        assertEquals(0, result.getFileResults().size());
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
```

Run: `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.BatchInspectionRunnerImplTest"`
Expected: runOnFileSkipsNonDslFile FAIL（UnsupportedOperationException）

- [ ] **Step 5: 实现 runOnFile 基础逻辑（非DSL文件 + DSL文件管线）**

更新 `BatchInspectionRunnerImpl.java`：

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

    public BatchInspectionRunnerImpl(
            DslFileMatcher fileMatcher,
            DslAstProvider astProvider,
            DiagnosticProvider diagnosticProvider,
            QuickFixProvider quickFixProvider,
            SymbolTableBuilder symbolTableBuilder,
            RuleRepository ruleRepository) {
        this.fileMatcher = Objects.requireNonNull(fileMatcher, "fileMatcher must not be null");
        this.astProvider = Objects.requireNonNull(astProvider, "astProvider must not be null");
        this.diagnosticProvider = Objects.requireNonNull(diagnosticProvider, "diagnosticProvider must not be null");
        this.quickFixProvider = Objects.requireNonNull(quickFixProvider, "quickFixProvider must not be null");
        this.symbolTableBuilder = Objects.requireNonNull(symbolTableBuilder, "symbolTableBuilder must not be null");
        this.ruleRepository = Objects.requireNonNull(ruleRepository, "ruleRepository must not be null");
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
                    .totalFiles(0)
                    .skippedFiles(1)
                    .errorCount(0)
                    .warningCount(0)
                    .infoCount(0)
                    .fileResults(List.of())
                    .build();
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
                        .filePath(filePath)
                        .diagnostics(List.of())
                        .fixActions(List.of())
                        .build());
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
                .totalFiles(totalFiles)
                .skippedFiles(skippedFiles)
                .errorCount(errorCount)
                .warningCount(warningCount)
                .infoCount(infoCount)
                .fileResults(fileResults)
                .build();
    }

    private FileDiagnosticResult analyzeFile(String filePath, String content) {
        DslFileNode ast = astProvider.getDslAst(filePath, content);
        List<Diagnostic> diagnostics = diagnosticProvider.analyze(ast, ruleRepository, symbolTableBuilder);
        List<FixAction> fixActions = quickFixProvider.getFixActions(diagnostics);
        return FileDiagnosticResult.builder()
                .filePath(filePath)
                .diagnostics(diagnostics)
                .fixActions(fixActions)
                .build();
    }

    private BatchInspectionResult buildSingleFileResult(FileDiagnosticResult fileResult) {
        int errorCount = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.ERROR);
        int warningCount = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.WARNING);
        int infoCount = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.INFO);
        return BatchInspectionResult.builder()
                .totalFiles(1)
                .skippedFiles(0)
                .errorCount(errorCount)
                .warningCount(warningCount)
                .infoCount(infoCount)
                .fileResults(List.of(fileResult))
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
```

Run: `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.BatchInspectionRunnerImplTest"`
Expected: 所有测试 PASS

- [ ] **Step 6: 添加更多 runOnFile 测试（DSL文件管线执行 + 诊断计数）**

在 `BatchInspectionRunnerImplTest` 中添加：

```java
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
```

Run: `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.BatchInspectionRunnerImplTest"`
Expected: PASS

- [ ] **Step 7: 添加 runOnDirectory 测试**

在 `BatchInspectionRunnerImplTest` 中添加：

```java
    @Test
    void runOnDirectoryScansXmlFiles() {
        Path dir = createTempDirWithFiles();
        BatchInspectionResult result = runner.runOnDirectory(dir.toString());
        assertEquals(1, result.getTotalFiles());
        assertEquals(1, result.getSkippedFiles());
        assertEquals(1, result.getErrorCount());
    }

    @Test
    void runOnDirectoryThrowsExceptionForNonexistentDir() {
        assertThrows(BatchInspectionException.class, () -> runner.runOnDirectory("/nonexistent/directory"));
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
```

Run: `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.BatchInspectionRunnerImplTest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionRunnerImpl.java
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionRunnerImplTest.java
git commit -m "feat(m7): implement BatchInspectionRunnerImpl with tests"
```

---

### Task 5: TerminalFormatter - TDD

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/TerminalFormatter.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/TerminalFormatterTest.java`

- [ ] **Step 1: 编写 TerminalFormatterTest（formatDiagnostic + ANSI颜色 + noColor）**

```java
package com.huawei.theme.analysis.core.batchinspection;

import java.util.List;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.batchinspection.model.FileDiagnosticResult;
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalFormatterTest {

    private final TerminalFormatter colorFormatter = new TerminalFormatter(false);
    private final TerminalFormatter noColorFormatter = new TerminalFormatter(true);

    private Diagnostic createDiagnostic(DiagnosticSeverity severity, String filePath, int line, int column, String ruleId, String message) {
        return Diagnostic.builder()
                .severity(severity)
                .ruleId(ruleId)
                .message(message)
                .filePath(filePath)
                .line(line)
                .column(column)
                .build();
    }

    @Test
    void formatDiagnosticWithColorError() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "theme.xml", 15, 3, "SEM-REF-001", "引用未定义变量 #steps_value");
        String result = colorFormatter.formatDiagnostic(diag);
        assertTrue(result.contains("\u001B[31m"));
        assertTrue(result.contains("\u001B[0m"));
        assertTrue(result.contains("theme.xml:15:3: error: 引用未定义变量 #steps_value [SEM-REF-001]"));
    }

    @Test
    void formatDiagnosticWithColorWarning() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.WARNING, "theme.xml", 20, 5, "SEM-SCOPE-001", "scope not allowed");
        String result = colorFormatter.formatDiagnostic(diag);
        assertTrue(result.contains("\u001B[33m"));
    }

    @Test
    void formatDiagnosticWithColorInfo() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.INFO, "theme.xml", 25, 1, "SEM-INFO-001", "info message");
        String result = colorFormatter.formatDiagnostic(diag);
        assertTrue(result.contains("\u001B[34m"));
    }

    @Test
    void formatDiagnosticNoColorMode() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "theme.xml", 15, 3, "SEM-REF-001", "引用未定义变量 #steps_value");
        String result = noColorFormatter.formatDiagnostic(diag);
        assertFalse(result.contains("\u001B["));
        assertEquals("theme.xml:15:3: error: 引用未定义变量 #steps_value [SEM-REF-001]", result);
    }

    @Test
    void formatSuggestedFixes() {
        List<SuggestedFix> fixes = List.of(
                SuggestedFix.builder().text("声明Var name=\"steps_value\"").build(),
                SuggestedFix.builder().text("使用全局变量替代").build()
        );
        String result = noColorFormatter.formatSuggestedFixes(fixes);
        assertTrue(result.contains("建议修复: 声明Var name=\"steps_value\""));
        assertTrue(result.contains("建议修复: 使用全局变量替代"));
    }

    @Test
    void formatSuggestedFixesEmptyList() {
        String result = noColorFormatter.formatSuggestedFixes(List.of());
        assertEquals("", result);
    }

    @Test
    void formatSummary() {
        BatchInspectionResult batchResult = BatchInspectionResult.builder()
                .errorCount(3)
                .warningCount(1)
                .infoCount(2)
                .build();
        String result = noColorFormatter.formatSummary(batchResult);
        assertEquals("3 errors, 1 warnings, 2 info", result);
    }

    @Test
    void formatFullReport() {
        Diagnostic diag1 = createDiagnostic(DiagnosticSeverity.ERROR, "theme.xml", 15, 3, "SEM-REF-001", "引用未定义变量");
        Diagnostic diag2 = createDiagnostic(DiagnosticSeverity.WARNING, "layout.xml", 5, 1, "SEM-SCOPE-001", "scope not allowed");
        FileDiagnosticResult fileResult1 = FileDiagnosticResult.builder()
                .filePath("theme.xml")
                .diagnostics(List.of(diag1))
                .fixActions(List.of())
                .build();
        FileDiagnosticResult fileResult2 = FileDiagnosticResult.builder()
                .filePath("layout.xml")
                .diagnostics(List.of(diag2))
                .fixActions(List.of())
                .build();
        BatchInspectionResult batchResult = BatchInspectionResult.builder()
                .totalFiles(2)
                .errorCount(1)
                .warningCount(1)
                .infoCount(0)
                .fileResults(List.of(fileResult1, fileResult2))
                .build();
        String report = noColorFormatter.formatFullReport(batchResult);
        assertTrue(report.contains("theme.xml:15:3: error: 引用未定义变量 [SEM-REF-001]"));
        assertTrue(report.contains("layout.xml:5:1: warning: scope not allowed [SEM-SCOPE-001]"));
        assertTrue(report.contains("1 errors, 1 warnings, 0 info"));
    }
}
```

- [ ] **Step 2: 实现 TerminalFormatter**

```java
package com.huawei.theme.analysis.core.batchinspection;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.batchinspection.model.FileDiagnosticResult;
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

public class TerminalFormatter {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";

    private final boolean noColor;

    public TerminalFormatter(boolean noColor) {
        this.noColor = noColor;
    }

    public String formatDiagnostic(Diagnostic diagnostic) {
        String severityLabel = severityLabel(diagnostic.getSeverity());
        String colorPrefix = colorForSeverity(diagnostic.getSeverity());
        String base = diagnostic.getFilePath() + ":" + diagnostic.getLine() + ":" + diagnostic.getColumn()
                + ": " + severityLabel + ": " + diagnostic.getMessage() + " [" + diagnostic.getRuleId() + "]";
        if (noColor) {
            return base;
        }
        return colorPrefix + base + ANSI_RESET;
    }

    public String formatSuggestedFixes(List<SuggestedFix> fixes) {
        if (fixes == null || fixes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (SuggestedFix fix : fixes) {
            sb.append("  建议修复: ").append(fix.getText()).append("\n");
        }
        return sb.toString();
    }

    public String formatSummary(BatchInspectionResult result) {
        return result.getErrorCount() + " errors, " + result.getWarningCount() + " warnings, " + result.getInfoCount() + " info";
    }

    public String formatFileResult(FileDiagnosticResult result) {
        StringBuilder sb = new StringBuilder();
        List<Diagnostic> sorted = sortDiagnostics(result.getDiagnostics());
        for (Diagnostic d : sorted) {
            sb.append(formatDiagnostic(d)).append("\n");
            if (d.getSuggestedFixes() != null && !d.getSuggestedFixes().isEmpty()) {
                sb.append(formatSuggestedFixes(d.getSuggestedFixes()));
            }
        }
        sb.append(fileSummary(result)).append("\n");
        return sb.toString();
    }

    public String formatFullReport(BatchInspectionResult result) {
        StringBuilder sb = new StringBuilder();
        for (FileDiagnosticResult fileResult : result.getFileResults()) {
            if (fileResult.getDiagnostics() != null && !fileResult.getDiagnostics().isEmpty()) {
                sb.append(formatFileResult(fileResult));
            }
        }
        sb.append("\n").append(formatSummary(result)).append("\n");
        return sb.toString();
    }

    private String severityLabel(DiagnosticSeverity severity) {
        switch (severity) {
            case ERROR: return "error";
            case WARNING: return "warning";
            case INFO: return "info";
            default: return "unknown";
        }
    }

    private String colorForSeverity(DiagnosticSeverity severity) {
        if (noColor) {
            return "";
        }
        switch (severity) {
            case ERROR: return ANSI_RED;
            case WARNING: return ANSI_YELLOW;
            case INFO: return ANSI_BLUE;
            default: return "";
        }
    }

    private List<Diagnostic> sortDiagnostics(List<Diagnostic> diagnostics) {
        if (diagnostics == null) {
            return List.of();
        }
        return diagnostics.stream()
                .sorted(Comparator.comparing(Diagnostic::getSeverity, severityOrder())
                        .thenComparing(Diagnostic::getFilePath)
                        .thenComparing(Diagnostic::getLine))
                .collect(Collectors.toList());
    }

    private Comparator<DiagnosticSeverity> severityOrder() {
        return Comparator.comparingInt(s -> {
            switch (s) {
                case ERROR: return 0;
                case WARNING: return 1;
                case INFO: return 2;
                default: return 3;
            }
        });
    }

    private String fileSummary(FileDiagnosticResult result) {
        int errors = countBySeverity(result.getDiagnostics(), DiagnosticSeverity.ERROR);
        int warnings = countBySeverity(result.getDiagnostics(), DiagnosticSeverity.WARNING);
        int infos = countBySeverity(result.getDiagnostics(), DiagnosticSeverity.INFO);
        return errors + " errors, " + warnings + " warnings, " + infos + " info";
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

- [ ] **Step 3: 运行 TerminalFormatter 测试**

Run: `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.TerminalFormatterTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/TerminalFormatter.java
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/TerminalFormatterTest.java
git commit -m "feat(m7): implement TerminalFormatter with ANSI color support"
```

---

### Task 6: 集成测试 (使用真实规则库)

**Files:**
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionIntegrationTest.java`

- [ ] **Step 1: 编写集成测试**

```java
package com.huawei.theme.analysis.core.batchinspection;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.fileidentification.DslFileIdentifier;
import com.huawei.theme.analysis.core.function.JsonFunctionSignatureLoader;
import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionRegistry;
import com.huawei.theme.analysis.core.quickfix.QuickFixProvider;
import com.huawei.theme.analysis.core.quickfix.QuickFixProviderImpl;
import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.AnalyzerRegistry;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProvider;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProviderImpl;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilder;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilderImpl;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;
import com.huawei.theme.analysis.core.syntaxanalysis.DslAstProvider;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchInspectionIntegrationTest {

    private static RuleRepository ruleRepo;
    private static BatchInspectionRunner runner;
    private static TerminalFormatter formatter;

    @BeforeAll
    static void setup() {
        String rulesDir = System.getProperty("user.dir") + "/src/main/resources/rules";
        com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary functionLibrary = new JsonFunctionSignatureLoader().loadFromClasspath();
        ruleRepo = new JsonRuleLoader().loadFromDirectory(rulesDir, functionLibrary);
        DslFileMatcher fileMatcher = new DslFileIdentifier(ruleRepo);
        DslAstProvider astProvider = new AstBuilder(ruleRepo);
        DiagnosticProvider diagnosticProvider = new DiagnosticProviderImpl();
        SymbolTableBuilder symbolTableBuilder = new SymbolTableBuilderImpl();
        AnalyzerRegistry.init();
        FixActionRegistry.init(ruleRepo);
        QuickFixProvider quickFixProvider = new QuickFixProviderImpl();
        runner = new BatchInspectionRunnerImpl(
                fileMatcher, astProvider, diagnosticProvider,
                quickFixProvider, symbolTableBuilder, ruleRepo);
        formatter = new TerminalFormatter(true);
    }

    @Test
    void runOnFileWithDslContent() {
        Path tempFile = writeTempFile("lockscreen.xml", "<Lockscreen>\n  <Var name=\"test_var\" type=\"number\" const=\"true\" expression=\"1\"/>\n</Lockscreen>");
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertTrue(result.getTotalFiles() > 0);
        assertTrue(result.getFileResults().size() > 0);
    }

    @Test
    void runOnFileWithNonDslContent() {
        Path tempFile = writeTempFile("nondsl.xml", "<html><body>not dsl</body></html>");
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(0, result.getTotalFiles());
        assertEquals(1, result.getSkippedFiles());
    }

    @Test
    void runOnDirectoryWithMixedFiles() {
        Path dir = Files.createTempDirectory("batch-integration-test");
        Path dslFile = dir.resolve("lockscreen.xml");
        Files.writeString(dslFile, "<Lockscreen>\n  <Var name=\"test_var\" type=\"number\" const=\"true\" expression=\"1\"/>\n</Lockscreen>", StandardCharsets.UTF_8);
        Path nonDslFile = dir.resolve("nondsl.xml");
        Files.writeString(nonDslFile, "<html><body>not dsl</body></html>", StandardCharsets.UTF_8);
        BatchInspectionResult result = runner.runOnDirectory(dir.toString());
        assertTrue(result.getTotalFiles() > 0);
        assertTrue(result.getSkippedFiles() > 0);
    }

    @Test
    void terminalFormatterOutputIsValid() {
        Path tempFile = writeTempFile("lockscreen.xml", "<Lockscreen>\n  <Var name=\"test_var\" type=\"number\" const=\"true\" expression=\"1\"/>\n</Lockscreen>");
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        if (!result.getFileResults().isEmpty() && !result.getFileResults().get(0).getDiagnostics().isEmpty()) {
            String output = formatter.formatFullReport(result);
            assertTrue(output.contains("lockscreen.xml"));
            assertTrue(output.contains("errors"));
        }
    }

    private Path writeTempFile(String name, String content) {
        try {
            Path dir = Files.createTempDirectory("batch-int-test");
            Path file = dir.resolve(name);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 2: 运行集成测试**

Run: `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.BatchInspectionIntegrationTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionIntegrationTest.java
git commit -m "feat(m7): add BatchInspection integration test"
```

---

### Task 7: 全量测试验证 + Core依赖隔离检查

- [ ] **Step 1: 运行全部 feature:analysis 测试**

Run: `.\gradlew.bat :feature:analysis:test`
Expected: 所有测试 PASS（包括 M0-M5 原有测试 + M7 新增测试）

- [ ] **Step 2: 运行 Core-Plugin 依赖隔离检查**

Run: `.\gradlew.bat :feature:analysis:checkCoreIntellijDependency`
Expected: PASSED (0 violations)，确认新增代码无 `com.intellij.*` import

- [ ] **Step 3: 最终 Commit（如有修复）**

如果 Step 1/2 全部通过，无需额外 commit。否则修复问题后 commit。
