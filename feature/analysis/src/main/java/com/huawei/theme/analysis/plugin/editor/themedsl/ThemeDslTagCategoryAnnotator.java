package com.huawei.theme.analysis.plugin.editor.themedsl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElementType;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * Colors ThemeDSL tag names according to their rule library category.
 *
 * <p>Each category has a custom default color defined in the bundled color
 * scheme file ({@code colorSchemes/ThemeDevStudio.xml}). Colors are customizable
 * via {@code Editor > Color Scheme > ThemeDSL Tags}.</p>
 */
public class ThemeDslTagCategoryAnnotator implements Annotator {

    public static final TextAttributesKey ROOT =
            createTextAttributesKey("DSL_TAG_ROOT", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey VIEW =
            createTextAttributesKey("DSL_TAG_VIEW", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey LAYOUT =
            createTextAttributesKey("DSL_TAG_LAYOUT", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey VARIABLE =
            createTextAttributesKey("DSL_TAG_VARIABLE", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey CONTROL =
            createTextAttributesKey("DSL_TAG_CONTROL", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey COMMAND =
            createTextAttributesKey("DSL_TAG_COMMAND", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey DATA_OPEN =
            createTextAttributesKey("DSL_TAG_DATA_OPEN", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey ANIMATION =
            createTextAttributesKey("DSL_TAG_ANIMATION", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey EFFECT =
            createTextAttributesKey("DSL_TAG_EFFECT", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey LONGTAKE =
            createTextAttributesKey("DSL_TAG_LONGTAKE", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey THREE_D =
            createTextAttributesKey("DSL_TAG_THREE_D", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey TRIGGER =
            createTextAttributesKey("DSL_TAG_TRIGGER", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey UNKNOWN =
            createTextAttributesKey("DSL_TAG_UNKNOWN", DefaultLanguageHighlighterColors.IDENTIFIER);

    private static final Map<String, TextAttributesKey> CATEGORY_KEYS = new HashMap<>();

    static {
        CATEGORY_KEYS.put("root", ROOT);
        CATEGORY_KEYS.put("view", VIEW);
        CATEGORY_KEYS.put("layout", LAYOUT);
        CATEGORY_KEYS.put("variable", VARIABLE);
        CATEGORY_KEYS.put("control", CONTROL);
        CATEGORY_KEYS.put("command", COMMAND);
        CATEGORY_KEYS.put("commands", COMMAND);
        CATEGORY_KEYS.put("data_open", DATA_OPEN);
        CATEGORY_KEYS.put("animation", ANIMATION);
        CATEGORY_KEYS.put("effect", EFFECT);
        CATEGORY_KEYS.put("longtake", LONGTAKE);
        CATEGORY_KEYS.put("three_d", THREE_D);
        CATEGORY_KEYS.put("trigger", TRIGGER);
    }

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null
                || element.getNode().getElementType() != XmlElementType.XML_TAG) {
            return;
        }

        var tagNameNode = element.getFirstChild().getNextSibling();
        if (tagNameNode == null) {
            return;
        }
        
        var closingTagNameNode = ((XmlTag) element.getNode()).isEmpty()
                ? null : element.getLastChild().getPrevSibling();

        String tagNameText = tagNameNode.getText();
        if (tagNameText == null || tagNameText.isEmpty()) {
            return;
        }

        RuleRepository repo = RuleRepositoryService.getInstance().getRuleRepository();
        Optional<DslElementRule> ruleOpt = repo.getElementRule(tagNameText);
        if (ruleOpt.isEmpty()) {
            return;
        }

        String category = ruleOpt.get().getCategory();
        TextAttributesKey key = resolveKey(category);

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(tagNameNode)
                .textAttributes(key)
                .create();

        if (closingTagNameNode != null) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(closingTagNameNode)
                    .textAttributes(key)
                    .create();
        }
    }

    @Nullable
    private static TextAttributesKey resolveKey(@Nullable String category) {
        if (category == null || category.isEmpty()) {
            return UNKNOWN;
        }
        return CATEGORY_KEYS.getOrDefault(category, UNKNOWN);
    }
}
