package com.huawei.theme.analysis.lsp;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupContent;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletionProviderTest {

    private final RuleRepository repo = new RuleRepositoryFactory(null).create();
    private final CompletionProvider provider = new CompletionProvider(repo);

    private static ContextResolver.Context elemCtx(String word) {
        return new ContextResolver.Context(
                ContextResolver.PositionType.ELEMENT_NAME, null, word, null, null, null);
    }

    private static ContextResolver.Context attrValueCtx(String tagName, String attrName, String word) {
        return new ContextResolver.Context(
                ContextResolver.PositionType.ATTRIBUTE_VALUE, tagName, word, attrName, null, null);
    }

    private static ContextResolver.Context attrNameCtx(String tagName, String word) {
        return new ContextResolver.Context(
                ContextResolver.PositionType.ATTRIBUTE_NAME, tagName, word, null, null, null);
    }

    private static Set<String> emptySet() {
        return Collections.emptySet();
    }

    @Test
    void enumValueCompletionForTextAlign() {
        // Text.align: enumValues = [left, center, right]
        List<CompletionItem> items = provider.complete(attrValueCtx("Text", "align", ""), emptySet(), emptySet());
        assertEquals(3, items.size());        List<String> labels = items.stream().map(CompletionItem::getLabel).toList();
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
        List<CompletionItem> items = provider.complete(attrValueCtx("Var", "type", ""), emptySet(), emptySet());
        assertEquals(4, items.size());
        // Default value "number" sorted first.
        CompletionItem first = items.get(0);
        assertEquals("number", first.getLabel());
        assertTrue(first.getSortText().startsWith("0_"));
    }

    @Test
    void enumValueCompletionPrefixFilter() {
        // "c" prefix -> only "center" for Text.align
        List<CompletionItem> items = provider.complete(attrValueCtx("Text", "align", "c"), emptySet(), emptySet());
        assertEquals(1, items.size());
        assertEquals("center", items.get(0).getLabel());
    }

    @Test
    void expressionAttrOffersVariablesAndFunctions() {
        // Text.x is a number attr with no enumValues but supportsExpression.
        // Now offers variables (global + declared) and functions.
        List<CompletionItem> items = provider.complete(attrValueCtx("Text", "x", ""),
                emptySet(), Set.of("myVar"));
        // Global variables (battery_level etc.) and the declared "myVar"
        // should appear as #name / @name.
        assertTrue(items.stream().anyMatch(i -> i.getLabel().contains("myVar")),
                "should offer declared variable #myVar / @myVar");
        assertTrue(items.stream().anyMatch(i -> i.getKind() == CompletionItemKind.Variable),
                "should offer variable completion items");
        // Functions should also appear.
        assertTrue(items.stream().anyMatch(i -> i.getKind() == CompletionItemKind.Function),
                "should offer function completion items");
    }

    @Test
    void attributeNameInsertTextIsSnippetWithCursorInQuotes() {
        List<CompletionItem> items = provider.complete(attrNameCtx("Text", "nam"), emptySet(), emptySet());
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
        List<CompletionItem> items = provider.complete(attrNameCtx("Var", ""), emptySet(), emptySet());
        CompletionItem nameItem = items.stream()
                .filter(i -> "name".equals(i.getLabel()))
                .findFirst()
                .orElseThrow();
        assertEquals(CompletionItemKind.Field, nameItem.getKind());
        assertEquals("required", nameItem.getDetail());
        assertTrue(nameItem.getSortText().startsWith("0_"));
    }

    /**
     * End-to-end via the text {@link ContextResolver} (the AST-failure
     * fallback): on an unclosed tag the three cursor positions must still
     * resolve to element-name / attribute-name / attribute-value completion.
     */
    @Test
    void threePositionsOnUnclosedTagViaTextFallback() {
        // "<Text align=\"" — unclosed tag (mid-typing); AST would fail.
        // positions: '<'(0) T(1)e(2)x(3)t(4) ' '(5) a(6)l(7)i(8)g(9)n(10) '='(11) '"'(12)
        String text = "<Text align=\"";

        // 1) right after '<' (offset 1) -> element name
        ContextResolver.Context elemCtx = new ContextResolver(text).resolve(1);
        assertEquals(ContextResolver.PositionType.ELEMENT_NAME, elemCtx.type);
        assertTrue(provider.complete(elemCtx, emptySet(), emptySet()).stream().anyMatch(i -> "Text".equals(i.getLabel())));

        // 2) in the attribute name "ali|gn" (offset 9) -> attribute name
        ContextResolver.Context attrCtx = new ContextResolver(text).resolve(9);
        assertEquals(ContextResolver.PositionType.ATTRIBUTE_NAME, attrCtx.type);
        assertEquals("Text", attrCtx.tagName);
        assertTrue(provider.complete(attrCtx, emptySet(), emptySet()).stream().anyMatch(i -> "align".equals(i.getLabel())));

        // 3) inside the quotes (offset 13, after opening quote) -> value
        ContextResolver.Context valCtx = new ContextResolver(text).resolve(13);
        assertEquals(ContextResolver.PositionType.ATTRIBUTE_VALUE, valCtx.type);
        assertEquals("align", valCtx.attrName);
        List<CompletionItem> valItems = provider.complete(valCtx, emptySet(), emptySet());
        assertTrue(valItems.stream().anyMatch(i -> "left".equals(i.getLabel())));
        assertTrue(valItems.stream().anyMatch(i -> "center".equals(i.getLabel())));
    }

    @Test
    void elementCompletionCarriesDocumentationAndCategoryDetail() {
        List<CompletionItem> items = provider.complete(elemCtx(""), emptySet(), emptySet());
        CompletionItem text = items.stream()
                .filter(i -> "Text".equals(i.getLabel()))
                .findFirst()
                .orElseThrow();
        // Detail reflects the element's category (not a generic "tag").
        assertEquals("view", text.getDetail());
        // Documentation carries the element-rule markup (category / required /
        // optional / allowed parents / inherits).
        MarkupContent doc = text.getDocumentation().getRight();
        assertNotNull(doc);
        assertTrue(doc.getValue().contains("Text"));
        assertTrue(doc.getValue().contains("Required") || doc.getValue().contains("Optional"));
    }

    @Test
    void attributeCompletionCarriesDocumentation() {
        List<CompletionItem> items = provider.complete(attrNameCtx("Text", ""), emptySet(), emptySet());
        CompletionItem align = items.stream()
                .filter(i -> "align".equals(i.getLabel()))
                .findFirst()
                .orElseThrow();
        MarkupContent doc = align.getDocumentation().getRight();
        assertNotNull(doc, "attribute completion must carry documentation");
        assertTrue(doc.getValue().contains("align"));
        assertTrue(doc.getValue().contains("Enum"));
    }
}
