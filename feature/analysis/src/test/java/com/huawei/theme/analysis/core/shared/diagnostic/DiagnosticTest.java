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
