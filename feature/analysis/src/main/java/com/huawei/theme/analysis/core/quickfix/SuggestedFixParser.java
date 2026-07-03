package com.huawei.theme.analysis.core.quickfix;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.model.FixActionType;

public final class SuggestedFixParser {

    private static final Pattern REMOVE_ATTR = Pattern.compile("移除(.+)属性");
    private static final Pattern REMOVE_CHILD = Pattern.compile("移除(.+)子标签");
    private static final Pattern ADD_ATTR_TO = Pattern.compile("为(.+)添加(.+)属性");
    private static final Pattern ADD_ATTR = Pattern.compile("添加(.+)属性(.*)");
    private static final Pattern ADD_CHILD = Pattern.compile("添加(.+)(子元素|子标签)");
    private static final Pattern ADD_DECLARATION = Pattern.compile("添加<(.+?)(/?)>声明");
    private static final Pattern SET_VALUE_EQ = Pattern.compile("设置(\\w+)=(.+)");
    private static final Pattern SET_VALUE_RANGE = Pattern.compile("设置(.+)值在(.+)范围内");
    private static final Pattern SET_VALUE_LIMIT = Pattern.compile("设置(.+)值不超过(.+)");
    private static final Pattern SET_VALUE_TO = Pattern.compile("设置(.+)为(.+)");
    private static final Pattern MODIFY_TO_ENUM = Pattern.compile("修改(.+)为合法枚举值");
    private static final Pattern MODIFY_NAME_FORMAT = Pattern.compile("修改(.+)的name属性为(.+)等标准格式");
    private static final Pattern MODIFY_TO = Pattern.compile("修改(.+)为(.+)");
    private static final Pattern CHANGE_TO = Pattern.compile("将(.+)改为(.+)");
    private static final Pattern CHANGE_VALUE = Pattern.compile("更改(.+)为(.+)");
    private static final Pattern ADJUST_RANGE = Pattern.compile("将(.+)调整到(.+)范围内");
    private static final Pattern ADJUST_TO = Pattern.compile("将(.+)设置为(.+)");
    private static final Pattern CONVERT_TO = Pattern.compile("将(.+)转换为(.+)");
    private static final Pattern MOVE_TO = Pattern.compile("将(.+)移至(.+)");
    private static final Pattern REDUCE_TO = Pattern.compile("(减小|减少|压缩)(.+)(至|值)(.+)");
    private static final Pattern USE_ALT_WITH = Pattern.compile("使用(.+?)替代(.+)");
    private static final Pattern USE_ALT = Pattern.compile("使用(.+?)(替代)?$");
    private static final Pattern DELETE_NODE = Pattern.compile("删除(.+)节点");
    private static final Pattern CONFIRM = Pattern.compile("确认(.+)");
    private static final Pattern DECLARE_OUTSIDE = Pattern.compile("在(.+)外声明<(.+)>.*");

    private SuggestedFixParser() {}

    public static List<FixActionIntent> parseTexts(List<String> suggestedFixes) {
        if (suggestedFixes == null) {
            return new ArrayList<>();
        }
        List<FixActionIntent> intents = new ArrayList<>();
        for (String text : suggestedFixes) {
            if (text != null && !text.isEmpty()) {
                intents.add(parseSingle(text.trim()));
            }
        }
        return intents;
    }

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
            } else {
                intents.add(parseSingle(fix.getText()));
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

    public static FixActionIntent parseSingle(String text) {
        Matcher m;
        m = REMOVE_ATTR.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.REMOVE_ATTR)
                    .targetName(m.group(1))
                    .build();
        }
        m = REMOVE_CHILD.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.REMOVE_CHILD)
                    .targetName(m.group(1))
                    .build();
        }
        m = ADD_ATTR_TO.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.ADD_ATTR)
                    .targetName(m.group(2))
                    .build();
        }
        m = ADD_ATTR.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.ADD_ATTR)
                    .targetName(extractAttrName(m.group(1)))
                    .targetValue(m.group(2))
                    .build();
        }
        m = ADD_CHILD.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.ADD_CHILD)
                    .targetName(m.group(1))
                    .build();
        }
        m = ADD_DECLARATION.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.ADD_DECLARATION)
                    .targetName(m.group(1))
                    .build();
        }
        m = SET_VALUE_EQ.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.SET_VALUE)
                    .targetName(m.group(1))
                    .targetValue(m.group(2))
                    .build();
        }
        m = SET_VALUE_RANGE.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.CLAMP_VALUE)
                    .targetName(extractAttrName(m.group(1)))
                    .targetValue(m.group(2))
                    .build();
        }
        m = SET_VALUE_LIMIT.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.CLAMP_VALUE)
                    .targetName(extractAttrName(m.group(1)))
                    .targetValue(m.group(2))
                    .build();
        }
        m = SET_VALUE_TO.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.SET_VALUE)
                    .targetName(extractAttrName(m.group(1)))
                    .targetValue(m.group(2))
                    .build();
        }
        m = MODIFY_TO_ENUM.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.REPLACE_ENUM)
                    .targetName(m.group(1))
                    .build();
        }
        m = MODIFY_NAME_FORMAT.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.MODIFY_NAME_FORMAT)
                    .targetName(m.group(1))
                    .targetValue(m.group(2))
                    .build();
        }
        m = MODIFY_TO.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.REPLACE_VALUE)
                    .targetName(m.group(1))
                    .targetValue(m.group(2))
                    .build();
        }
        m = CHANGE_TO.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.REPLACE_VALUE)
                    .targetName(m.group(1))
                    .targetValue(m.group(2))
                    .build();
        }
        m = CHANGE_VALUE.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.REPLACE_VALUE)
                    .targetName(m.group(1))
                    .targetValue(stripParenthetical(m.group(2)))
                    .build();
        }
        m = ADJUST_RANGE.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.CLAMP_VALUE)
                    .targetName(m.group(1))
                    .targetValue(m.group(2))
                    .build();
        }
        m = ADJUST_TO.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.SET_VALUE)
                    .targetName(m.group(1))
                    .targetValue(m.group(2))
                    .build();
        }
        m = CONVERT_TO.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.REPLACE_VALUE)
                    .targetName(m.group(1))
                    .targetValue(m.group(2))
                    .build();
        }
        m = MOVE_TO.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.MOVE_ELEMENT)
                    .targetName(m.group(1))
                    .targetValue(m.group(2))
                    .build();
        }
        m = REDUCE_TO.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.REDUCE_VALUE)
                    .targetName(m.group(2))
                    .targetValue(m.group(4))
                    .build();
        }
        m = USE_ALT_WITH.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.USE_ALTERNATIVE)
                    .targetName(m.group(1))
                    .targetValue(m.group(2))
                    .build();
        }
        m = USE_ALT.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.USE_ALTERNATIVE)
                    .targetName(m.group(1))
                    .build();
        }
        m = DELETE_NODE.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.DELETE_NODE)
                    .targetName(m.group(1))
                    .build();
        }
        m = CONFIRM.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.CONFIRM)
                    .targetName(m.group(1))
                    .build();
        }
        m = DECLARE_OUTSIDE.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionType.DECLARE_OUTSIDE)
                    .targetName(m.group(2))
                    .targetValue(m.group(1))
                    .build();
        }
        return unknownIntent(text);
    }

    private static String extractAttrName(String raw) {
        if (raw.contains("指定")) {
            return raw.substring(0, raw.indexOf("指定"));
        }
        return raw;
    }

    private static String stripParenthetical(String value) {
        return value.replaceAll("\\([^)]*\\)", "");
    }

    private static FixActionIntent unknownIntent(String text) {
        return FixActionIntent.builder()
                .actionType(FixActionType.UNKNOWN)
                .description(text)
                .build();
    }
}
