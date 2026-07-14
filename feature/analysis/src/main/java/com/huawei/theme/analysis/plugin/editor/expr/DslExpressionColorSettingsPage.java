package com.huawei.theme.analysis.plugin.editor.expr;

import java.util.HashMap;
import java.util.Map;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;

/**
 * Registers a user-configurable color settings page under
 * {@code Editor > Color Scheme > DslExpression} so the highlighting keys
 * declared in {@link DslExpressionSyntaxHighlighter} (lexer) and
 * {@link DslExpressionSemanticAnnotator} (semantic) can be customized.
 *
 * <p>The demo text uses {@code <tag>...</tag>} markers (mapped via
 * {@link #getAdditionalHighlightingTagToDescriptorMap()}) to showcase the
 * semantic colors, since the color-settings preview only runs the lexer
 * highlighter, not the annotator.</p>
 */
public class DslExpressionColorSettingsPage implements ColorSettingsPage {

    private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
            new AttributesDescriptor("Number", DslExpressionSyntaxHighlighter.NUMBER),
            new AttributesDescriptor("String", DslExpressionSyntaxHighlighter.STRING),
            new AttributesDescriptor("Identifier (function name)", DslExpressionSyntaxHighlighter.IDENTIFIER),
            new AttributesDescriptor("Variable name", DslExpressionSyntaxHighlighter.VARIABLE_NAME),
            new AttributesDescriptor("Variable prefix (# @)", DslExpressionSyntaxHighlighter.VAR_PREFIX),
            new AttributesDescriptor("Operator", DslExpressionSyntaxHighlighter.OPERATOR),
            new AttributesDescriptor("Parentheses", DslExpressionSyntaxHighlighter.PARENTHESES),
            new AttributesDescriptor("Brackets", DslExpressionSyntaxHighlighter.BRACKETS),
            new AttributesDescriptor("Braces", DslExpressionSyntaxHighlighter.BRACES),
            new AttributesDescriptor("Comma", DslExpressionSyntaxHighlighter.COMMA),
            new AttributesDescriptor("Bad character", DslExpressionSyntaxHighlighter.BAD_CHARACTER),
            new AttributesDescriptor("Function call", DslExpressionSemanticAnnotator.FUNCTION),
            new AttributesDescriptor("String variable (@name)", DslExpressionSemanticAnnotator.STRING_VARIABLE),
            new AttributesDescriptor("Numeric variable (#name)", DslExpressionSemanticAnnotator.NUMERIC_VARIABLE)
    };

    private static final String DEMO_TEXT =
            "<func>max</func>(<num>#w</num>,<num>#h</num>)+<str>@label</str>+'x'";

    @Nullable
    @Override
    public Icon getIcon() {
        return null;
    }

    @NotNull
    @Override
    public DslExpressionSyntaxHighlighter getHighlighter() {
        return new DslExpressionSyntaxHighlighter();
    }

    @NotNull
    @Override
    public String getDemoText() {
        return DEMO_TEXT;
    }

    @Nullable
    @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        Map<String, TextAttributesKey> tags = new HashMap<>();
        tags.put("func", DslExpressionSemanticAnnotator.FUNCTION);
        tags.put("str", DslExpressionSemanticAnnotator.STRING_VARIABLE);
        tags.put("num", DslExpressionSemanticAnnotator.NUMERIC_VARIABLE);
        return tags;
    }

    @NotNull
    @Override
    public AttributesDescriptor[] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @NotNull
    @Override
    public ColorDescriptor[] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "DslExpression";
    }
}
