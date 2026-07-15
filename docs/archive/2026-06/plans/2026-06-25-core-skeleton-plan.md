# Core Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the Java code skeleton for dsl-analyzer-core cross-module interfaces and shared data models (shared + M0~M4), per the design spec at `docs/superpowers/specs/2026-06-25-core-skeleton-design.md`.

**Architecture:** Method B — shared data model subpackage + interface isolation. Cross-module data models in `core/shared/`, interfaces and single-module models in their respective module packages. Diagnostic physically migrated from `core/diagnostic/` to `core/shared/diagnostic/`. DIP fix: `ExpressionAstNode` interface in shared/ast/, M0 `ExpressionNode` implements it.

**Tech Stack:** Java 17, Lombok (@Data/@Builder), JUnit 5, Gradle 8.2

---

## File Structure

```
feature/analysis/src/main/java/com/huawei/theme/analysis/core/
├── shared/ast/
│   ├── DslAstNode.java            ← abstract base, fields: text/line/column
│   ├── ExpressionAstNode.java      ← interface (DIP fix), 4 methods
│   ├── ExpressionKind.java         ← enum, 8 values
│   ├── DslFileNode.java            ← extends DslAstNode, field: xmlDeclaration/rootElement
│   ├── DslElementNode.java         ← extends DslAstNode, fields: tagName/attributes/childElements/selfClosing/hasError/errorMessage
│   ├── DslAttributeNode.java       ← extends DslAstNode, fields: name/value
│   ├── DslAttributeValueNode.java  ← extends DslAstNode, fields: rawValue/expression(Optional)/isLiteral
├── shared/type/
│   ├── DslType.java                ← abstract base, abstract getName()
│   ├── DslNumberType.java          ← getName()="number"
│   ├── DslStringType.java          ← getName()="string"
│   ├── DslArrayType.java           ← @Data, baseType field, getName()="array"
├── shared/diagnostic/              ← MIGRATED from core/diagnostic/
│   ├── Diagnostic.java             ← @Data @Builder, suggestedFixes=@Builder.Default
│   ├── DiagnosticSeverity.java     ← enum ERROR/WARNING/INFO
│   ├── TextRange.java              ← @Data @Builder
│   ├── adapter/DiagnosticSeverityAdapter.java ← migrated, package path updated
├── expression/
│   ├── ExpressionNode.java         ← abstract, implements ExpressionAstNode
│   ├── FunctionSignatureLibrary.java ← interface, 3 methods
│   ├── model/FunctionSignature.java ← @Data @Builder
│   ├── model/FunctionParam.java    ← @Data @Builder
├── ruledsl/
│   ├── RuleDslEvaluator.java       ← interface, evaluate(condition, context)
│   ├── EvaluationContext.java      ← @Data @Builder
├── function/                       ← placeholder package (implementation deferred)
├── fileidentification/
│   ├── DslFileMatcher.java         ← interface, isDslFile(filePath, content)
├── rulelibrary/                    ← EXISTING, only import path changes for DiagnosticSeverity
│   ├── model/RuleConstraint.java   ← import path update only
├── syntaxanalysis/
│   ├── DslAstProvider.java         ← interface, getDslAst(filePath, content)
├── semanticanalysis/
│   ├── DiagnosticProvider.java     ← interface, analyzeFile(filePath, content)
│   ├── model/SymbolTable.java      ← @Data @Builder
│   ├── model/VarDeclaration.java   ← @Data @Builder
│   ├── model/VarReference.java     ← @Data @Builder
│   ├── model/ReferenceKind.java    ← enum NUMERIC/STRING

feature/analysis/src/test/java/com/huawei/theme/analysis/core/
├── shared/ast/DslAstNodeTest.java
├── shared/type/DslTypeTest.java
├── shared/diagnostic/DiagnosticTest.java
├── expression/ExpressionNodeTest.java
├── expression/FunctionSignatureLibraryTest.java
├── ruledsl/EvaluationContextTest.java
├── fileidentification/DslFileMatcherTest.java
├── syntaxanalysis/DslAstProviderTest.java
├── semanticanalysis/DiagnosticProviderTest.java
├── semanticanalysis/model/SymbolTableTest.java
```

**Deleted files (physically migrated):**
- `core/diagnostic/Diagnostic.java`
- `core/diagnostic/DiagnosticSeverity.java`
- `core/diagnostic/TextRange.java`
- `core/diagnostic/adapter/DiagnosticSeverityAdapter.java`

---

### Task 1: shared/ast/ — AST Node Hierarchy

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/ast/DslAstNode.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/ast/ExpressionAstNode.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/ast/ExpressionKind.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/ast/DslFileNode.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/ast/DslElementNode.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/ast/DslAttributeNode.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/ast/DslAttributeValueNode.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/shared/ast/DslAstNodeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.huawei.theme.analysis.core.shared.ast;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslAstNodeTest {

    @Test
    void dslAstNodeBaseFields() {
        DslElementNode node = DslElementNode.builder()
                .text("<Var>")
                .line(5)
                .column(10)
                .tagName("Var")
                .attributes(List.of())
                .childElements(List.of())
                .selfClosing(false)
                .hasError(false)
                .errorMessage(null)
                .build();
        assertEquals("<Var>", node.getText());
        assertEquals(5, node.getLine());
        assertEquals(10, node.getColumn());
    }

    @Test
    void dslFileNodeStructure() {
        DslElementNode root = DslElementNode.builder()
                .text("<Lockscreen>")
                .line(1)
                .column(0)
                .tagName("Lockscreen")
                .attributes(List.of())
                .childElements(List.of())
                .selfClosing(false)
                .hasError(false)
                .errorMessage(null)
                .build();
        DslFileNode fileNode = DslFileNode.builder()
                .text("<Lockscreen>")
                .line(1)
                .column(0)
                .xmlDeclaration("<?xml version=\"1.0\"?>")
                .rootElement(root)
                .build();
        assertEquals("<?xml version=\"1.0\"?>", fileNode.getXmlDeclaration());
        assertEquals("Lockscreen", fileNode.getRootElement().getTagName());
    }

    @Test
    void dslAttributeValueNodeExpressionIsOptional() {
        DslAttributeValueNode literal = DslAttributeValueNode.builder()
                .text("\"hello\"")
                .line(3)
                .column(5)
                .rawValue("hello")
                .expression(Optional.empty())
                .isLiteral(true)
                .build();
        assertFalse(literal.getExpression().isPresent());

        DslAttributeValueNode expr = DslAttributeValueNode.builder()
                .text("#screen_width/2")
                .line(4)
                .column(8)
                .rawValue("#screen_width/2")
                .expression(Optional.of(new StubExpressionAstNode()))
                .isLiteral(false)
                .build();
        assertTrue(expr.getExpression().isPresent());
        assertEquals("#screen_width/2", expr.getExpression().get().getText());
    }

    @Test
    void expressionKindEnumValues() {
        assertEquals(8, ExpressionKind.values().length);
        assertEquals(ExpressionKind.LITERAL, ExpressionKind.valueOf("LITERAL"));
        assertEquals(ExpressionKind.VARIABLE_REF, ExpressionKind.valueOf("VARIABLE_REF"));
        assertEquals(ExpressionKind.FUNCTION_CALL, ExpressionKind.valueOf("FUNCTION_CALL"));
        assertEquals(ExpressionKind.BINARY_EXPR, ExpressionKind.valueOf("BINARY_EXPR"));
        assertEquals(ExpressionKind.UNARY_EXPR, ExpressionKind.valueOf("UNARY_EXPR"));
        assertEquals(ExpressionKind.CONDITIONAL, ExpressionKind.valueOf("CONDITIONAL"));
        assertEquals(ExpressionKind.ARRAY_ACCESS, ExpressionKind.valueOf("ARRAY_ACCESS"));
        assertEquals(ExpressionKind.UNKNOWN, ExpressionKind.valueOf("UNKNOWN"));
    }

    private static class StubExpressionAstNode implements ExpressionAstNode {
        @Override public String getText() { return "#screen_width/2"; }
        @Override public int getLine() { return 4; }
        @Override public int getColumn() { return 8; }
        @Override public ExpressionKind getKind() { return ExpressionKind.BINARY_EXPR; }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.shared.ast.DslAstNodeTest"`
Expected: FAIL — classes not found

- [ ] **Step 3: Write implementation files**

Create `DslAstNode.java`:
```java
package com.huawei.theme.analysis.core.shared.ast;

import lombok.Data;

@Data
public abstract class DslAstNode {
    String text;
    int line;
    int column;
}
```

Create `ExpressionAstNode.java`:
```java
package com.huawei.theme.analysis.core.shared.ast;

public interface ExpressionAstNode {
    String getText();
    int getLine();
    int getColumn();
    ExpressionKind getKind();
}
```

Create `ExpressionKind.java`:
```java
package com.huawei.theme.analysis.core.shared.ast;

public enum ExpressionKind {
    LITERAL,
    VARIABLE_REF,
    FUNCTION_CALL,
    BINARY_EXPR,
    UNARY_EXPR,
    CONDITIONAL,
    ARRAY_ACCESS,
    UNKNOWN
}
```

Create `DslFileNode.java`:
```java
package com.huawei.theme.analysis.core.shared.ast;

import lombok.Data;

@Data
public class DslFileNode extends DslAstNode {
    String xmlDeclaration;
    DslElementNode rootElement;
}
```

Create `DslElementNode.java`:
```java
package com.huawei.theme.analysis.core.shared.ast;

import java.util.List;

import lombok.Data;

@Data
public class DslElementNode extends DslAstNode {
    String tagName;
    List<DslAttributeNode> attributes;
    List<DslElementNode> childElements;
    boolean selfClosing;
    boolean hasError;
    String errorMessage;
}
```

Create `DslAttributeNode.java`:
```java
package com.huawei.theme.analysis.core.shared.ast;

import lombok.Data;

@Data
public class DslAttributeNode extends DslAstNode {
    String name;
    DslAttributeValueNode value;
}
```

Create `DslAttributeValueNode.java`:
```java
package com.huawei.theme.analysis.core.shared.ast;

import java.util.Optional;

import lombok.Data;

@Data
public class DslAttributeValueNode extends DslAstNode {
    String rawValue;
    Optional<ExpressionAstNode> expression;
    boolean isLiteral;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.shared.ast.DslAstNodeTest"`
Expected: PASS

- [ ] **Step 5: Verify tests pass** (commit deferred to Task 9)

---

### Task 2: shared/type/ — Type System Hierarchy

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/type/DslType.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/type/DslNumberType.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/type/DslStringType.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/type/DslArrayType.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/shared/type/DslTypeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.huawei.theme.analysis.core.shared.type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DslTypeTest {

    @Test
    void dslNumberTypeName() {
        DslType type = new DslNumberType();
        assertEquals("number", type.getName());
    }

    @Test
    void dslStringTypeName() {
        DslType type = new DslStringType();
        assertEquals("string", type.getName());
    }

    @Test
    void dslArrayTypeWithBaseType() {
        DslArrayType type = DslArrayType.builder()
                .baseType("number")
                .build();
        assertEquals("array", type.getName());
        assertEquals("number", type.getBaseType());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.shared.type.DslTypeTest"`
Expected: FAIL

- [ ] **Step 3: Write implementation files**

Create `DslType.java`:
```java
package com.huawei.theme.analysis.core.shared.type;

public abstract class DslType {
    public abstract String getName();
}
```

Create `DslNumberType.java`:
```java
package com.huawei.theme.analysis.core.shared.type;

public class DslNumberType extends DslType {
    @Override
    public String getName() {
        return "number";
    }
}
```

Create `DslStringType.java`:
```java
package com.huawei.theme.analysis.core.shared.type;

public class DslStringType extends DslType {
    @Override
    public String getName() {
        return "string";
    }
}
```

Create `DslArrayType.java`:
```java
package com.huawei.theme.analysis.core.shared.type;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DslArrayType extends DslType {
    String baseType;

    @Override
    public String getName() {
        return "array";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.shared.type.DslTypeTest"`
Expected: PASS

- [ ] **Step 5: Verify tests pass** (commit deferred to Task 9)

---

### Task 3: shared/diagnostic/ — Migrate Diagnostic from core/diagnostic/

**Files:**
- Delete: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/diagnostic/Diagnostic.java`
- Delete: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/diagnostic/DiagnosticSeverity.java`
- Delete: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/diagnostic/TextRange.java`
- Delete: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/diagnostic/adapter/DiagnosticSeverityAdapter.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/diagnostic/Diagnostic.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/diagnostic/DiagnosticSeverity.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/diagnostic/TextRange.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/diagnostic/adapter/DiagnosticSeverityAdapter.java`
- Modify: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/rulelibrary/model/RuleConstraint.java` (import path update)
- Modify: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/rulelibrary/JsonRuleLoader.java` (import path update if needed)
- Modify: all 5 test files (import path update)
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/shared/diagnostic/DiagnosticTest.java`

- [ ] **Step 1: Write the failing test for new Diagnostic location**

```java
package com.huawei.theme.analysis.core.shared.diagnostic;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticTest {

    @Test
    void diagnosticBuilderDefaults() {
        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REF-001")
                .message("引用未定义变量")
                .filePath("test.xml")
                .line(10)
                .column(5)
                .build();
        assertEquals(Collections.emptyList(), diag.getSuggestedFixes());
        assertTrue(diag.getSuggestedFixes().isEmpty());
    }

    @Test
    void diagnosticBuilderWithFixes() {
        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.WARNING)
                .ruleId("SEM-VAR-003")
                .message("Var的values与size属性同时存在")
                .filePath("test.xml")
                .line(15)
                .column(3)
                .suggestedFixes(List.of("移除values属性", "移除size属性"))
                .ruleDocUrl("https://dsl-docs.example.com/rules/SEM-VAR-003")
                .build();
        assertEquals(2, diag.getSuggestedFixes().size());
        assertEquals("https://dsl-docs.example.com/rules/SEM-VAR-003", diag.getRuleDocUrl());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticTest"`
Expected: FAIL

- [ ] **Step 3: Create new files in shared/diagnostic/ with updated package paths and @Builder.Default**

Create `Diagnostic.java` (key changes: package path + `@Builder.Default`):
```java
package com.huawei.theme.analysis.core.shared.diagnostic;

import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Diagnostic {
    DiagnosticSeverity severity;
    String ruleId;
    String message;
    String filePath;
    int line;
    int column;
    @Builder.Default List<String> suggestedFixes = Collections.emptyList();
    String ruleDocUrl;
}
```

Create `DiagnosticSeverity.java`:
```java
package com.huawei.theme.analysis.core.shared.diagnostic;

public enum DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}
```

Create `TextRange.java`:
```java
package com.huawei.theme.analysis.core.shared.diagnostic;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TextRange {
    int startLine;
    int startColumn;
    int endLine;
    int endColumn;
}
```

Create `DiagnosticSeverityAdapter.java` (package path update only, content unchanged):
```java
package com.huawei.theme.analysis.core.shared.diagnostic.adapter;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

public class DiagnosticSeverityAdapter extends TypeAdapter<DiagnosticSeverity> {

    @Override
    public void write(JsonWriter out, DiagnosticSeverity severity) throws IOException {
        if (severity == null) {
            out.nullValue();
            return;
        }
        out.value(severity.name().toLowerCase());
    }

    @Override
    public DiagnosticSeverity read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String value = in.nextString();
        if (value == null || value.isEmpty()) {
            return null;
        }
        return DiagnosticSeverity.valueOf(value.toUpperCase());
    }
}
```

- [ ] **Step 4: Delete old files in core/diagnostic/**

Delete these 4 files:
- `feature/analysis/src/main/java/com/huawei/theme/analysis/core/diagnostic/Diagnostic.java`
- `feature/analysis/src/main/java/com/huawei/theme/analysis/core/diagnostic/DiagnosticSeverity.java`
- `feature/analysis/src/main/java/com/huawei/theme/analysis/core/diagnostic/TextRange.java`
- `feature/analysis/src/main/java/com/huawei/theme/analysis/core/diagnostic/adapter/DiagnosticSeverityAdapter.java`

- [ ] **Step 5: Update all import references from old path to new path**

In `RuleConstraint.java`:
Change `import com.huawei.theme.analysis.core.diagnostic.DiagnosticSeverity;`
To     `import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;`

In `JsonRuleLoader.java` and all 5 test files (`Batch1LoadTest.java`, `Batch3LoadTest.java`, `Batch4LoadTest.java`, `Batch5LoadTest.java`, `FigureLoadTest.java`):
Search for `com.huawei.theme.analysis.core.diagnostic` and replace with `com.huawei.theme.analysis.core.shared.diagnostic`.

- [ ] **Step 6: Run all existing tests to verify migration**

Run: `./gradlew :feature:analysis:test`
Expected: ALL PASS (existing M2 tests + new Diagnostic test)

- [ ] **Step 7: Verify all tests pass** (commit deferred to Task 9)

---

### Task 4: M0 expression/ — ExpressionNode + FunctionSignatureLibrary

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/expression/ExpressionNode.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/expression/FunctionSignatureLibrary.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/expression/model/FunctionSignature.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/expression/model/FunctionParam.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/expression/ExpressionNodeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.huawei.theme.analysis.core.expression;

import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpressionNodeTest {

    @Test
    void expressionNodeImplementsExpressionAstNode() {
        ExpressionNode node = new StubExpressionNode("5", 1, 0, ExpressionKind.LITERAL);
        assertEquals("5", node.getText());
        assertEquals(1, node.getLine());
        assertEquals(0, node.getColumn());
        assertEquals(ExpressionKind.LITERAL, node.getKind());
    }

    private static class StubExpressionNode extends ExpressionNode {
        private final ExpressionKind kind;

        StubExpressionNode(String text, int line, int column, ExpressionKind kind) {
            this.kind = kind;
            this.text = text;
            this.line = line;
            this.column = column;
        }

        @Override
        public ExpressionKind getKind() {
            return kind;
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.expression.ExpressionNodeTest"`
Expected: FAIL

- [ ] **Step 3: Write implementation files**

Create `ExpressionNode.java`:
```java
package com.huawei.theme.analysis.core.expression;

import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;

import lombok.Data;

@Data
public abstract class ExpressionNode implements ExpressionAstNode {
    String text;
    int line;
    int column;

    @Override
    public String getText() {
        return text;
    }

    @Override
    public int getLine() {
        return line;
    }

    @Override
    public int getColumn() {
        return column;
    }

    @Override
    public abstract ExpressionKind getKind();
}
```

Create `FunctionSignatureLibrary.java`:
```java
package com.huawei.theme.analysis.core.expression;

import java.util.List;
import java.util.Optional;

import com.huawei.theme.analysis.core.expression.model.FunctionSignature;

public interface FunctionSignatureLibrary {
    Optional<FunctionSignature> getSignature(String name, String expressionKind);
    List<FunctionSignature> getSignatures(String name);
    boolean hasFunction(String name);
}
```

Create `FunctionSignature.java`:
```java
package com.huawei.theme.analysis.core.expression.model;

import java.util.List;

import com.huawei.theme.analysis.core.shared.type.DslType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FunctionSignature {
    String name;
    List<FunctionParam> params;
    DslType returnType;
    String expressionKind;
}
```

Create `FunctionParam.java`:
```java
package com.huawei.theme.analysis.core.expression.model;

import com.huawei.theme.analysis.core.shared.type.DslType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FunctionParam {
    String name;
    DslType type;
    boolean isVariadic;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.expression.ExpressionNodeTest"`
Expected: PASS

- [ ] **Step 5: Verify tests pass** (commit deferred to Task 9)

---

### Task 5: M0 ruledsl/ — RuleDslEvaluator + EvaluationContext

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/ruledsl/RuleDslEvaluator.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/ruledsl/EvaluationContext.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/ruledsl/EvaluationContextTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.huawei.theme.analysis.core.ruledsl;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvaluationContextTest {

    @Test
    void evaluationContextBuilder() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementName("Var")
                .elementCategory("variable")
                .elementAttrs(Map.of("name", "steps_value", "type", "number"))
                .scope(Map.of("Lockscreen", true, "Widget", false))
                .deviceSupport(Map.of("barPhone", true))
                .build();
        assertEquals("Var", ctx.getElementName());
        assertEquals("variable", ctx.getElementCategory());
        assertEquals(2, ctx.getElementAttrs().size());
        assertEquals("number", ctx.getElementAttrs().get("type"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.ruledsl.EvaluationContextTest"`
Expected: FAIL

- [ ] **Step 3: Write implementation files**

Create `RuleDslEvaluator.java`:
```java
package com.huawei.theme.analysis.core.ruledsl;

public interface RuleDslEvaluator {
    boolean evaluate(String condition, EvaluationContext context);
}
```

Create `EvaluationContext.java`:
```java
package com.huawei.theme.analysis.core.ruledsl;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EvaluationContext {
    Map<String, String> elementAttrs;
    String elementName;
    String elementCategory;
    Map<String, Boolean> scope;
    Map<String, Boolean> deviceSupport;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.ruledsl.EvaluationContextTest"`
Expected: PASS

- [ ] **Step 5: Verify tests pass** (commit deferred to Task 9)

---

### Task 6: M1 fileidentification/ — DslFileMatcher interface

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/fileidentification/DslFileMatcher.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/fileidentification/DslFileMatcherTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.huawei.theme.analysis.core.fileidentification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DslFileMatcherTest {

    @Test
    void dslFileMatcherInterfaceExists() {
        DslFileMatcher matcher = new StubMatcher();
        assertTrue(matcher.isDslFile("test.xml", "<Lockscreen>"));
    }

    private static class StubMatcher implements DslFileMatcher {
        @Override
        public boolean isDslFile(String filePath, String content) {
            return true;
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.fileidentification.DslFileMatcherTest"`
Expected: FAIL

- [ ] **Step 3: Write implementation file**

Create `DslFileMatcher.java`:
```java
package com.huawei.theme.analysis.core.fileidentification;

public interface DslFileMatcher {
    boolean isDslFile(String filePath, String content);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.fileidentification.DslFileMatcherTest"`
Expected: PASS

- [ ] **Step 5: Verify tests pass** (commit deferred to Task 9)

---

### Task 7: M3 syntaxanalysis/ — DslAstProvider interface

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/syntaxanalysis/DslAstProvider.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/syntaxanalysis/DslAstProviderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.huawei.theme.analysis.core.syntaxanalysis;

import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DslAstProviderTest {

    @Test
    void dslAstProviderInterfaceExists() {
        DslAstProvider provider = new StubProvider();
        DslFileNode ast = provider.getDslAst("test.xml", "<Lockscreen/>");
        assertNotNull(ast);
    }

    private static class StubProvider implements DslAstProvider {
        @Override
        public DslFileNode getDslAst(String filePath, String content) {
            return DslFileNode.builder().text("").line(0).column(0).build();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.syntaxanalysis.DslAstProviderTest"`
Expected: FAIL

- [ ] **Step 3: Write implementation file**

Create `DslAstProvider.java`:
```java
package com.huawei.theme.analysis.core.syntaxanalysis;

import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

public interface DslAstProvider {
    DslFileNode getDslAst(String filePath, String content);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.syntaxanalysis.DslAstProviderTest"`
Expected: PASS

- [ ] **Step 5: Verify tests pass** (commit deferred to Task 9)

---

### Task 8: M4 semanticanalysis/ — DiagnosticProvider + SymbolTable models

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/semanticanalysis/DiagnosticProvider.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/semanticanalysis/model/SymbolTable.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/semanticanalysis/model/VarDeclaration.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/semanticanalysis/model/VarReference.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/semanticanalysis/model/ReferenceKind.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/semanticanalysis/model/SymbolTableTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.huawei.theme.analysis.core.semanticanalysis.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.type.DslNumberType;
import com.huawei.theme.analysis.core.shared.type.DslStringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolTableTest {

    @Test
    void symbolTableWithDeclarationsAndReferences() {
        DslElementNode astNode = DslElementNode.builder()
                .text("<Var>")
                .line(5)
                .column(0)
                .tagName("Var")
                .attributes(List.of())
                .childElements(List.of())
                .selfClosing(false)
                .hasError(false)
                .errorMessage(null)
                .build();

        VarDeclaration decl = VarDeclaration.builder()
                .name("steps_value")
                .type(new DslNumberType())
                .expression("#steps")
                .isConstAttr(false)
                .astNode(astNode)
                .build();

        VarReference ref = VarReference.builder()
                .name("steps_value")
                .kind(ReferenceKind.NUMERIC)
                .astNode(astNode)
                .build();

        Map<String, VarDeclaration> declarations = new HashMap<>();
        declarations.put("steps_value", decl);

        SymbolTable table = SymbolTable.builder()
                .declarations(declarations)
                .references(List.of(ref))
                .build();

        assertTrue(table.getDeclarations().containsKey("steps_value"));
        assertEquals("number", table.getDeclarations().get("steps_value").getType().getName());
        assertEquals(1, table.getReferences().size());
        assertEquals(ReferenceKind.NUMERIC, table.getReferences().get(0).getKind());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTableTest"`
Expected: FAIL

- [ ] **Step 3: Write implementation files**

Create `DiagnosticProvider.java`:
```java
package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.List;

import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

public interface DiagnosticProvider {
    List<Diagnostic> analyzeFile(String filePath, String content);
}
```

Create `SymbolTable.java`:
```java
package com.huawei.theme.analysis.core.semanticanalysis.model;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SymbolTable {
    Map<String, VarDeclaration> declarations;
    List<VarReference> references;
}
```

Create `VarDeclaration.java`:
```java
package com.huawei.theme.analysis.core.semanticanalysis.model;

import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.type.DslType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VarDeclaration {
    String name;
    DslType type;
    String expression;
    boolean isConstAttr;
    DslElementNode astNode;
}
```

Create `VarReference.java`:
```java
package com.huawei.theme.analysis.core.semanticanalysis.model;

import com.huawei.theme.analysis.core.shared.ast.DslAstNode;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VarReference {
    String name;
    ReferenceKind kind;
    DslAstNode astNode;
}
```

Create `ReferenceKind.java`:
```java
package com.huawei.theme.analysis.core.semanticanalysis.model;

public enum ReferenceKind {
    NUMERIC,
    STRING
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTableTest"`
Expected: PASS

- [ ] **Step 5: Verify tests pass** (commit deferred to Task 9)

---

### Task 9: Full Integration — Verify + Single Commit on Feature Branch

**Files:**
- No new code files

- [ ] **Step 1: Create feature branch from master**

```bash
git checkout master
git checkout -b feat/core-skeleton
```

- [ ] **Step 2: Run full test suite**

Run: `./gradlew :feature:analysis:test`
Expected: ALL PASS — existing M2 tests (5 files) + new skeleton tests (7 files)

- [ ] **Step 3: Verify no com.intellij imports in core/ packages**

Run: `rg "com\.intellij" feature/analysis/src/main/java/com/huawei/theme/analysis/core/`
Expected: No matches found (Core-Plugin isolation)

- [ ] **Step 4: Single commit on feature branch**

```bash
git add -A
git commit -m "feat: core skeleton — shared/ast/type/diagnostic + M0~M4 interfaces & models

- shared/ast: DslAstNode hierarchy + ExpressionAstNode (DIP fix) + ExpressionKind
- shared/type: DslType hierarchy (Number/String/Array only)
- shared/diagnostic: migrated from core/diagnostic/ + @Builder.Default null-safety
- M0 expression: ExpressionNode + FunctionSignatureLibrary interface + models
- M0 ruledsl: RuleDslEvaluator interface + EvaluationContext model
- M1: DslFileMatcher interface
- M3: DslAstProvider interface
- M4: DiagnosticProvider interface + SymbolTable/VarDeclaration/VarReference/ReferenceKind
- M2: import path updates for DiagnosticSeverity migration"
```
