package com.huawei.theme.analysis.core.quickfix;

import java.util.ArrayList;
import java.util.List;

import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.model.FixActionType;

public final class SuggestedFixParser {

    private SuggestedFixParser() {}

    public static List<FixActionIntent> parse(List<SuggestedFix> suggestedFixes) {
        if (suggestedFixes == null) {
            return new ArrayList<>();
        }
        List<FixActionIntent> intents = new ArrayList<>();
        for (SuggestedFix fix : suggestedFixes) {
            if (fix == null) {
                continue;
            }
            FixActionType actionType = resolveType(fix.getType());
            if (actionType != null && actionType != FixActionType.UNKNOWN) {
                intents.add(FixActionIntent.builder()
                        .actionType(actionType)
                        .targetName(fix.getTarget())
                        .targetValue(fix.getValue() != null ? fix.getValue() : fix.getRange())
                        .description(fix.getText())
                        .build());
            }
        }
        return intents;
    }

    private static FixActionType resolveType(String typeStr) {
        if (typeStr == null || typeStr.isEmpty()) {
            return null;
        }
        try {
            return FixActionType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return FixActionType.UNKNOWN;
        }
    }
}
