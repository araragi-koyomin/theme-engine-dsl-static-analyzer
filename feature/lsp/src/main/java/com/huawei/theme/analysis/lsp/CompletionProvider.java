package com.huawei.theme.analysis.lsp;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;

/**
 * Provides completion items for element names and attribute names based on
 * the cursor context resolved by {@link ContextResolver}.
 *
 * <p>Element-name completion offers every tag from the rule library. Attribute
 * completion offers the canonical attribute set of the enclosing tag, with
 * required attributes sorted first.</p>
 */
final class CompletionProvider {

    private final RuleRepository ruleRepository;

    CompletionProvider(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    List<CompletionItem> complete(ContextResolver.Context ctx) {
        if (ctx.type == ContextResolver.PositionType.ELEMENT_NAME) {
            return elementNameItems(ctx.word);
        }
        if (ctx.type == ContextResolver.PositionType.ATTRIBUTE_NAME && ctx.tagName != null) {
            return attributeNameItems(ctx.tagName, ctx.word);
        }
        return List.of();
    }

    private List<CompletionItem> elementNameItems(String prefix) {
        List<CompletionItem> items = new ArrayList<>();
        for (String name : ruleRepository.getAllElementNames()) {
            if (!matches(prefix, name)) {
                continue;
            }
            CompletionItem item = new CompletionItem(name);
            item.setKind(CompletionItemKind.Class);
            item.setDetail("ThemeDSL tag");
            items.add(item);
        }
        return items;
    }

    private List<CompletionItem> attributeNameItems(String tagName, String prefix) {
        Optional<DslElementRule> ruleOpt = ruleRepository.getElementRule(tagName);
        if (ruleOpt.isEmpty()) {
            return List.of();
        }
        DslElementRule rule = ruleOpt.get();
        Set<String> canonical = ruleRepository.getCanonicalAttrNames(tagName);
        List<String> required = rule.getRequiredAttrs();
        List<CompletionItem> items = new ArrayList<>();
        for (String attr : canonical) {
            if (!matches(prefix, attr)) {
                continue;
            }
            boolean isRequired = required != null && required.contains(attr);
            CompletionItem item = new CompletionItem(attr);
            item.setKind(isRequired ? CompletionItemKind.Field : CompletionItemKind.Property);
            item.setDetail(isRequired ? "required" : "optional");
            item.setSortText(isRequired ? "0_" + attr : "1_" + attr);
            items.add(item);
        }
        return items;
    }

    private static boolean matches(String prefix, String candidate) {
        return prefix == null || prefix.isEmpty()
                || candidate.toLowerCase().startsWith(prefix.toLowerCase());
    }
}
