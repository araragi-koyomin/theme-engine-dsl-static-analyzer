package com.huawei.theme.analysis.plugin.editor.expr;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import org.antlr.intellij.adaptor.lexer.PSIElementTypeFactory;
import org.antlr.intellij.adaptor.lexer.RuleIElementType;
import org.antlr.intellij.adaptor.psi.ANTLRPsiNode;

import com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionLexer;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionParser;
import com.huawei.theme.analysis.core.expression.model.FunctionSignature;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.plugin.ast.DslAstService;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;
import com.huawei.theme.analysis.plugin.editor.reference.VarNameResolver;

/**
 * Completion contributor for DE expressions running on the host ThemeDSL
 * language (not on injected DE PSI). Fires when the cursor is inside the
 * value of an expression-supporting attribute, and provides:
 * <ol>
 *   <li>Global variables (from the rule library)</li>
 *   <li>User {@code <Var>} declarations (from the host XML file)</li>
 *   <li>Local {@code indexFlag} variables (from enclosing tags)</li>
 *   <li>Global functions (from {@code dsl_functions.json})</li>
 * </ol>
 *
 * <p>Registered via {@code <completion.contributor language="ThemeDSL">} so it
 * runs on the host XML PSI, avoiding the performance cost of DE language
 * injection. Dummy DE PSI elements (created via {@link PsiFileFactory}) are
 * linked to each candidate so the {@link DslExpressionDocumentationProvider}
 * can render docs in the completion popup on hover.</p>
 */
public class DslExpressionCompletionContributor extends CompletionContributor {

    private static final RuleIElementType FUNCTION_CALL;
    private static final RuleIElementType HASH_VAR_REF;
    private static final RuleIElementType AT_VAR_REF;

    static {
        PSIElementTypeFactory.defineLanguageIElementTypes(
                DslExpressionLanguage.INSTANCE,
                DslExpressionLexer.VOCABULARY,
                DslExpressionParser.ruleNames);
        List<RuleIElementType> ruleTypes =
                PSIElementTypeFactory.getRuleIElementTypes(DslExpressionLanguage.INSTANCE);
        FUNCTION_CALL = ruleTypes.get(DslExpressionParser.RULE_functionCall);
        HASH_VAR_REF = ruleTypes.get(DslExpressionParser.RULE_hashVarRef);
        AT_VAR_REF = ruleTypes.get(DslExpressionParser.RULE_atVarRef);
    }

    private static final Map<String, PsiElement> DOC_FUNC_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, PsiElement> DOC_VAR_CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean invokeAutoPopup(@NotNull PsiElement position, char typeChar) {
        return typeChar == '#' || typeChar == '@';
    }

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                      @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();

        // Only fire inside attribute values
        XmlAttributeValue attrValue = PsiTreeUtil.getParentOfType(position, XmlAttributeValue.class);
        if (attrValue == null) {
            return;
        }
        XmlAttribute attr = PsiTreeUtil.getParentOfType(attrValue, XmlAttribute.class);
        if (attr == null) {
            return;
        }
        XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
        if (tag == null) {
            return;
        }

        // Only fire for expression-supporting attributes
        RuleRepository repo = RuleRepositoryService.getInstance().getRuleRepository();
        Optional<DslElementRule> ruleOpt = repo.getElementRule(tag.getName());
        if (ruleOpt.isEmpty()) {
            return;
        }
        AttrTypeSpec spec = findAttrSpec(ruleOpt.get(), attr.getName());
        if (spec == null || !spec.isSupportsExpression()) {
            return;
        }

        Project project = position.getProject();
        if (project == null) {
            return;
        }

        String prefix = computePrefix(parameters);
        CompletionResultSet prefixedResult = result.withPrefixMatcher(prefix);

        // 1-3. Visible variables (preset globals + user <Var> + in-scope indexFlag locals)
        // discovered via the AST SymbolTable at the cursor's enclosing element.
        XmlFile hostFile = tag.getContainingFile() instanceof XmlFile xmlFile ? xmlFile : null;
        if (hostFile != null) {
            for (VarNameResolver.ContextualDeclaration contextual
                    : VarNameResolver.visibleContextualDeclarations(project, hostFile, tag)) {
                DslAstService.ContextDeclaration d = contextual.getDeclaration();
                String pfx = VarNameResolver.sigilOf(d.getType());
                String lookupText = pfx + d.getName();
                String typeText = VarNameResolver.contextualTypeText(
                        contextual, VarNameResolver.typeName(d.getType()));
                PsiElement dummyVar = getDocVarRef(project, lookupText);
                LookupElementBuilder builder = dummyVar != null
                        ? LookupElementBuilder.create(dummyVar, lookupText)
                        : LookupElementBuilder.create(lookupText);
                prefixedResult.addElement(builder
                        .withIcon(AllIcons.Nodes.Variable)
                        .withTypeText(typeText));
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
                PsiElement dummyFunc = getDocFunctionCall(project, sig.getName());
                LookupElementBuilder builder = dummyFunc != null
                        ? LookupElementBuilder.create(dummyFunc, sig.getName())
                        : LookupElementBuilder.create(sig.getName());
                prefixedResult.addElement(builder
                        .withIcon(AllIcons.Nodes.Function)
                        .withTailText("(" + params + ")", true)
                        .withTypeText(returnType));
            }
        }

        // Suppress default XML completion
        result.stopHere();
    }

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

    private static AttrTypeSpec findAttrSpec(DslElementRule rule, String attrName) {
        AttrTypeSpec spec = rule.getAttrTypes().get(attrName);
        if (spec != null) {
            return spec;
        }
        for (AttrTypeSpec candidate : rule.getAttrTypes().values()) {
            if (candidate.getAliases() != null && candidate.getAliases().contains(attrName)) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    private static PsiElement getDocFunctionCall(@NotNull Project project, @NotNull String funcName) {
        PsiElement cached = DOC_FUNC_CACHE.get(funcName);
        if (cached != null && cached.isValid()) {
            return cached;
        }
        try {
            PsiFile file = PsiFileFactory.getInstance(project)
                    .createFileFromText(DslExpressionLanguage.INSTANCE, funcName + "()");
            Collection<ANTLRPsiNode> nodes = PsiTreeUtil.findChildrenOfType(file, ANTLRPsiNode.class);
            for (ANTLRPsiNode node : nodes) {
                if (node.getNode().getElementType() == FUNCTION_CALL) {
                    DOC_FUNC_CACHE.put(funcName, node);
                    return node;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private static PsiElement getDocVarRef(@NotNull Project project, @NotNull String refText) {
        PsiElement cached = DOC_VAR_CACHE.get(refText);
        if (cached != null && cached.isValid()) {
            return cached;
        }
        try {
            PsiFile file = PsiFileFactory.getInstance(project)
                    .createFileFromText(DslExpressionLanguage.INSTANCE, refText);
            Collection<ANTLRPsiNode> nodes = PsiTreeUtil.findChildrenOfType(file, ANTLRPsiNode.class);
            for (ANTLRPsiNode node : nodes) {
                IElementType t = node.getNode().getElementType();
                if (t == HASH_VAR_REF || t == AT_VAR_REF) {
                    DOC_VAR_CACHE.put(refText, node);
                    return node;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
