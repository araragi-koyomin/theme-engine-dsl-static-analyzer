package com.huawei.theme.analysis.plugin.editor.expr;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
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
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;

/**
 * Documentation provider for the DslExpression language (injected into ThemeDSL
 * attribute values). Provides hover/Quick-Documentation for:
 * <ul>
 *   <li>Global variables — from {@link DslGlobalVar#getDescription()}</li>
 *   <li>User-defined {@code <Var>} — "User-defined variable" + type</li>
 *   <li>Local {@code indexFlag} — "Local variable"</li>
 *   <li>Global functions — placeholder</li>
 * </ul>
 */
public class DslExpressionDocumentationProvider extends AbstractDocumentationProvider {

    private static final RuleIElementType AT_VAR_REF;
    private static final RuleIElementType HASH_VAR_REF;
    private static final RuleIElementType VAR_NAME;
    private static final RuleIElementType FUNCTION_CALL;
    private static final int ID_TOKEN = DslExpressionParser.ID;

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
        // Walk up to the nearest varRef, varName (inside a varRef), or function name ID.
        PsiElement e = contextElement;
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
            // Function name: ID token whose parent is functionCall
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

        // 2. User-defined <Var>
        Project project = varRef.getProject();
        if (project != null) {
            PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(project).getInjectionHost(varRef);
            if (host instanceof XmlAttributeValue hostValue) {
                PsiFile hostFile = hostValue.getContainingFile();
                if (hostFile instanceof XmlFile xmlFile) {
                    for (XmlTag tag : PsiTreeUtil.findChildrenOfType(xmlFile, XmlTag.class)) {
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
                XmlAttribute attr = PsiTreeUtil.getParentOfType(hostValue, XmlAttribute.class);
                if (attr != null) {
                    XmlTag enclosingTag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
                    for (XmlTag t = enclosingTag; t != null; t = PsiTreeUtil.getParentOfType(t, XmlTag.class)) {
                        String indexFlag = t.getAttributeValue("indexFlag");
                        if (varName.equals(indexFlag)) {
                            return doc("Local Variable", "#" + varName, "number", "Local index variable");
                        }
                    }
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
