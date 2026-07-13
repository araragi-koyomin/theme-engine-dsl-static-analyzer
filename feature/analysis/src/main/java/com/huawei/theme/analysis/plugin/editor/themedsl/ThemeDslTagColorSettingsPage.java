package com.huawei.theme.analysis.plugin.editor.themedsl;

import java.util.HashMap;
import java.util.Map;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lexer.XmlLexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;

/**
 * Color settings page for ThemeDSL tag categories.
 *
 * <p>Registered via {@code <colorSettingsPage>} EP. Shows up under
 * {@code Editor > Color Scheme > ThemeDSL Tags}.</p>
 */
public class ThemeDslTagColorSettingsPage implements ColorSettingsPage {

    private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
            new AttributesDescriptor("Root tags (Lockscreen, Wallpaper…)", ThemeDslTagCategoryAnnotator.ROOT),
            new AttributesDescriptor("View tags (Image, Text, Video…)", ThemeDslTagCategoryAnnotator.VIEW),
            new AttributesDescriptor("Layout tags (Group…)", ThemeDslTagCategoryAnnotator.LAYOUT),
            new AttributesDescriptor("Variable tags (Var, Array…)", ThemeDslTagCategoryAnnotator.VARIABLE),
            new AttributesDescriptor("Control tags (Button, Slider…)", ThemeDslTagCategoryAnnotator.CONTROL),
            new AttributesDescriptor("Command tags (Command, SoundCommand…)", ThemeDslTagCategoryAnnotator.COMMAND),
            new AttributesDescriptor("Data tags (Calendar, Weather…)", ThemeDslTagCategoryAnnotator.DATA_OPEN),
            new AttributesDescriptor("Animation tags (AlphaAnimation, PositionAnimation…)", ThemeDslTagCategoryAnnotator.ANIMATION),
            new AttributesDescriptor("Effect tags (Particles, ScreenFlash…)", ThemeDslTagCategoryAnnotator.EFFECT),
            new AttributesDescriptor("LongTake tags (LongTake, AodClock…)", ThemeDslTagCategoryAnnotator.LONGTAKE),
            new AttributesDescriptor("3D tags (StereoView, Layer…)", ThemeDslTagCategoryAnnotator.THREE_D),
            new AttributesDescriptor("Trigger tags (Trigger)", ThemeDslTagCategoryAnnotator.TRIGGER),
            new AttributesDescriptor("Unknown category", ThemeDslTagCategoryAnnotator.UNKNOWN),
    };

    private static final String DEMO_TEXT =
            "<root>Lockscreen</root>\n" +
            "<view>Image</view>\n" +
            "<layout>Group</layout>\n" +
            "<variable>Var</variable>\n" +
            "<control>Button</control>\n" +
            "<commands>Command</commands>\n" +
            "<data_open>Calendar</data_open>\n" +
            "<animation>AlphaAnimation</animation>\n" +
            "<effect>Particles</effect>\n" +
            "<longtake>LongTake</longtake>\n" +
            "<three_d>StereoView</three_d>\n" +
            "<trigger>Trigger</trigger>\n" +
            "<unknown>CustomTag</unknown>";

    private static final Map<String, TextAttributesKey> TAG_MAP = new HashMap<>();

    static {
        TAG_MAP.put("root", ThemeDslTagCategoryAnnotator.ROOT);
        TAG_MAP.put("view", ThemeDslTagCategoryAnnotator.VIEW);
        TAG_MAP.put("layout", ThemeDslTagCategoryAnnotator.LAYOUT);
        TAG_MAP.put("variable", ThemeDslTagCategoryAnnotator.VARIABLE);
        TAG_MAP.put("control", ThemeDslTagCategoryAnnotator.CONTROL);
        TAG_MAP.put("commands", ThemeDslTagCategoryAnnotator.COMMAND);
        TAG_MAP.put("data_open", ThemeDslTagCategoryAnnotator.DATA_OPEN);
        TAG_MAP.put("animation", ThemeDslTagCategoryAnnotator.ANIMATION);
        TAG_MAP.put("effect", ThemeDslTagCategoryAnnotator.EFFECT);
        TAG_MAP.put("longtake", ThemeDslTagCategoryAnnotator.LONGTAKE);
        TAG_MAP.put("three_d", ThemeDslTagCategoryAnnotator.THREE_D);
        TAG_MAP.put("trigger", ThemeDslTagCategoryAnnotator.TRIGGER);
        TAG_MAP.put("unknown", ThemeDslTagCategoryAnnotator.UNKNOWN);
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return null;
    }

    @NotNull
    @Override
    public SyntaxHighlighter getHighlighter() {
        return new SyntaxHighlighterBase() {
            @Override
            public com.intellij.lexer.Lexer getHighlightingLexer() {
                return new XmlLexer();
            }
            @Override
            public TextAttributesKey @NotNull [] getTokenHighlights(com.intellij.psi.tree.IElementType tokenType) {
                return TextAttributesKey.EMPTY_ARRAY;
            }
        };
    }

    @NotNull
    @Override
    public String getDemoText() {
        return DEMO_TEXT;
    }

    @Nullable
    @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return TAG_MAP;
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
        return "ThemeDSL Tags";
    }
}
