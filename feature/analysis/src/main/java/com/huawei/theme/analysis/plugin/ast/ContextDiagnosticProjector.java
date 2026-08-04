package com.huawei.theme.analysis.plugin.ast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.psi.xml.XmlFile;

import com.huawei.theme.analysis.core.macro.DemacroedAst;
import com.huawei.theme.analysis.core.macro.DiagnosticDedup;
import com.huawei.theme.analysis.core.macro.IncludeInstance;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

final class ContextDiagnosticProjector {

    static final String RULE_NO_CONTEXT_ROOT = "MACRO-008";

    private ContextDiagnosticProjector() {
    }

    @NotNull
    static List<Diagnostic> project(@NotNull XmlFile file,
                                    @NotNull List<DslAnalysisContext> contexts) {
        if (!isFunctionFile(file.getName())) {
            return contexts.isEmpty() ? List.of()
                    : DiagnosticDedup.dedup(contexts.get(0).getDiagnostics()).stream()
                            .map(ContextDiagnosticProjector::detachedCopy)
                            .toList();
        }
        String requestedPath = normalizePath(pathOf(file));
        int totalInstances = contexts.stream()
                .mapToInt(context -> context.getIncludeInstances(requestedPath).size())
                .sum();
        if (totalInstances == 0) {
            return List.of(noContextDiagnostic(requestedPath));
        }

        Map<DiagnosticKey, ProjectedDiagnostic> grouped = new LinkedHashMap<>();
        for (DslAnalysisContext context : contexts) {
            collectContextDiagnostics(context, requestedPath, grouped);
        }
        List<Diagnostic> result = new ArrayList<>();
        for (ProjectedDiagnostic aggregate : grouped.values()) {
            result.add(aggregate.toDiagnostic(requestedPath, totalInstances));
        }
        return List.copyOf(result);
    }

    private static void collectContextDiagnostics(@NotNull DslAnalysisContext context,
                                                  @NotNull String requestedPath,
                                                  @NotNull Map<DiagnosticKey, ProjectedDiagnostic> grouped) {
        int ordinal = 0;
        for (Diagnostic diagnostic : context.getDiagnostics()) {
            Projection projection = projectToFile(context, diagnostic, requestedPath);
            if (projection == null) {
                continue;
            }
            DiagnosticKey key = DiagnosticKey.of(diagnostic, projection.normalNode);
            ProjectedDiagnostic aggregate = grouped.computeIfAbsent(key,
                    ignored -> new ProjectedDiagnostic(diagnostic, projection.normalNode));
            String occurrence = context.getRootFilePath() + "#"
                    + (projection.includeInstance != null
                    ? projection.includeInstance.getId() : "unscoped-" + ordinal);
            aggregate.occurrences.add(occurrence);
            ordinal++;
        }
    }

    @Nullable
    private static Projection projectToFile(@NotNull DslAnalysisContext context,
                                            @NotNull Diagnostic diagnostic,
                                            @NotNull String requestedPath) {
        DslElementNode owner = owningElement(diagnostic.getAstNode());
        if (owner == null) {
            return projectPositionOnlyDiagnostic(context, diagnostic, requestedPath);
        }
        DemacroedAst demacroed = context.getDemacroed();
        DslElementNode normal = demacroed.getNormalNode(owner).orElse(owner);
        if (!normalizePath(demacroed.getFilePathOfNormalNode(normal)).equals(requestedPath)) {
            return null;
        }
        return new Projection(normal, demacroed.getIncludeInstance(owner).orElse(null));
    }

    @Nullable
    private static Projection projectPositionOnlyDiagnostic(@NotNull DslAnalysisContext context,
                                                              @NotNull Diagnostic diagnostic,
                                                              @NotNull String requestedPath) {
        DemacroedAst demacroed = context.getDemacroed();
        for (IncludeInstance instance : context.getIncludeInstances(requestedPath)) {
            for (DslElementNode generated : instance.getGeneratedNodes()) {
                if (!contains(generated, diagnostic.getLine(), diagnostic.getColumn())) {
                    continue;
                }
                Optional<DslElementNode> normal = demacroed.getNormalNode(generated);
                if (normal.isPresent()
                        && normalizePath(demacroed.getFilePathOfNormalNode(normal.get())).equals(requestedPath)) {
                    return new Projection(normal.get(), instance);
                }
            }
        }
        return null;
    }

    @Nullable
    private static DslElementNode owningElement(@Nullable DslAstNode node) {
        DslAstNode current = node;
        while (current != null) {
            if (current instanceof DslElementNode element) {
                return element;
            }
            if (current instanceof DslAttributeNode attribute) {
                current = attribute.getParent();
            } else {
                return null;
            }
        }
        return null;
    }

    private static boolean contains(@NotNull DslAstNode node, int line, int column) {
        if (line < node.getLine() || line > node.getEndLine()) {
            return false;
        }
        if (line == node.getLine() && column < node.getColumn()) {
            return false;
        }
        return line != node.getEndLine() || column <= node.getEndColumn();
    }

    private static Diagnostic noContextDiagnostic(@NotNull String path) {
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.WARNING)
                .ruleId(RULE_NO_CONTEXT_ROOT)
                .message("Cannot find context root")
                .filePath(path)
                .line(1)
                .column(0)
                .endLine(1)
                .endColumn(1)
                .build();
    }

    @NotNull
    private static Diagnostic detachedCopy(@NotNull Diagnostic source) {
        return Diagnostic.builder()
                .severity(source.getSeverity())
                .ruleId(source.getRuleId())
                .message(source.getMessage())
                .filePath(source.getFilePath())
                .line(source.getLine())
                .column(source.getColumn())
                .endLine(source.getEndLine())
                .endColumn(source.getEndColumn())
                .suggestedFixes(source.getSuggestedFixes() == null
                        ? List.of() : List.copyOf(source.getSuggestedFixes()))
                .ruleDocUrl(source.getRuleDocUrl())
                .build();
    }

    private static boolean isFunctionFile(@NotNull String name) {
        return name.startsWith("function_") && name.endsWith(".xml");
    }

    @NotNull
    private static String pathOf(@NotNull XmlFile file) {
        return file.getVirtualFile() != null ? file.getVirtualFile().getPath() : file.getName();
    }

    @NotNull
    private static String normalizePath(@NotNull String path) {
        return path.replace('\\', '/');
    }

    private record Projection(DslElementNode normalNode, IncludeInstance includeInstance) {
    }

    private record DiagnosticKey(String ruleId, String message, DiagnosticSeverity severity,
                                 int line, int column, int endLine, int endColumn) {

        static DiagnosticKey of(@NotNull Diagnostic diagnostic, @NotNull DslElementNode node) {
            return new DiagnosticKey(diagnostic.getRuleId(), diagnostic.getMessage(), diagnostic.getSeverity(),
                    node.getLine(), node.getColumn(), node.getEndLine(), node.getEndColumn());
        }
    }

    private static final class ProjectedDiagnostic {
        final Diagnostic source;
        final DslElementNode normalNode;
        final Set<String> occurrences = new LinkedHashSet<>();

        ProjectedDiagnostic(@NotNull Diagnostic source, @NotNull DslElementNode normalNode) {
            this.source = source;
            this.normalNode = normalNode;
        }

        Diagnostic toDiagnostic(@NotNull String path, int totalInstances) {
            int affected = Math.min(occurrences.size(), totalInstances);
            DiagnosticSeverity severity = affected < totalInstances && source.getSeverity() == DiagnosticSeverity.ERROR
                    ? DiagnosticSeverity.WARNING : source.getSeverity();
            return Diagnostic.builder()
                    .severity(severity)
                    .ruleId(source.getRuleId())
                    .message(source.getMessage() + " (" + affected + "/" + totalInstances + " include contexts)")
                    .filePath(path)
                    .line(normalNode.getLine())
                    .column(normalNode.getColumn())
                    .endLine(normalNode.getEndLine())
                    .endColumn(normalNode.getEndColumn())
                    .suggestedFixes(source.getSuggestedFixes())
                    .ruleDocUrl(source.getRuleDocUrl())
                    .build();
        }
    }
}
