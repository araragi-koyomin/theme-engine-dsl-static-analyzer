package com.huawei.theme.analysis.lsp;

import java.util.Optional;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;

/**
 * Provides hover documentation for DSL tags, derived from the rule library.
 *
 * <p>Hover is only produced for tag contexts; attribute documentation will be
 * added once the rule library exposes per-attribute descriptions.</p>
 */
final class HoverProvider {

    private final RuleRepository ruleRepository;

    HoverProvider(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    Hover hover(ContextResolver.Context ctx) {
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

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
