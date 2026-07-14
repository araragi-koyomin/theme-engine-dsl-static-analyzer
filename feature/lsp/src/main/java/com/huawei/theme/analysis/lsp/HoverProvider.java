package com.huawei.theme.analysis.lsp;

import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;

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
