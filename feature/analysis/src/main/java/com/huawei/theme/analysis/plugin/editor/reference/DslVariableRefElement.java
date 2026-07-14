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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;

import org.antlr.intellij.adaptor.lexer.PSIElementTypeFactory;
import org.antlr.intellij.adaptor.lexer.RuleIElementType;
import org.antlr.intellij.adaptor.psi.ANTLRPsiNode;

import com.huawei.theme.analysis.core.expression.generated.DslExpressionLexer;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionParser;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;

/**
 * The {@code atVarRef}/{@code hashVarRef} PSI node (in an injected DE fragment),
 * which IS a {@link PsiReference} to the declaring {@code <Var name="...">} tag in
 * the host ThemeDSL XML file.
 *
 * <p>Because DE is injected into XML attribute values, host-side reference
 * contributors are bypassed (the {@code @x}/{@code #x} text belongs to the
 * injected DE PSI). So the reference must live on the DE PSI itself and reach
 * back to the host via {@link InjectedLanguageManager#getInjectionHost}.</p>
 *
 * <p><b>Whitespace note.</b> The DE lexer skips {@code WS}, and the PSI builder
 * glues trailing whitespace onto the preceding token's range, so the
 * {@code varName} child's text/range can include trailing spaces (e.g.
 * {@code "timeTest "} in {@code "#timeTest + 2"}). All name extraction and ranges
 * here trim surrounding whitespace so {@code resolve()} matches the declaration
 * and rename preserves the sigil and surrounding spaces.</p>
 */
public class DslVariableRefElement extends ANTLRPsiNode implements PsiReference {

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
        String name = getVariableName();
        XmlFile hostFile = getHostXmlFile();
        if (name == null || hostFile == null) {
            return null;
        }
        for (XmlTag tag : PsiTreeUtil.findChildrenOfType(hostFile, XmlTag.class)) {
            if (!"Var".equals(tag.getName())) {
                continue;
            }
            if (name.equals(tag.getAttributeValue("name"))) {
                XmlAttribute nameAttr = tag.getAttribute("name");
                if (nameAttr == null) {
                    return null;
                }
                XmlAttributeValue nameValue = nameAttr.getValueElement();
                if (nameValue == null) {
                    return null;
                }
                // Resolve to the injected VarNameElement (a PsiNameIdentifierOwner) so that
                // find-usages/rename from the declaration target the variable name, not the tag.
                VarNameElement varNameElement = findVarNameElement(nameValue);
                return varNameElement != null ? varNameElement : nameValue;
            }
        }
        return null;
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
    public boolean isReferenceTo(@NotNull PsiElement element) {
        PsiElement target = resolve();
        return target != null && element.getManager().areElementsEquivalent(target, element);
    }

    @Override
    public @NotNull Object @NotNull [] getVariants() {
        List<LookupElement> variants = new ArrayList<>();
        XmlFile hostFile = getHostXmlFile();
        if (hostFile != null) {
            for (XmlTag tag : PsiTreeUtil.findChildrenOfType(hostFile, XmlTag.class)) {
                if (!"Var".equals(tag.getName())) {
                    continue;
                }
                String name = tag.getAttributeValue("name");
                if (name != null && !name.isEmpty()) {
                    variants.add(LookupElementBuilder.create(name)
                            .withIcon(AllIcons.Nodes.Variable)
                            .withTypeText("Var"));
                }
            }
        }
        RuleRepository repo = RuleRepositoryService.getInstance().getRuleRepository();
        for (DslGlobalVar gv : repo.getAllGlobalVars()) {
            variants.add(LookupElementBuilder.create(gv.getName())
                    .withIcon(AllIcons.Nodes.Variable)
                    .withTypeText(gv.getType()));
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
    private VarNameElement findVarNameElement(XmlAttributeValue nameValue) {
        Project project = getProject();
        if (project == null) {
            return null;
        }
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
