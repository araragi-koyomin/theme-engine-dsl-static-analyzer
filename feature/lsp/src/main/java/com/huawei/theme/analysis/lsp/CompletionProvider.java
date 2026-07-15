package com.huawei.theme.analysis.lsp;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;

/**
 * Provides completion items for element names, attribute names, and attribute
 * values based on the cursor context resolved by {@link ContextResolver}.
 *
 * <p>Element-name completion offers every tag from the rule library. Attribute
 * completion offers the canonical attribute set of the enclosing tag, with
 * required attributes sorted first; each item is inserted as a snippet
 * {@code name="$0"} so the cursor lands inside the quotes for immediate value
 * entry. Attribute-value completion offers the attribute's enum values (e.g.
 * {@code Var.type} → number/string/..., {@code Text.align} → left/center/right)
 * when the rule library declares them; the default value is sorted first.</p>
 */
final class CompletionProvider {

    private final RuleRepository ruleRepository;
    private final HoverProvider hoverProvider;

    CompletionProvider(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
        this.hoverProvider = new HoverProvider(ruleRepository);
    }

    List<CompletionItem> complete(ContextResolver.Context ctx) {
        if (ctx.type == ContextResolver.PositionType.ELEMENT_NAME) {
            return elementNameItems(ctx.word);
        }
        if (ctx.type == ContextResolver.PositionType.ATTRIBUTE_NAME && ctx.tagName != null) {
            return attributeNameItems(ctx.tagName, ctx.word);
        }
        if (ctx.type == ContextResolver.PositionType.ATTRIBUTE_VALUE
                && ctx.tagName != null && ctx.attrName != null) {
            return attributeValueItems(ctx.tagName, ctx.attrName, ctx.word);
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
            Optional<DslElementRule> ruleOpt = ruleRepository.getElementRule(name);
            String category = ruleOpt.map(DslElementRule::getCategory).orElse(null);
            item.setDetail((category != null && !category.isEmpty()) ? category : "ThemeDSL tag");
            String markup = hoverProvider.tagMarkup(name);
            if (markup != null) {
                item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, markup));
            }
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
            // Insert as a snippet so the cursor lands inside the quotes,
            // ready for value entry (clients that don't support snippets fall
            // back to the label).
            item.setInsertText(attr + "=\"$0\"");
            item.setInsertTextFormat(InsertTextFormat.Snippet);
            String markup = hoverProvider.attributeMarkup(tagName, attr);
            if (markup != null) {
                item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, markup));
            }
            items.add(item);
        }
        return items;
    }

    private List<CompletionItem> attributeValueItems(String tagName, String attrName, String prefix) {
        Optional<AttrTypeSpec> specOpt = ruleRepository.getAttrTypeSpec(tagName, attrName);
        if (specOpt.isEmpty()) {
            return List.of();
        }
        AttrTypeSpec spec = specOpt.get();
        List<String> enumValues = spec.getEnumValues();
        if (enumValues == null || enumValues.isEmpty()) {
            return List.of();
        }
        String defaultValue = spec.getDefaultValue();
        List<CompletionItem> items = new ArrayList<>();
        for (String value : enumValues) {
            if (!matches(prefix, value)) {
                continue;
            }
            boolean isDefault = value.equals(defaultValue);
            CompletionItem item = new CompletionItem(value);
            item.setKind(CompletionItemKind.EnumMember);
            item.setDetail(spec.getType());
            item.setSortText(isDefault ? "0_" + value : "1_" + value);
            // Insert the bare value; the surrounding quotes already exist.
            item.setInsertText(value);
            items.add(item);
        }
        return items;
    }

    private static boolean matches(String prefix, String candidate) {
        return prefix == null || prefix.isEmpty()
                || candidate.toLowerCase().startsWith(prefix.toLowerCase());
    }
}
