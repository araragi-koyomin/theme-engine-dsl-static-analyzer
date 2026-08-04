package com.huawei.theme.analysis.plugin.editor.reference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import com.huawei.theme.analysis.core.semanticanalysis.model.VarDeclaration;
import com.huawei.theme.analysis.core.shared.type.DslArrayType;
import com.huawei.theme.analysis.core.shared.type.DslStringType;
import com.huawei.theme.analysis.core.shared.type.DslType;
import com.huawei.theme.analysis.plugin.ast.DslAstService;
import com.huawei.theme.analysis.plugin.editor.varname.VarNameElement;

public final class VarNameResolver {

    private VarNameResolver() {
    }

    @Nullable
    public static PsiElement resolveDeclaration(@NotNull Project project,
                                                @NotNull XmlFile hostFile,
                                                @Nullable XmlTag hostTag,
                                                @NotNull String varName) {
        return resolveDeclarationsMulti(project, hostFile, hostTag, varName).stream()
                .findFirst().orElse(null);
    }

    /**
     * Multi-resolve a (possibly macro-interpolated) reference to ALL its declaration targets.
     *
     * <p>A single source reference inside a {@code <For>} body, e.g. {@code #x_%{i}}, expands to
     * one demacoed copy per iteration. Each copy is interpolated with its own compile-time scope
     * ({@code {i:1}} → {@code x_1}, {@code {i:2}} → {@code x_2}), and each may resolve to a
     * different declaration. We collect the distinct targets (deduped by demacoed decl-node
     * identity) so that jump-to-def offers all of them and find-usages from any one declaration
     * still finds this reference.</p>
     */
    @NotNull
    public static List<PsiElement> resolveDeclarationsMulti(@NotNull Project project,
                                                             @NotNull XmlFile hostFile,
                                                             @Nullable XmlTag hostTag,
                                                             @NotNull String varName) {
        DslAstService svc = DslAstService.getInstance(project);
        // Dedup by the FINAL PSI target (the VarNameElement/XmlAttributeValue at the original source),
        // not by the intermediate demacoed decl node: N macro-expanded copies of one <Var> all two-hop
        // to the same original source, so they must collapse to a single resolve target. Distinct
        // source declarations (e.g. manually-defined x_1 and x_2) remain separate entries.
        Set<PsiElement> seenTargets = new LinkedHashSet<>();
        List<PsiElement> result = new ArrayList<>();
        for (DslAstService.ContextTarget contextTarget : svc.demacroedTargetsWithContext(hostFile, hostTag)) {
            String resolvedName = svc.interpolateName(contextTarget, varName);
            Optional<DslAstService.ContextDeclaration> declOpt =
                    svc.lookupDeclaration(contextTarget, resolvedName);
            if (declOpt.isEmpty()) {
                continue;
            }
            DslAstService.ContextDeclaration d = declOpt.get();
            if (d.isGlobal()) {
                continue;
            }
            Optional<XmlAttributeValue> nameValue = svc.getDeclarationValue(contextTarget, d);
            if (nameValue.isEmpty()) {
                continue;
            }
            VarNameElement vne = findVarNameElement(project, nameValue.get());
            PsiElement target = vne != null ? vne : nameValue.get();
            if (seenTargets.add(target)) {
                result.add(target);
            }
        }
        return result;
    }

    public static Optional<ContextualDeclaration> lookupContextualDeclaration(
            @NotNull Project project,
            @NotNull XmlFile hostFile,
            @Nullable XmlTag hostTag,
            @NotNull String varName) {
        DslAstService svc = DslAstService.getInstance(project);
        List<DslAstService.ContextTarget> targets = svc.demacroedTargetsWithContext(hostFile, hostTag);
        if (targets.isEmpty()) {
            return Optional.empty();
        }
        Set<String> allOccurrences = occurrenceKeys(svc, targets);
        DeclarationAccumulator accumulator = new DeclarationAccumulator();
        for (DslAstService.ContextTarget contextTarget : targets) {
            String resolvedName = svc.interpolateName(contextTarget, varName);
            svc.lookupDeclaration(contextTarget, resolvedName).ifPresent(declaration ->
                    accumulator.add(declaration, svc.getOccurrenceKey(contextTarget)));
        }
        return accumulator.isEmpty() ? Optional.empty()
                : Optional.of(accumulator.build(allOccurrences.size()));
    }

    public static List<VarDeclaration> visibleDeclarations(@NotNull Project project,
                                                            @NotNull XmlFile hostFile,
                                                            @Nullable XmlTag hostTag) {
        return visibleContextualDeclarations(project, hostFile, hostTag).stream()
                .map(contextual -> {
                    DslAstService.ContextDeclaration declaration = contextual.getDeclaration();
                    return VarDeclaration.builder()
                            .name(declaration.getName())
                            .type(declaration.getType())
                            .isGlobal(declaration.isGlobal())
                            .hostAttrName(declaration.getHostAttrName())
                            .build();
                })
                .toList();
    }

    public static List<ContextualDeclaration> visibleContextualDeclarations(@NotNull Project project,
                                                                              @NotNull XmlFile hostFile,
                                                                              @Nullable XmlTag hostTag) {
        DslAstService svc = DslAstService.getInstance(project);
        List<DslAstService.ContextTarget> targets = svc.demacroedTargetsWithContext(hostFile, hostTag);
        Set<String> allOccurrences = occurrenceKeys(svc, targets);
        Map<String, DeclarationAccumulator> declarations = new LinkedHashMap<>();
        for (DslAstService.ContextTarget target : targets) {
            String occurrence = svc.getOccurrenceKey(target);
            for (DslAstService.ContextDeclaration declaration : svc.visibleDeclarations(target)) {
                declarations.computeIfAbsent(declaration.getName(), ignored -> new DeclarationAccumulator())
                        .add(declaration, occurrence);
            }
        }
        return declarations.values().stream()
                .map(accumulator -> accumulator.build(allOccurrences.size()))
                .toList();
    }

    @NotNull
    private static Set<String> occurrenceKeys(@NotNull DslAstService service,
                                               @NotNull List<DslAstService.ContextTarget> targets) {
        Set<String> result = new LinkedHashSet<>();
        for (DslAstService.ContextTarget target : targets) {
            result.add(service.getOccurrenceKey(target));
        }
        return result;
    }

    @NotNull
    public static String contextualTypeText(@NotNull ContextualDeclaration contextual,
                                             @NotNull String baseType) {
        List<String> details = new ArrayList<>();
        if (contextual.hasConflictingTypes()) {
            details.add("conflicting types");
        }
        if (contextual.getAvailableContexts() < contextual.getTotalContexts()) {
            details.add("available " + contextual.getAvailableContexts() + "/"
                    + contextual.getTotalContexts() + " contexts");
        }
        return details.isEmpty() ? baseType : baseType + " · " + String.join(" · ", details);
    }

    public static String sigilOf(@Nullable DslType type) {
        return type instanceof DslStringType ? "@" : "#";
    }

    public static String typeName(@Nullable DslType type) {
        if (type == null) {
            return "";
        }
        if (type instanceof DslArrayType arr) {
            String base = arr.getBaseType();
            return (base != null ? base : "number") + "[]";
        }
        return type.getName();
    }

    @Nullable
    public static VarNameElement findVarNameElement(@NotNull Project project,
                                                    @NotNull XmlAttributeValue nameValue) {
        List<Pair<PsiElement, TextRange>> injected =
                InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(nameValue);
        if (injected == null) {
            return null;
        }
        for (Pair<PsiElement, TextRange> entry : injected) {
            PsiElement e = entry.getFirst();
            VarNameElement found = e instanceof VarNameElement vne ? vne
                    : PsiTreeUtil.getChildOfType(e, VarNameElement.class);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public static final class ElementResolveResult implements ResolveResult {
        private final PsiElement element;

        public ElementResolveResult(@Nullable PsiElement element) {
            this.element = element;
        }

        @Override
        public @Nullable PsiElement getElement() {
            return element;
        }

        @Override
        public boolean isValidResult() {
            return element != null && element.isValid();
        }
    }

    public static final class ContextualDeclaration {
        private final DslAstService.ContextDeclaration declaration;
        private final int availableContexts;
        private final int totalContexts;
        private final boolean conflictingTypes;

        private ContextualDeclaration(@NotNull DslAstService.ContextDeclaration declaration,
                                      int availableContexts,
                                      int totalContexts, boolean conflictingTypes) {
            this.declaration = declaration;
            this.availableContexts = availableContexts;
            this.totalContexts = totalContexts;
            this.conflictingTypes = conflictingTypes;
        }

        @NotNull
        public DslAstService.ContextDeclaration getDeclaration() {
            return declaration;
        }

        public int getAvailableContexts() {
            return availableContexts;
        }

        public int getTotalContexts() {
            return totalContexts;
        }

        public boolean hasConflictingTypes() {
            return conflictingTypes;
        }
    }

    private static final class DeclarationAccumulator {
        private final List<DslAstService.ContextDeclaration> declarations = new ArrayList<>();
        private final Set<String> occurrences = new LinkedHashSet<>();

        private void add(@NotNull DslAstService.ContextDeclaration declaration,
                         @NotNull String occurrence) {
            declarations.add(declaration);
            occurrences.add(occurrence);
        }

        private boolean isEmpty() {
            return declarations.isEmpty();
        }

        @NotNull
        private ContextualDeclaration build(int totalContexts) {
            Set<String> types = new LinkedHashSet<>();
            for (DslAstService.ContextDeclaration declaration : declarations) {
                types.add(typeName(declaration.getType()));
            }
            return new ContextualDeclaration(declarations.get(0), occurrences.size(), totalContexts,
                    types.size() > 1);
        }
    }
}
