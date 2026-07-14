package com.huawei.theme.analysis.core.quickfix;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.model.FixActionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestedFixParserTest {

    @Test
    void parseStructuredRemoveAttr() {
        List<SuggestedFix> fixes = List.of(
                SuggestedFix.builder().text("移除play属性").type("REMOVE_ATTR").target("play").build()
        );
        List<FixActionIntent> intents = SuggestedFixParser.parse(fixes);
        assertEquals(1, intents.size());
        assertEquals(FixActionType.REMOVE_ATTR, intents.get(0).getActionType());
        assertEquals("play", intents.get(0).getTargetName());
    }

    @Test
    void parseStructuredAddAttr() {
        List<SuggestedFix> fixes = List.of(
                SuggestedFix.builder().text("添加width属性").type("ADD_ATTR").target("width").build()
        );
        List<FixActionIntent> intents = SuggestedFixParser.parse(fixes);
        assertEquals(1, intents.size());
        assertEquals(FixActionType.ADD_ATTR, intents.get(0).getActionType());
        assertEquals("width", intents.get(0).getTargetName());
    }

    @Test
    void parseStructuredSetValue() {
        List<SuggestedFix> fixes = List.of(
                SuggestedFix.builder().text("设置scaleType=center_crop").type("SET_VALUE").target("scaleType").value("center_crop").build()
        );
        List<FixActionIntent> intents = SuggestedFixParser.parse(fixes);
        assertEquals(FixActionType.SET_VALUE, intents.get(0).getActionType());
        assertEquals("scaleType", intents.get(0).getTargetName());
        assertEquals("center_crop", intents.get(0).getTargetValue());
    }

    @Test
    void parseStructuredClampValueWithRange() {
        List<SuggestedFix> fixes = List.of(
                SuggestedFix.builder().text("设置alpha值在0-255范围内").type("CLAMP_VALUE").target("alpha").range("0-255").build()
        );
        List<FixActionIntent> intents = SuggestedFixParser.parse(fixes);
        assertEquals(FixActionType.CLAMP_VALUE, intents.get(0).getActionType());
        assertEquals("0-255", intents.get(0).getTargetValue());
    }

    @Test
    void parseStructuredClampValueWithValue() {
        List<SuggestedFix> fixes = List.of(
                SuggestedFix.builder().text("设置delay值不超过3000").type("CLAMP_VALUE").target("delay").value("3000").build()
        );
        List<FixActionIntent> intents = SuggestedFixParser.parse(fixes);
        assertEquals("3000", intents.get(0).getTargetValue());
    }

    @Test
    void parseUnknownTypeSkipped() {
        List<SuggestedFix> fixes = List.of(
                SuggestedFix.builder().text("将视频转换为mp4格式").type("UNKNOWN").build()
        );
        List<FixActionIntent> intents = SuggestedFixParser.parse(fixes);
        assertTrue(intents.isEmpty());
    }

    @Test
    void parseInvalidTypeStringSkipped() {
        List<SuggestedFix> fixes = List.of(
                SuggestedFix.builder().text("some text").type("INVALID_TYPE").build()
        );
        List<FixActionIntent> intents = SuggestedFixParser.parse(fixes);
        assertTrue(intents.isEmpty());
    }

    @Test
    void parseNullReturnsEmpty() {
        List<FixActionIntent> intents = SuggestedFixParser.parse(null);
        assertTrue(intents.isEmpty());
    }

    @Test
    void parseEmptyListReturnsEmpty() {
        List<FixActionIntent> intents = SuggestedFixParser.parse(Collections.emptyList());
        assertTrue(intents.isEmpty());
    }

    @Test
    void parseMultipleFixes() {
        List<SuggestedFix> fixes = List.of(
                SuggestedFix.builder().text("移除play属性").type("REMOVE_ATTR").target("play").build(),
                SuggestedFix.builder().text("移除sound属性").type("REMOVE_ATTR").target("sound").build()
        );
        List<FixActionIntent> intents = SuggestedFixParser.parse(fixes);
        assertEquals(2, intents.size());
        assertEquals(FixActionType.REMOVE_ATTR, intents.get(0).getActionType());
        assertEquals(FixActionType.REMOVE_ATTR, intents.get(1).getActionType());
    }
}
