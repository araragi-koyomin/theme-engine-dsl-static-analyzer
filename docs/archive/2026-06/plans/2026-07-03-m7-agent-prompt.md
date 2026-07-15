# M7 批量检查功能 - Agent 实现 Prompt

## 约束规则（必须严格遵守）

1. **禁止 commit** - 不做任何 git commit，所有代码改动完成后由人工审核后再决定是否 commit
2. **禁止阻塞式 gradlew 测试** - 不运行 `.\gradlew.bat :feature:analysis:test` 这种全量测试命令。只运行单个测试类级别的命令，例如 `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.model.*"`。如果测试命令超过 60 秒未完成就停止。
3. **Core 层禁止 import com.intellij.*** - 所有新增代码在 core 包下，不得引入任何 IntelliJ SDK 依赖
4. **遵循 AGENTS.md 代码风格** - 缩进4空格、大驼峰类名、小驼峰方法名、导入顺序分组、@Data/@Builder注解、不抛受检异常
5. **不添加注释** - AGENTS.md 明确要求不添加注释（除非被要求）

## 项目位置

- 工作目录：`C:\Users\30991\theme-engine-dsl-static-analyzer`
- 源码根目录：`feature/analysis/src/main/java/com/huawei/theme/analysis/core/`
- 测试根目录：`feature/analysis/src/test/java/com/huawei/theme/analysis/core/`
- 资源目录：`feature/analysis/src/main/resources/`
- 模块名：`feature:analysis`

## 上游依赖接口签名（必须按此消费）

### M1 DslFileMatcher
```java
package com.huawei.theme.analysis.core.fileidentification;
public interface DslFileMatcher {
    boolean isDslFile(String filePath, String content);
}
```

### M1 DslFileIdentifier（构造器接收 RuleRepository）
```java
package com.huawei.theme.analysis.core.fileidentification;
public class DslFileIdentifier implements DslFileMatcher {
    private static final String XML_EXTENSION = ".xml";
    private final Set<String> rootElementNames;
    public DslFileIdentifier(RuleRepository ruleRepository) {
        this.rootElementNames = Set.copyOf(ruleRepository.getRootElementNames());
    }
    @Override
    public boolean isDslFile(String filePath, String content) {
        // 双重识别：扩展名检查 + 根元素标签匹配
    }
}
```

### M3 DslAstProvider
```java
package com.huawei.theme.analysis.core.syntaxanalysis;
public interface DslAstProvider {
    DslFileNode getDslAst(String filePath, String content);
}
```

### M3 AstBuilder（构造器可选接收 RuleRepository）
```java
package com.huawei.theme.analysis.core.syntaxanalysis;
public class AstBuilder implements DslAstProvider {
    public AstBuilder() { this(null); }
    public AstBuilder(RuleRepository ruleRepository) { this.ruleRepository = ruleRepository; }
    @Override
    public DslFileNode getDslAst(String filePath, String content) { ... }
}
```

### M4 DiagnosticProvider
```java
package com.huawei.theme.analysis.core.semanticanalysis;
public interface DiagnosticProvider {
    List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder symbolTableBuilder);
}
```

### M4 DiagnosticProviderImpl
```java
package com.huawei.theme.analysis.core.semanticanalysis;
public class DiagnosticProviderImpl implements DiagnosticProvider {
    @Override
    public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder symbolTableBuilder) { ... }
}
```

### M4 SymbolTableBuilder
```java
package com.huawei.theme.analysis.core.semanticanalysis;
public interface SymbolTableBuilder {
    SymbolTable buildGlobal(DslFileNode fileNode, RuleRepository ruleRepository);
    SymbolTable build(DslElementNode elementNode, SymbolTable parent, RuleRepository ruleRepository);
}
```

### M5 QuickFixProvider
```java
package com.huawei.theme.analysis.core.quickfix;
public interface QuickFixProvider {
    List<FixAction> getFixActions(Diagnostic diagnostic);
    default List<FixAction> getFixActions(List<Diagnostic> diagnostics) {
        // 遍历所有 diagnostic，逐个调用 getFixActions(diagnostic) 合并结果
    }
}
```

### M5 QuickFixProviderImpl
```java
package com.huawei.theme.analysis.core.quickfix;
public class QuickFixProviderImpl implements QuickFixProvider { ... }
```

### M4 AnalyzerRegistry（集成测试需要 init）
```java
package com.huawei.theme.analysis.core.semanticanalysis;
public class AnalyzerRegistry {
    public static void init();
    public static List<DslAnalyzer> getAnalyzers();
}
```

### M5 FixActionRegistry（集成测试需要 init）
```java
package com.huawei.theme.analysis.core.quickfix;
public class FixActionRegistry {
    public static void init(RuleRepository ruleRepository);
}
```

### M2 RuleRepository（158行接口）
```java
package com.huawei.theme.analysis.core.rulelibrary;
public interface RuleRepository {
    Optional<DslElementRule> getElementRule(String elementName);
    List<DslElementRule> getAllElementRules();
    List<String> getAllElementNames();
    List<String> getRootElementNames();
    Optional<AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName);
    Optional<String> resolveAttrAlias(String elementName, String attrName);
    Set<String> getCanonicalAttrNames(String elementName);
    List<String> getAllowedParents(String elementName);
    List<String> getAllowedChildren(String elementName);
    List<RuleConstraint> getConstraints(String elementName);
    Optional<DslGlobalVar> getGlobalVar(String varName);
    List<DslGlobalVar> getAllGlobalVars();
    Optional<RuleSource> getRuleSource(String ruleId);
    FunctionSignatureLibrary getFunctionSignatureLibrary();
}
```

### M2 JsonRuleLoader（一步加载）
```java
package com.huawei.theme.analysis.core.rulelibrary;
public class JsonRuleLoader {
    public RuleRepository loadFromDirectory(String rulesDir);
    public RuleRepository loadFromDirectory(String rulesDir, FunctionSignatureLibrary functionLibrary);
}
```

### M0 FunctionSignatureLibrary + JsonFunctionSignatureLoader
```java
package com.huawei.theme.analysis.core.function;
public class JsonFunctionSignatureLoader {
    public FunctionSignatureLibrary loadFromClasspath();
}
```

### 共享数据模型
```java
// Diagnostic - @Data @Builder
DiagnosticSeverity severity; String ruleId; String message; String filePath;
int line; int column; int endLine; int endColumn; DslAstNode astNode;
List<SuggestedFix> suggestedFixes; String ruleDocUrl;

// DiagnosticSeverity enum: ERROR, WARNING, INFO

// DslFileNode extends DslAstNode: String filePath; String xmlDeclaration; DslElementNode rootElement;

// DslAstNode abstract: String text; int line; int column; int endLine; int endColumn;

// DslElementNode extends DslAstNode: String tagName; List<DslAttributeNode> attributes;
// List<DslElementNode> childElements; boolean selfClosing; boolean hasError; String errorMessage; DslAstNode parent;

// SuggestedFix - @Data @Builder: String text; String type; String target; String value; String range;

// FixAction - @Data @Builder: FixActionType fixType; TextRange targetRange; String replacementText;
// List<CandidateItem> candidates; String description;

// RuleSource - @Data @Builder: String ruleId; String category; String description; String docUrl;

// SymbolTable - @Data @Builder: SymbolTable parent; Map<String, VarDeclaration> declarations; Set<String> elementNames;
```

## 测试模式参考

项目使用 JUnit 5，不使用 Mockito。测试类使用内部类 Stub 实现替代 mock：

```java
// 示例：MockRuleRepository 内部类模式（见 QuickFixIntegrationTest.java）
private static class MockRuleRepository implements RuleRepository {
    // 实现所有 RuleRepository 方法，返回固定测试数据
    @Override
    public Optional<DslElementRule> getElementRule(String elementName) {
        if ("Var".equals(elementName)) {
            return Optional.of(DslElementRule.builder().elementName("Var").build());
        }
        return Optional.empty();
    }
    // ... 其他方法返回 Collections.emptyList() / Optional.empty() 等
}

// 测试类风格：package-private（无 public 修饰符）
class BatchInspectionRunnerImplTest {
    // 不用 public class
}
```

---

## 实现计划（逐 Task 执行）

### Task 1: 数据模型 (BatchInspectionResult + FileDiagnosticResult)

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/model/BatchInspectionResult.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/model/FileDiagnosticResult.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/model/BatchInspectionResultTest.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/model/FileDiagnosticResultTest.java`

**BatchInspectionResult.java:**

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

**FileDiagnosticResult.java:**

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

**BatchInspectionResultTest.java:**

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

**FileDiagnosticResultTest.java:**

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

**验证:** 运行 `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.model.*"` 确认 PASS

---

### Task 2: BatchInspectionException

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionException.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionExceptionTest.java`

**BatchInspectionException.java:**

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

**BatchInspectionExceptionTest.java:**

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

**验证:** 运行 `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.BatchInspectionExceptionTest"` 确认 PASS

---

### Task 3: BatchInspectionRunner 接口

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionRunner.java`

**BatchInspectionRunner.java:**

```java
package com.huawei.theme.analysis.core.batchinspection;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;

public interface BatchInspectionRunner {
    BatchInspectionResult runOnFile(String filePath);
    BatchInspectionResult runOnDirectory(String directoryPath);
}
```

---

### Task 4: BatchInspectionRunnerImpl - TDD

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionRunnerImpl.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionRunnerImplTest.java`

先创建完整测试文件，再创建实现文件。

**BatchInspectionRunnerImplTest.java（完整版，包含所有测试方法）：**

```java
package com.huawei.theme.analysis.core.batchinspection;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
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
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.syntaxanalysis.DslAstProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void runOnFileSkipsNonDslFile() {
        Path tempFile = createTempXmlFile("<html><body>not dsl</body></html>");
        StubDslFileMatcher nonDslMatcher = new StubDslFileMatcher(false);
        BatchInspectionRunnerImpl nonDslRunner = new BatchInspectionRunnerImpl(
                nonDslMatcher, stubAstProvider, stubDiagnosticProvider,
                stubQuickFixProvider, stubSymbolTableBuilder, stubRuleRepository);
        BatchInspectionResult result = nonDslRunner.runOnFile(tempFile.toString());
        assertEquals(0, result.getTotalFiles());
        assertEquals(1, result.getSkippedFiles());
        assertEquals(0, result.getFileResults().size());
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
        BatchInspectionResult result = runner.runOnDirectory(dir.toString());
        assertEquals(1, result.getTotalFiles());
        assertEquals(1, result.getSkippedFiles());
        assertEquals(1, result.getErrorCount());
    }

    @Test
    void runOnDirectoryThrowsExceptionForNonexistentDir() {
        assertThrows(BatchInspectionException.class, () -> runner.runOnDirectory("/nonexistent/directory"));
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
```

**BatchInspectionRunnerImpl.java（完整实现）：**

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

**验证:** 运行 `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.BatchInspectionRunnerImplTest"` 确认 PASS

---

### Task 5: TerminalFormatter - TDD

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/TerminalFormatter.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/TerminalFormatterTest.java`

**TerminalFormatterTest.java:**

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

**TerminalFormatter.java:**

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

**验证:** 运行 `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.TerminalFormatterTest"` 确认 PASS

---

### Task 6: 集成测试 (使用真实规则库)

**Files:**
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionIntegrationTest.java`

**BatchInspectionIntegrationTest.java:**

```java
package com.huawei.theme.analysis.core.batchinspection;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary;
import com.huawei.theme.analysis.core.fileidentification.DslFileIdentifier;
import com.huawei.theme.analysis.core.function.JsonFunctionSignatureLoader;
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
        FunctionSignatureLibrary functionLibrary = new JsonFunctionSignatureLoader().loadFromClasspath();
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
    void runOnFileWithNonDslContent() throws Exception {
        Path dir = Files.createTempDirectory("batch-int-nondsl");
        Path nonDslFile = dir.resolve("nondsl.xml");
        Files.writeString(nonDslFile, "<html><body>not dsl</body></html>", StandardCharsets.UTF_8);
        BatchInspectionResult result = runner.runOnFile(nonDslFile.toString());
        assertEquals(0, result.getTotalFiles());
        assertEquals(1, result.getSkippedFiles());
    }

    @Test
    void runOnDirectoryWithMixedFiles() throws Exception {
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

**验证:** 运行 `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.BatchInspectionIntegrationTest"` 确认 PASS（注意：集成测试加载真实规则库可能较慢，设置 timeout 120秒）

---

### Task 7: 全量测试验证 + Core依赖隔离检查

1. 运行 `.\gradlew.bat :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.*"` 确认所有 M7 测试 PASS
2. 运行 `.\gradlew.bat :feature:analysis:checkCoreIntellijDependency` 确认 PASSED (0 violations)
3. 如果测试失败，分析失败原因并修复代码

---

## 最终交付要求

完成所有 Task 后，报告：
- 所有创建的文件路径列表
- 每个测试类的测试结果（PASS/FAIL）
- Core 依赖隔离检查结果
- 任何发现的问题或改进建议
