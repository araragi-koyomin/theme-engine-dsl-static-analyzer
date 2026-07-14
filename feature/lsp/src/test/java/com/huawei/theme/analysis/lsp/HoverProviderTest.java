package com.huawei.theme.analysis.lsp;

import org.junit.jupiter.api.Test;

import org.eclipse.lsp4j.Hover;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoverProviderTest {

    private final RuleRepository repo = new RuleRepositoryFactory(null).create();
    private final HoverProvider provider = new HoverProvider(repo);

    private static ContextResolver.Context tagCtx(String tagName, String word) {
        return new ContextResolver.Context(
                ContextResolver.PositionType.ELEMENT_NAME, tagName, word, null);
    }

    private static ContextResolver.Context attrCtx(String tagName, String attrName) {
        return new ContextResolver.Context(
                ContextResolver.PositionType.ATTRIBUTE_NAME, tagName, attrName, attrName);
    }

    @Test
    void tagHoverRendersElementRule() {
        // "Text" is a known view element in the built-in rule library.
        Hover hover = provider.hover(tagCtx("Text", "Tex"));
        assertNotNull(hover);
        String content = hover.getContents().getRight().getValue();
        assertTrue(content.contains("Text"), "tag hover should mention the tag name");
        // Markup is Markdown (not HTML) so standard LSP clients render it.
        // VS Code treats MarkupContent as Markdown and strips raw HTML tags,
        // which is why the prior HTML markup didn't render there.
        assertTrue(content.startsWith("### "), "tag hover should use a Markdown heading");
        assertTrue(content.contains("**"), "tag hover should use Markdown bold for labels");
        assertTrue(content.contains("`"), "tag hover should use Markdown inline code");
        // Must not contain raw HTML tags that VS Code would strip.
        assertTrue(!content.contains("<h3>") && !content.contains("<b>")
                        && !content.contains("<code>") && !content.contains("<br>"),
                "tag hover must not emit raw HTML tags");
    }

    @Test
    void tagHoverUnknownTagReturnsNull() {
        Hover hover = provider.hover(tagCtx("NoSuchTag", "No"));
        assertNull(hover);
    }

    @Test
    void attributeHoverRendersTypeSpec() {
        // Text.x: type=number, supportsExpression=true, expressionKind=number, defaultValue=0
        Hover hover = provider.hover(attrCtx("Text", "x"));
        assertNotNull(hover);
        String content = hover.getContents().getRight().getValue();
        assertTrue(content.contains("x"), "attribute hover should mention the attr name");
        assertTrue(content.contains("number"), "attribute hover should mention the type");
        assertTrue(content.contains("Expression"), "attribute hover should note expression support");
        assertTrue(content.contains("0"), "attribute hover should show the default value");
    }

    @Test
    void attributeHoverForUnknownAttrFallsBackToTagHover() {
        // attrName set but no AttrTypeSpec in the rule library -> hoverAttribute
        // returns null and the code falls through to tag hover.
        Hover hover = provider.hover(attrCtx("Text", "totallyUnknownAttr"));
        assertNotNull(hover, "should fall back to tag hover for unknown attributes");
        String content = hover.getContents().getRight().getValue();
        assertTrue(content.contains("Text"), "fallback should render the enclosing tag");
    }

    @Test
    void attributeHoverOnUnknownTagReturnsNull() {
        // Unknown tag + attrName set: getAttrTypeSpec empty, getElementRule empty -> null.
        Hover hover = provider.hover(attrCtx("NoSuchTag", "x"));
        assertNull(hover);
    }

    @Test
    void nullTagNameReturnsNull() {
        ContextResolver.Context ctx = new ContextResolver.Context(
                ContextResolver.PositionType.OTHER, null, null, null);
        assertNull(provider.hover(ctx));
    }
}
