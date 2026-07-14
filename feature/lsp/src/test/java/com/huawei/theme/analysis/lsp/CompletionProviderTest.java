package com.huawei.theme.analysis.lsp;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletionProviderTest {

    private final RuleRepository repo = new RuleRepositoryFactory(null).create();
    private final CompletionProvider provider = new CompletionProvider(repo);

    private static ContextResolver.Context attrValueCtx(String tagName, String attrName, String word) {
        return new ContextResolver.Context(
                ContextResolver.PositionType.ATTRIBUTE_VALUE, tagName, word, attrName);
    }

    private static ContextResolver.Context attrNameCtx(String tagName, String word) {
        return new ContextResolver.Context(
                ContextResolver.PositionType.ATTRIBUTE_NAME, tagName, word, null);
    }

    @Test
    void enumValueCompletionForTextAlign() {
        // Text.align: enumValues = [left, center, right]
        List<CompletionItem> items = provider.complete(attrValueCtx("Text", "align", ""));
        assertEquals(3, items.size());
        List<String> labels = items.stream().map(CompletionItem::getLabel).toList();
        assertTrue(labels.contains("left"));
        assertTrue(labels.contains("center"));
        assertTrue(labels.contains("right"));
        for (CompletionItem item : items) {
            assertEquals(CompletionItemKind.EnumMember, item.getKind());
            assertEquals("string", item.getDetail());
            // Bare value, no extra quotes (the surrounding quotes exist).
            assertEquals(item.getLabel(), item.getInsertText());
        }
    }

    @Test
    void enumValueCompletionForVarType() {
        // Var.type: enumValues = [number, string, number[], string[]], default=number
        List<CompletionItem> items = provider.complete(attrValueCtx("Var", "type", ""));
        assertEquals(4, items.size());
        // Default value "number" sorted first.
        CompletionItem first = items.get(0);
        assertEquals("number", first.getLabel());
        assertTrue(first.getSortText().startsWith("0_"));
    }

    @Test
    void enumValueCompletionPrefixFilter() {
        // "c" prefix -> only "center" for Text.align
        List<CompletionItem> items = provider.complete(attrValueCtx("Text", "align", "c"));
        assertEquals(1, items.size());
        assertEquals("center", items.get(0).getLabel());
    }

    @Test
    void enumValueCompletionNonEnumAttrReturnsEmpty() {
        // Text.x is a number attr with no enumValues -> no value completion.
        List<CompletionItem> items = provider.complete(attrValueCtx("Text", "x", ""));
        assertTrue(items.isEmpty());
    }

    @Test
    void attributeNameInsertTextIsSnippetWithCursorInQuotes() {
        List<CompletionItem> items = provider.complete(attrNameCtx("Text", "nam"));
        // "name" is an optional attr of Text; prefix "nam" matches it.
        CompletionItem nameItem = items.stream()
                .filter(i -> "name".equals(i.getLabel()))
                .findFirst()
                .orElseThrow();
        assertEquals("name=\"$0\"", nameItem.getInsertText());
        assertEquals(InsertTextFormat.Snippet, nameItem.getInsertTextFormat());
    }

    @Test
    void attributeNameRequiredSortedFirst() {
        // Var "name" is required; it should sort before optional attrs.
        List<CompletionItem> items = provider.complete(attrNameCtx("Var", ""));
        CompletionItem nameItem = items.stream()
                .filter(i -> "name".equals(i.getLabel()))
                .findFirst()
                .orElseThrow();
        assertEquals(CompletionItemKind.Field, nameItem.getKind());
        assertEquals("required", nameItem.getDetail());
        assertTrue(nameItem.getSortText().startsWith("0_"));
    }
}
