package com.huawei.theme.analysis.lsp;

import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;

import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;

/**
 * Provides hover documentation for DSL tags and attributes, derived from the
 * rule library.
 *
 * <p>Tag hover renders the element rule (category / required / optional /
 * allowed parents / inherits). Attribute hover renders the attribute type
 * spec (type / default / enum / aliases / expression support), populated by
 * the AST-based {@link AstContextResolver} which sets {@code ctx.attrName}.
 * The rule library has no per-attribute free-text description, so hover is
 * synthesized from the structured {@link AttrTypeSpec} metadata.</p>
 *
 * <p>The markup is produced as <b>Markdown</b> (not HTML) so that standard LSP
 * clients (VS Code, Neovim, Helix) render it natively — those clients treat
 * {@code MarkupContent} as Markdown and strip raw HTML tags, which is why the
 * prior HTML markup appeared as unformatted run-on text in VS Code. The
 * IntelliJ client (which renders HTML in its documentation panel) converts
 * this Markdown back to HTML in
 * {@code ThemeDslLspHoverProvider#markdownToHtml}.</p>
 */
final class HoverProvider {

    private final RuleRepository ruleRepository;

    HoverProvider(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    Hover hover(ContextResolver.Context ctx) {
        // Per-token hover inside expressions (variable ref / function / literal)
        if (ctx.exprNode != null) {
            String markup = hoverExpressionToken(ctx.exprNode);
            if (markup != null) {
                return new Hover(new MarkupContent(MarkupKind.MARKDOWN, markup));
            }
        }
        // Variable definition hover: cursor on name="..." value of a Var element
        if (ctx.attrName != null && ctx.tagName != null && ctx.elementNode != null
                && "name".equals(ctx.attrName) && isVariableTag(ctx.tagName)) {
            String markup = hoverVariableDef(ctx.elementNode);
            if (markup != null) {
                return new Hover(new MarkupContent(MarkupKind.MARKDOWN, markup));
            }
        }
        if (ctx.attrName != null && ctx.tagName != null) {
            String attrMarkup = attributeMarkup(ctx.tagName, ctx.attrName);
            if (attrMarkup != null) {
                return new Hover(new MarkupContent(MarkupKind.MARKDOWN, attrMarkup));
            }
        }
        String tagMarkup = tagMarkup(ctx.tagName);
        if (tagMarkup == null) {
            return null;
        }
        return new Hover(new MarkupContent(MarkupKind.MARKDOWN, tagMarkup));
    }

    private String hoverExpressionToken(ExpressionAstNode node) {
        ExpressionKind kind = node.getKind();
        switch (kind) {
            case VARIABLE_REF:
            case ARRAY_ACCESS:
                return hoverVariableRef((ExpressionNode) node);
            case FUNCTION_CALL:
                return hoverFunctionCall((ExpressionNode) node);
            case LITERAL:
                return hoverLiteral(node);
            default:
                return null;
        }
    }

    private String hoverVariableRef(ExpressionNode node) {
        String varName = node.getVariableName();
        String prefix = node.getPrefix();
        String refText = (prefix != null ? prefix : "") + (varName != null ? varName : "");
        StringBuilder sb = new StringBuilder();
        sb.append("### `").append(esc(refText)).append("` — 变量引用\n\n");
        // Check if it's a known global variable
        if (varName != null && !varName.isEmpty()) {
            var globalVar = ruleRepository.getGlobalVar(varName);
            if (globalVar.isPresent()) {
                var gv = globalVar.get();
                sb.append("**类型:** `").append(esc(gv.getType())).append("`  \n");
                if (gv.getScope() != null && !gv.getScope().isEmpty()) {
                    sb.append("**作用域:** `").append(esc(gv.getScope())).append("`  \n");
                }
                if (gv.getDescription() != null && !gv.getDescription().isEmpty()) {
                    sb.append("**描述:** ").append(esc(gv.getDescription())).append("  \n");
                }
            } else {
                sb.append("用户定义变量（需在文件中声明 `").append(esc(varName))
                        .append("`）  \n");
            }
        }
        return sb.toString();
    }

    private String hoverFunctionCall(ExpressionNode node) {
        String fnName = node.getFunctionName();
        if (fnName == null || fnName.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("### `").append(esc(fnName)).append("()` — 函数调用\n\n");
        var fnLib = ruleRepository.getFunctionSignatureLibrary();
        if (fnLib != null) {
            var sigs = fnLib.getSignatures(fnName);
            if (!sigs.isEmpty()) {
                var sig = sigs.get(0);
                var returnType = sig.getReturnType();
                if (returnType != null && returnType.getName() != null && !returnType.getName().isEmpty()) {
                    sb.append("**返回类型:** `").append(esc(returnType.getName())).append("`  \n");
                }
                var params = sig.getParams();
                if (params != null && !params.isEmpty()) {
                    sb.append("**参数:**\n");
                    for (var p : params) {
                        sb.append("- `").append(esc(p.getName())).append("`");
                        var pType = p.getType();
                        if (pType != null && pType.getName() != null && !pType.getName().isEmpty()) {
                            sb.append(" (").append(esc(pType.getName())).append(")");
                        }
                        if (p.isVariadic()) {
                            sb.append(" — 可变参数");
                        }
                        sb.append("  \n");
                    }
                }
            }
        }
        return sb.toString();
    }

    private String hoverLiteral(ExpressionAstNode node) {
        String t = node.getText();
        if (t == null || t.isEmpty()) {
            return null;
        }
        boolean isString = t.charAt(0) == '\'';
        return "### `" + esc(t) + "` — " + (isString ? "字符串字面量" : "数字字面量") + "\n";
    }

    private String hoverVariableDef(com.huawei.theme.analysis.core.shared.ast.DslElementNode element) {
        String varName = null;
        String varType = null;
        String varExpr = null;
        var attrs = element.getAttributes();
        if (attrs != null) {
            for (var attr : attrs) {
                String name = attr.getName();
                var value = attr.getValue();
                String raw = value != null ? value.getRawValue() : null;
                if ("name".equals(name)) {
                    varName = raw;
                } else if ("type".equals(name)) {
                    varType = raw;
                } else if ("expression".equals(name)) {
                    varExpr = raw;
                }
            }
        }
        if (varName == null || varName.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("### `").append(esc(varName)).append("` — 变量定义\n\n");
        if (varType != null && !varType.isEmpty()) {
            sb.append("**类型:** `").append(esc(varType)).append("`  \n");
        }
        if (varExpr != null && !varExpr.isEmpty()) {
            sb.append("**表达式:** `").append(esc(varExpr)).append("`  \n");
        }
        return sb.toString();
    }

    private boolean isVariableTag(String tagName) {
        var rule = ruleRepository.getElementRule(tagName);
        return rule.isPresent() && "variable".equals(rule.get().getCategory());
    }

    /**
     * Renders the element-rule markup (category / required / optional / allowed
     * parents / inherits) for the given tag, or {@code null} if the tag is
     * unknown to the rule library. Reused by {@link CompletionProvider} to
     * attach documentation to element-name completion items.
     */
    String tagMarkup(String tagName) {
        if (tagName == null || tagName.isEmpty()) {
            return null;
        }
        Optional<DslElementRule> ruleOpt = ruleRepository.getElementRule(tagName);
        return ruleOpt.isEmpty() ? null : renderTag(ruleOpt.get());
    }

    /**
     * Renders the attribute type-spec markup (type / default / enum / aliases /
     * expression) for the given attribute, or {@code null} if unknown. Reused
     * by {@link CompletionProvider} to attach documentation to attribute-name
     * completion items.
     */
    String attributeMarkup(String tagName, String attrName) {
        if (tagName == null || attrName == null) {
            return null;
        }
        Optional<AttrTypeSpec> specOpt = ruleRepository.getAttrTypeSpec(tagName, attrName);
        return specOpt.isEmpty() ? null : renderAttribute(attrName, specOpt.get());
    }

    private String renderTag(DslElementRule rule) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(esc(rule.getElementName()));
        if (rule.getCategory() != null && !rule.getCategory().isEmpty()) {
            sb.append(" `").append(esc(rule.getCategory())).append("`");
        }
        sb.append("\n\n");
        if (!rule.getRequiredAttrs().isEmpty()) {
            sb.append("**Required:** `").append(esc(String.join("`, `", rule.getRequiredAttrs())))
                    .append("`  \n");
        }
        if (!rule.getOptionalAttrs().isEmpty()) {
            sb.append("**Optional:** `").append(esc(String.join("`, `", rule.getOptionalAttrs())))
                    .append("`  \n");
        }
        if (!rule.getAllowedParents().isEmpty()) {
            sb.append("**Allowed parents:** `").append(esc(String.join("`, `", rule.getAllowedParents())))
                    .append("`  \n");
        }
        if (rule.getInherits() != null && !rule.getInherits().isEmpty()) {
            sb.append("**Inherits:** `").append(esc(rule.getInherits())).append("`  \n");
        }
        return sb.toString();
    }

    private String renderAttribute(String attrName, AttrTypeSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(esc(attrName));
        if (spec.getType() != null && !spec.getType().isEmpty()) {
            sb.append(" `").append(esc(spec.getType())).append("`");
        }
        sb.append("\n\n");
        if (spec.getDefaultValue() != null && !spec.getDefaultValue().isEmpty()) {
            sb.append("**Default:** `").append(esc(spec.getDefaultValue())).append("`  \n");
        }
        if (spec.getEnumValues() != null && !spec.getEnumValues().isEmpty()) {
            sb.append("**Enum:** `").append(esc(String.join("`, `", spec.getEnumValues()))).append("`  \n");
        }
        if (spec.getAliases() != null && !spec.getAliases().isEmpty()) {
            sb.append("**Aliases:** `").append(esc(String.join("`, `", spec.getAliases()))).append("`  \n");
        }
        if (spec.isSupportsExpression()) {
            sb.append("**Expression:** supported");
            if (spec.getExpressionKind() != null && !spec.getExpressionKind().isEmpty()) {
                sb.append(" (`").append(esc(spec.getExpressionKind())).append("`)");
            }
            sb.append("  \n");
        }
        return sb.toString();
    }

    /**
     * Escapes Markdown special characters in raw text so element/attribute
     * names and values render literally. Backticks are the delimiter for
     * inline code, so any literal backtick in a name (none expected) would
     * break formatting — escape them. {@code *}, {@code #}, {@code [} are
     * also escaped to avoid accidental bold/heading/link interpretation.
     */
    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("#", "\\#")
                .replace("[", "\\[")
                .replace("]", "\\]");
    }
}
