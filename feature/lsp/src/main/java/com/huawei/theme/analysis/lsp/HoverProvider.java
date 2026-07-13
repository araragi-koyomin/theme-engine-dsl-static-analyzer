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
 */
final class HoverProvider {

    private final RuleRepository ruleRepository;

    HoverProvider(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    Hover hover(ContextResolver.Context ctx) {
        if (ctx.attrName != null && ctx.tagName != null) {
            Hover attrHover = hoverAttribute(ctx.tagName, ctx.attrName);
            if (attrHover != null) {
                return attrHover;
            }
        }
        String tagName = ctx.tagName;
        if (tagName == null || tagName.isEmpty()) {
            return null;
        }
        Optional<DslElementRule> ruleOpt = ruleRepository.getElementRule(tagName);
        if (ruleOpt.isEmpty()) {
            return null;
        }
        DslElementRule rule = ruleOpt.get();
        return new Hover(new MarkupContent(MarkupKind.MARKDOWN, renderTag(rule)));
    }

    private Hover hoverAttribute(String tagName, String attrName) {
        Optional<AttrTypeSpec> specOpt = ruleRepository.getAttrTypeSpec(tagName, attrName);
        if (specOpt.isEmpty()) {
            return null;
        }
        String rendered = renderAttribute(attrName, specOpt.get());
        return new Hover(new MarkupContent(MarkupKind.MARKDOWN, rendered));
    }

    private String renderTag(DslElementRule rule) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h3>").append(esc(rule.getElementName())).append("</h3>");
        if (rule.getCategory() != null && !rule.getCategory().isEmpty()) {
            sb.append(" <code>").append(esc(rule.getCategory())).append("</code>");
        }
        sb.append("<br>");
        if (!rule.getRequiredAttrs().isEmpty()) {
            sb.append("<b>Required:</b> ")
                    .append(esc(String.join(", ", rule.getRequiredAttrs())))
                    .append("<br>");
        }
        if (!rule.getOptionalAttrs().isEmpty()) {
            sb.append("<b>Optional:</b> ")
                    .append(esc(String.join(", ", rule.getOptionalAttrs())))
                    .append("<br>");
        }
        if (!rule.getAllowedParents().isEmpty()) {
            sb.append("<b>Allowed parents:</b> ")
                    .append(esc(String.join(", ", rule.getAllowedParents())))
                    .append("<br>");
        }
        if (rule.getInherits() != null && !rule.getInherits().isEmpty()) {
            sb.append("<b>Inherits:</b> <code>").append(esc(rule.getInherits())).append("</code><br>");
        }
        return sb.toString();
    }

    private String renderAttribute(String attrName, AttrTypeSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h3>").append(esc(attrName)).append("</h3>");
        if (spec.getType() != null && !spec.getType().isEmpty()) {
            sb.append(" <code>").append(esc(spec.getType())).append("</code>");
        }
        sb.append("<br>");
        if (spec.getDefaultValue() != null && !spec.getDefaultValue().isEmpty()) {
            sb.append("<b>Default:</b> <code>").append(esc(spec.getDefaultValue())).append("</code><br>");
        }
        if (spec.getEnumValues() != null && !spec.getEnumValues().isEmpty()) {
            sb.append("<b>Enum:</b> ").append(esc(String.join(", ", spec.getEnumValues()))).append("<br>");
        }
        if (spec.getAliases() != null && !spec.getAliases().isEmpty()) {
            sb.append("<b>Aliases:</b> ").append(esc(String.join(", ", spec.getAliases()))).append("<br>");
        }
        if (spec.isSupportsExpression()) {
            sb.append("<b>Expression:</b> supported");
            if (spec.getExpressionKind() != null && !spec.getExpressionKind().isEmpty()) {
                sb.append(" (<code>").append(esc(spec.getExpressionKind())).append("</code>)");
            }
            sb.append("<br>");
        }
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
