package com.huawei.theme.analysis.plugin.editor.expr;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
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
import org.antlr.intellij.adaptor.lexer.TokenIElementType;

import com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionLexer;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionParser;
import com.huawei.theme.analysis.core.expression.model.FunctionParam;
import com.huawei.theme.analysis.core.expression.model.FunctionSignature;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;

/**
 * Documentation provider for DslExpression content, operating on the host
 * ThemeDSL language. When the cursor is inside an expression-supporting
 * attribute value, it creates a dummy DE PSI via {@link PsiFileFactory} to
 * locate the exact element (variable ref or function call), then generates
 * documentation from the rule library.
 */
public class DslExpressionDocumentationProvider extends AbstractDocumentationProvider {

    private static final RuleIElementType AT_VAR_REF;
    private static final RuleIElementType HASH_VAR_REF;
    private static final RuleIElementType VAR_NAME;
    private static final RuleIElementType FUNCTION_CALL;
    private static final int ID_TOKEN = DslExpressionParser.ID;

    private static final Key<XmlFile> HOST_FILE_KEY = Key.create("DslExpressionDocProvider.hostFile");
    private static final Key<XmlTag> HOST_TAG_KEY = Key.create("DslExpressionDocProvider.hostTag");

    static {
        PSIElementTypeFactory.defineLanguageIElementTypes(
                DslExpressionLanguage.INSTANCE,
                DslExpressionLexer.VOCABULARY,
                DslExpressionParser.ruleNames);
        List<RuleIElementType> ruleTypes =
                PSIElementTypeFactory.getRuleIElementTypes(DslExpressionLanguage.INSTANCE);
        AT_VAR_REF = ruleTypes.get(DslExpressionParser.RULE_atVarRef);
        HASH_VAR_REF = ruleTypes.get(DslExpressionParser.RULE_hashVarRef);
        VAR_NAME = ruleTypes.get(DslExpressionParser.RULE_varName);
        FUNCTION_CALL = ruleTypes.get(DslExpressionParser.RULE_functionCall);
    }

    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        return generateDoc(element, originalElement);
    }

    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor,
                                                               @NotNull PsiFile file,
                                                               @Nullable PsiElement contextElement,
                                                               int targetOffset) {
        if (contextElement == null) {
            return null;
        }

        // Cursor must be inside an expression-supporting attribute value
        XmlAttributeValue attrValue = PsiTreeUtil.getParentOfType(contextElement, XmlAttributeValue.class);
        if (attrValue == null) {
            return null;
        }
        return resolveFromHost(editor, file, contextElement, targetOffset, attrValue);
    }

    @Override
    public @Nullable String generateDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
        IElementType type = element.getNode() == null ? null : element.getNode().getElementType();

        if (type == AT_VAR_REF || type == HASH_VAR_REF) {
            return generateVarRefDoc(element);
        }
        if (type == FUNCTION_CALL) {
            String funcName = extractFirstChildText(element, TokenIElementType.class, ID_TOKEN);
            return generateFunctionDoc(funcName);
        }
        // Function name ID inside functionCall
        if (type instanceof TokenIElementType tet && tet.getANTLRTokenType() == ID_TOKEN) {
            PsiElement parent = element.getParent();
            if (parent != null && parent.getNode() != null
                    && parent.getNode().getElementType() == FUNCTION_CALL) {
                return generateFunctionDoc(element.getText());
            }
        }
        return null;
    }

    // ---- ThemeDSL host path ----

    /**
     * Creates a dummy DE file from the attribute value text, finds the element
     * at the cursor offset, walks up to a varRef or functionCall, and returns it.
     * The host XML file and tag are attached via {@link Key} so
     * {@link #generateVarRefDoc} can look up user vars and indexFlag.
     */
    @Nullable
    private static PsiElement resolveFromHost(@NotNull Editor editor,
                                              @NotNull PsiFile file,
                                              @NotNull PsiElement contextElement,
                                              int targetOffset,
                                              @NotNull XmlAttributeValue attrValue) {
        XmlAttribute attr = PsiTreeUtil.getParentOfType(attrValue, XmlAttribute.class);
        XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
        if (attr == null || tag == null) {
            return null;
        }

        RuleRepository repo = RuleRepositoryService.getInstance().getRuleRepository();
        Optional<DslElementRule> ruleOpt = repo.getElementRule(tag.getName());
        if (ruleOpt.isEmpty()) {
            return null;
        }
        AttrTypeSpec spec = findAttrSpec(ruleOpt.get(), attr.getName());
        if (spec == null || !spec.isSupportsExpression()) {
            return null;
        }

        String valueText = attrValue.getValue();
        if (valueText == null || valueText.isEmpty()) {
            return null;
        }

        Project project = editor.getProject();
        if (project == null) {
            return null;
        }

        // Offset within the attribute value (excluding the opening quote)
        int valueStart = attrValue.getTextRange().getStartOffset() + 1;
        int relOffset = targetOffset - valueStart;
        relOffset = Math.max(0, Math.min(relOffset, valueText.length() - 1));

        PsiFile deFile = PsiFileFactory.getInstance(project)
                .createFileFromText(DslExpressionLanguage.INSTANCE, valueText);
        deFile.putUserData(HOST_FILE_KEY, file instanceof XmlFile xmlFile ? xmlFile : null);
        deFile.putUserData(HOST_TAG_KEY, tag);

        PsiElement leaf = deFile.findElementAt(relOffset);
        if (leaf == null) {
            return null;
        }
        return walkToResolvable(leaf);
    }

    @Nullable
    private static PsiElement walkToResolvable(@NotNull PsiElement element) {
        PsiElement e = element;
        while (e != null) {
            IElementType type = e.getNode() == null ? null : e.getNode().getElementType();
            if (type == AT_VAR_REF || type == HASH_VAR_REF) {
                return e;
            }
            if (type == VAR_NAME && e.getParent() != null) {
                IElementType parentType = e.getParent().getNode() == null ? null : e.getParent().getNode().getElementType();
                if (parentType == AT_VAR_REF || parentType == HASH_VAR_REF) {
                    return e.getParent();
                }
            }
            if (type == FUNCTION_CALL) {
                return e;
            }
            if (type instanceof TokenIElementType tet && tet.getANTLRTokenType() == ID_TOKEN) {
                PsiElement parent = e.getParent();
                if (parent != null && parent.getNode() != null
                        && parent.getNode().getElementType() == FUNCTION_CALL) {
                    return e;
                }
            }
            e = e.getParent();
        }
        return null;
    }

    // ---- Doc generation ----

    private String generateVarRefDoc(@NotNull PsiElement varRef) {
        String varName = extractVarName(varRef);
        if (varName == null) {
            return null;
        }

        // 1. Global variable
        RuleRepository repo = RuleRepositoryService.getInstance().getRuleRepository();
        Optional<DslGlobalVar> gvOpt = repo.getGlobalVar(varName);
        if (gvOpt.isPresent()) {
            DslGlobalVar gv = gvOpt.get();
            String sigil = gv.getType() != null && gv.getType().startsWith("string") ? "@" : "#";
            String desc = gv.getDescription() != null ? gv.getDescription() : "No description available.";
            return doc("Global Variable", sigil + gv.getName(), gv.getType(), desc);
        }

        // Obtain host context (from dummy DE file)
        PsiFile containingFile = varRef.getContainingFile();
        XmlFile hostFile = containingFile != null ? containingFile.getUserData(HOST_FILE_KEY) : null;
        XmlTag hostTag = containingFile != null ? containingFile.getUserData(HOST_TAG_KEY) : null;

        // 2. User-defined <Var>
        if (hostFile != null) {
            for (XmlTag tag : PsiTreeUtil.findChildrenOfType(hostFile, XmlTag.class)) {
                if ("Var".equals(tag.getName()) && varName.equals(tag.getAttributeValue("name"))) {
                    String varType = tag.getAttributeValue("type");
                    if (varType == null || varType.isEmpty()) {
                        varType = "number";
                    }
                    String sigil = varType.startsWith("string") ? "@" : "#";
                    return doc("User-defined Variable", sigil + varName, varType, "User-defined variable");
                }
            }
        }

        // 3. Local indexFlag
        if (hostTag != null) {
            for (XmlTag t = hostTag; t != null; t = PsiTreeUtil.getParentOfType(t, XmlTag.class)) {
                String indexFlag = t.getAttributeValue("indexFlag");
                if (varName.equals(indexFlag)) {
                    return doc("Local Variable", "#" + varName, "number", "Local index variable");
                }
            }
        }
        return null;
    }

    private String generateFunctionDoc(@Nullable String funcName) {
        if (funcName == null) {
            return null;
        }
        RuleRepository repo = RuleRepositoryService.getInstance().getRuleRepository();
        FunctionSignatureLibrary lib = repo.getFunctionSignatureLibrary();
        if (lib == null) {
            return doc("Function", funcName + "(...)", "", "Function library not loaded.");
        }
        List<FunctionSignature> sigs = lib.getSignatures(funcName);
        if (sigs.isEmpty()) {
            return doc("Function", funcName + "(...)", "", "Unknown function.");
        }

        StringBuilder sb = new StringBuilder();
        for (FunctionSignature sig : sigs) {
            if (sb.length() > 0) {
                sb.append("<hr>");
            }
            sb.append(DocumentationMarkup.DEFINITION_START);
            sb.append("<b>").append(sig.getName()).append("(");
            List<FunctionParam> params = sig.getParams();
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                FunctionParam p = params.get(i);
                sb.append(p.getName());
                if (p.isVariadic()) {
                    sb.append("...");
                }
                sb.append(": ").append(p.getType() != null ? p.getType().getName() : "number");
            }
            sb.append(")</b>");
            sb.append(" <code>").append(sig.getExpressionKind()).append("</code>");
            sb.append(DocumentationMarkup.DEFINITION_END);
            sb.append(DocumentationMarkup.CONTENT_START);
            String desc = sig.getDescription();
            sb.append(desc != null ? desc : "No description available.");
            sb.append(DocumentationMarkup.CONTENT_END);
        }
        return sb.toString();
    }

    // ---- Helpers ----

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

    private String doc(String kind, String signature, String type, String content) {
        StringBuilder sb = new StringBuilder();
        sb.append(DocumentationMarkup.DEFINITION_START);
        sb.append("<b>").append(kind).append("</b>");
        if (!signature.isEmpty()) {
            sb.append(" <code>").append(signature).append("</code>");
        }
        if (!type.isEmpty()) {
            sb.append(" <code>").append(type).append("</code>");
        }
        sb.append(DocumentationMarkup.DEFINITION_END);
        sb.append(DocumentationMarkup.CONTENT_START);
        sb.append(content);
        sb.append(DocumentationMarkup.CONTENT_END);
        return sb.toString();
    }

    private String extractVarName(@NotNull PsiElement varRef) {
        for (PsiElement child = varRef.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null && child.getNode().getElementType() == VAR_NAME) {
                String text = child.getText();
                return text != null ? text.trim() : null;
            }
        }
        return null;
    }

    @Nullable
    private String extractFirstChildText(@NotNull PsiElement parent, @NotNull Class<TokenIElementType> tokenClass, int antlrTokenType) {
        for (PsiElement child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null && child.getNode().getElementType() instanceof TokenIElementType tet
                    && tet.getANTLRTokenType() == antlrTokenType) {
                return child.getText();
            }
        }
        return null;
    }
}
