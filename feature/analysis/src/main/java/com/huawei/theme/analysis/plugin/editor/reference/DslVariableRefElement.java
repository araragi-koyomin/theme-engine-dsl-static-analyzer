package com.huawei.theme.analysis.plugin.editor.reference;

import java.util.ArrayList;
import java.util.List;

import com.huawei.theme.analysis.plugin.editor.varname.VarNameElement;
import com.huawei.theme.analysis.plugin.editor.expr.DslExpressionLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;

import org.antlr.intellij.adaptor.lexer.PSIElementTypeFactory;
import org.antlr.intellij.adaptor.lexer.RuleIElementType;
import org.antlr.intellij.adaptor.psi.ANTLRPsiNode;

import com.huawei.theme.analysis.core.expression.generated.DslExpressionLexer;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionParser;
import com.huawei.theme.analysis.plugin.ast.DslAstService;

/**
 * The {@code atVarRef}/{@code hashVarRef} PSI node (in an injected DE fragment),
 * which IS a {@link PsiReference} to the declaring {@code <Var name="...">} tag (or
 * {@code <Array indexFlag="...">} local) in the host ThemeDSL XML file.
 *
 * <p>Resolution goes through the AST {@link com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable}
 * via {@link VarNameResolver} (scope of the host element → lookup → PSI↔AST map →
 * injected {@link VarNameElement}). {@link #handleElementRename(String)} remains a
 * direct PSI edit on the host attribute value — it is the rename write path and is
 * intentionally not routed through the (read-only) AST.</p>
 */
public class DslVariableRefElement extends ANTLRPsiNode implements PsiPolyVariantReference {

    private static final RuleIElementType VAR_NAME_RULE;

    static {
        PSIElementTypeFactory.defineLanguageIElementTypes(
                DslExpressionLanguage.INSTANCE,
                DslExpressionLexer.VOCABULARY,
                DslExpressionParser.ruleNames);
        VAR_NAME_RULE = PSIElementTypeFactory.getRuleIElementTypes(DslExpressionLanguage.INSTANCE)
                .get(DslExpressionParser.RULE_varName);
    }

    public DslVariableRefElement(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull PsiElement getElement() {
        return this;
    }

    @Override
    public @NotNull TextRange getRangeInElement() {
        PsiElement varName = findVarNameChild();
        if (varName == null) {
            return TextRange.EMPTY_RANGE;
        }
        int[] bounds = trimmedBounds(varName.getText());
        int startInVarName = bounds[0];
        int length = bounds[1] - bounds[0];
        return TextRange.from(varName.getStartOffsetInParent() + startInVarName, length);
    }

    @Override
    public @Nullable PsiElement resolve() {
        ResolveResult[] results = multiResolve(false);
        return results.length == 0 ? null : results[0].getElement();
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        XmlFile hostFile = getHostXmlFile();
        if (hostFile == null) {
            return ResolveResult.EMPTY_ARRAY;
        }
        Project project = getProject();
        if (project == null) {
            return ResolveResult.EMPTY_ARRAY;
        }
        String name = getVariableName();
        if (name == null) {
            return ResolveResult.EMPTY_ARRAY;
        }
        List<PsiElement> targets = VarNameResolver.resolveDeclarationsMulti(project, hostFile, hostTagOf(), name);
        return targets.stream().map(VarNameResolver.ElementResolveResult::new).toArray(ResolveResult[]::new);
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
        for (ResolveResult r : multiResolve(false)) {
            PsiElement e = r.getElement();
            if (e != null && element.getManager().areElementsEquivalent(e, element)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @NotNull String getCanonicalText() {
        String name = getVariableName();
        return name == null ? "" : name;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        Project project = getProject();
        if (project == null) {
            return this;
        }
        PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(project).getInjectionHost(this);
        if (!(host instanceof XmlAttributeValue hostValue)) {
            return this;
        }
        PsiElement varName = findVarNameChild();
        if (varName == null) {
            return this;
        }
        int[] bounds = trimmedBounds(varName.getText());
        // DE expressions contain no XML escaping, so decoded (injected) offsets map 1:1 to value-text offsets.
        int valueStartInElement =
                hostValue.getValueTextRange().getStartOffset() - hostValue.getTextRange().getStartOffset();
        int injStart = varName.getTextRange().getStartOffset() + bounds[0];
        int injEnd = injStart + (bounds[1] - bounds[0]);
        int hostStart = valueStartInElement + injStart;
        int hostEnd = valueStartInElement + injEnd;
        TextRange rangeInHost = TextRange.create(hostStart, hostEnd);
        return ElementManipulators.getManipulator(hostValue).handleContentChange(hostValue, rangeInHost, newElementName);
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        throw new IncorrectOperationException("Not supported");
    }

    @Override
    public @NotNull Object @NotNull [] getVariants() {
        XmlFile hostFile = getHostXmlFile();
        if (hostFile == null) {
            return new Object[0];
        }
        Project project = getProject();
        if (project == null) {
            return new Object[0];
        }
        List<LookupElement> variants = new ArrayList<>();
        for (VarNameResolver.ContextualDeclaration contextual
                : VarNameResolver.visibleContextualDeclarations(project, hostFile, hostTagOf())) {
            DslAstService.ContextDeclaration d = contextual.getDeclaration();
            String baseType = d.isGlobal() ? VarNameResolver.typeName(d.getType()) : "Var";
            String typeText = VarNameResolver.contextualTypeText(contextual, baseType);
            variants.add(LookupElementBuilder.create(d.getName())
                    .withIcon(AllIcons.Nodes.Variable)
                    .withTypeText(typeText));
        }
        return variants.toArray();
    }

    @Override
    public boolean isSoft() {
        return true;
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        return new PsiReference[]{this};
    }

    @Nullable
    private PsiElement findVarNameChild() {
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null && child.getNode().getElementType() == VAR_NAME_RULE) {
                return child;
            }
        }
        return null;
    }

    @Nullable
    private String getVariableName() {
        PsiElement varName = findVarNameChild();
        if (varName == null) {
            return null;
        }
        String text = varName.getText();
        if (text == null) {
            return null;
        }
        return text.trim();
    }

    @Nullable
    private XmlFile getHostXmlFile() {
        Project project = getProject();
        if (project == null) {
            return null;
        }
        PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(project).getInjectionHost(this);
        if (host == null) {
            return null;
        }
        return host.getContainingFile() instanceof XmlFile xmlFile ? xmlFile : null;
    }

    @Nullable
    private XmlTag hostTagOf() {
        Project project = getProject();
        if (project == null) {
            return null;
        }
        PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(project).getInjectionHost(this);
        if (host instanceof XmlAttributeValue hostValue) {
            return PsiTreeUtil.getParentOfType(hostValue, XmlTag.class);
        }
        return null;
    }

    /** Returns [leadingNonWsStart, trailingNonWsEnd) bounds within the given (possibly whitespace-padded) text. */
    private static int[] trimmedBounds(String text) {
        if (text == null) {
            return new int[]{0, 0};
        }
        int leading = 0;
        while (leading < text.length() && Character.isWhitespace(text.charAt(leading))) {
            leading++;
        }
        int end = text.length();
        while (end > leading && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return new int[]{leading, end};
    }
}
