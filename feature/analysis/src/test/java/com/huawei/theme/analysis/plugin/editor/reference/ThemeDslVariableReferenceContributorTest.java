package com.huawei.theme.analysis.plugin.editor.reference;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeDslVariableReferenceContributorTest {

    @Test
    void scanVariableRefsConsumesInterpolationAsPartOfName() {
        List<ThemeDslVariableReferenceContributor.VarRef> refs =
                ThemeDslVariableReferenceContributor.scanVariableRefs("#x_%{i} + #y + @z_%{j}_tail");
        assertEquals(3, refs.size());
        assertEquals("x_%{i}", refs.get(0).name());
        assertEquals("y", refs.get(1).name());
        assertEquals("z_%{j}_tail", refs.get(2).name());
    }

    @Test
    void scanVariableRefsTrailingDotTrimmed() {
        List<ThemeDslVariableReferenceContributor.VarRef> refs =
                ThemeDslVariableReferenceContributor.scanVariableRefs("#a.");
        assertEquals(1, refs.size());
        assertEquals("a", refs.get(0).name());
    }
}
