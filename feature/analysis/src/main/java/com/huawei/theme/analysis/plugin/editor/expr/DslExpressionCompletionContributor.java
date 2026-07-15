package com.huawei.theme.analysis.plugin.editor.expr;

import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary;
import com.huawei.theme.analysis.core.expression.model.FunctionSignature;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;

/**
 * Completion contributor for the DslExpression language (injected into ThemeDSL
 * attribute values). Provides four candidate types at any position in the expression:
 * <ol>
 *   <li>Global variables (from the rule library)</li>
 *   <li>User {@code <Var>} declarations (from the host XML file)</li>
 *   <li>Local {@code indexFlag} variables (from enclosing {@code <Array>}/{@code <CycleCommand>})</li>
 *   <li>Global functions (from {@code dsl_functions.json})</li>
 * </ol>
 *
 * <p>The platform filters candidates by the prefix the user has typed. Variables
 * are shown with a variable icon; functions with a function icon and signature
 * info in the tail text.</p>
 */
public class DslExpressionCompletionContributor extends CompletionContributor {

    @Override
    public boolean invokeAutoPopup(@NotNull PsiElement position, char typeChar) {
        return typeChar == '#' || typeChar == '@';
    }

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                      @NotNull CompletionResultSet result) {
        // Compute a prefix that includes the #/@ sigil so the platform replaces it
        // when a candidate is selected (avoids "##x": user types "#", selects "#x",
        // platform should replace "#" with "#x", not append "#x" after "#").
        String prefix = computePrefix(parameters);
        CompletionResultSet prefixedResult = result.withPrefixMatcher(prefix);

        PsiElement position = parameters.getPosition();
        Project project = position.getProject();
        if (project == null) {
            return;
        }

        // Find the host XML file via the injection host.
        PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(project).getInjectionHost(position);
        XmlFile hostFile = null;
        XmlAttributeValue hostValue = null;
        if (host instanceof XmlAttributeValue value) {
            hostValue = value;
            hostFile = value.getContainingFile() instanceof XmlFile xmlFile ? xmlFile : null;
        }

        // 1. Global variables
        RuleRepository repo = RuleRepositoryService.getInstance().getRuleRepository();
        for (DslGlobalVar gv : repo.getAllGlobalVars()) {
            String pfx = prefixForType(gv.getType());
            prefixedResult.addElement(LookupElementBuilder.create(pfx + gv.getName())
                    .withIcon(AllIcons.Nodes.Variable)
                    .withTypeText(gv.getType()));
        }

        // 2. User <Var> declarations
        if (hostFile != null) {
            for (XmlTag tag : PsiTreeUtil.findChildrenOfType(hostFile, XmlTag.class)) {
                if (!"Var".equals(tag.getName())) {
                    continue;
                }
                String name = tag.getAttributeValue("name");
                if (name != null && !name.isEmpty()) {
                    String type = tag.getAttributeValue("type");
                    if (type == null || type.isEmpty()) {
                        type = "number";
                    }
                    String pfx = prefixForType(type);
                    prefixedResult.addElement(LookupElementBuilder.create(pfx + name)
                            .withIcon(AllIcons.Nodes.Variable)
                            .withTypeText(type));
                }
            }
        }

        // 3. Local indexFlag variables from enclosing <Array>/<CycleCommand>
        if (hostValue != null) {
            XmlAttribute attr = PsiTreeUtil.getParentOfType(hostValue, XmlAttribute.class);
            if (attr != null) {
                XmlTag enclosingTag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
                for (XmlTag t = enclosingTag; t != null; t = PsiTreeUtil.getParentOfType(t, XmlTag.class)) {
                    String indexFlag = t.getAttributeValue("indexFlag");
                    if (indexFlag != null && !indexFlag.isEmpty()) {
                        prefixedResult.addElement(LookupElementBuilder.create("#" + indexFlag)
                                .withIcon(AllIcons.Nodes.Variable)
                                .withTypeText("number"));
                    }
                }
            }
        }

        // 4. Global functions
        FunctionSignatureLibrary lib = repo.getFunctionSignatureLibrary();
        if (lib != null) {
            for (FunctionSignature sig : lib.getAllSignatures()) {
                String params = sig.getParams().stream()
                        .map(p -> p.getName() + ": " + (p.getType() != null ? p.getType().getName() : "?"))
                        .collect(Collectors.joining(", "));
                String returnType = sig.getReturnType() != null ? sig.getReturnType().getName() : "";
                prefixedResult.addElement(LookupElementBuilder.create(sig.getName())
                        .withIcon(AllIcons.Nodes.Function)
                        .withTailText("(" + params + ")", true)
                        .withTypeText(returnType));
            }
        }
    }

    /**
     * Computes the completion prefix including the {@code #}/{@code @} sigil and
     * any identifier characters typed after it, so the platform replaces the full
     * prefix (sigil + name) when a candidate is selected.
     */
    private static String computePrefix(CompletionParameters parameters) {
        int offset = parameters.getOffset();
        CharSequence text = parameters.getEditor().getDocument().getCharsSequence();
        int start = offset;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                start--;
            } else if (c == '#' || c == '@') {
                start--;
                break;
            } else {
                break;
            }
        }
        return text.subSequence(start, offset).toString();
    }

    /**
     * Returns the variable access sigil for a given type: {@code #} for numeric
     * types (number, number[]), {@code @} for string types (string, string[]).
     * Defaults to {@code #} (number) when type is null/empty/unknown.
     */
    private static String prefixForType(String type) {
        if (type == null || type.isEmpty() || type.startsWith("number")) {
            return "#";
        }
        if (type.startsWith("string")) {
            return "@";
        }
        return "#";
    }
}
