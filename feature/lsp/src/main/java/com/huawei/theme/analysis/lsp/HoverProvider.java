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
        sb.append("### ").append(rule.getElementName());
        if (rule.getCategory() != null && !rule.getCategory().isEmpty()) {
            sb.append("  ·  `").append(rule.getCategory()).append("`");
        }
        sb.append("\n\n");

        if (!rule.getRequiredAttrs().isEmpty()) {
            sb.append("**Required:** ")
                    .append(String.join(", ", rule.getRequiredAttrs()))
                    .append("\n\n");
        }
        if (!rule.getOptionalAttrs().isEmpty()) {
            sb.append("**Optional:** ")
                    .append(String.join(", ", rule.getOptionalAttrs()))
                    .append("\n\n");
        }
        if (!rule.getAllowedParents().isEmpty()) {
            sb.append("**Allowed parents:** ")
                    .append(String.join(", ", rule.getAllowedParents()))
                    .append("\n\n");
        }
        if (rule.getInherits() != null && !rule.getInherits().isEmpty()) {
            sb.append("**Inherits:** `").append(rule.getInherits()).append("`\n\n");
        }
        return sb.toString().trim();
    }
}
