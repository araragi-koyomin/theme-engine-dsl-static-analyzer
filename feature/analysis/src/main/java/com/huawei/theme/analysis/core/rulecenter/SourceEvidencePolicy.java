package com.huawei.theme.analysis.core.rulecenter;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.huawei.theme.analysis.core.rulecenter.model.RuleCandidate;

final class SourceEvidencePolicy {
    private static final Pattern ATTRIBUTE_REFERENCE = Pattern.compile(
            "element\\.attrs\\[\\s*'([^']+)'\\s*]");
    private static final Pattern STRING_LITERAL = Pattern.compile("'([^']+)'");
    private static final Pattern NUMBER_LITERAL = Pattern.compile(
            "(?<![A-Za-z0-9_])-?\\d+(?:\\.\\d+)?(?![A-Za-z0-9_])");
    private static final Pattern NORMATIVE_LANGUAGE = Pattern.compile(
            "(?:必须|不得|不能|不允许|仅限|至少|至多|应当|应该|需要|禁止|范围|"
                    + "同时|不可|must|shall|should|required|cannot|not allowed|only|"
                    + "at least|at most|between|range)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RESOURCE_SUBJECT = Pattern.compile(
            "(?:文件|资源|视频|音频|媒体|图片|图像|路径|URL|file|resource|video|audio|"
                    + "media|image|path|url)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXTERNAL_FACT = Pattern.compile(
            "(?:文件.*存在|资源.*存在|路径.*存在|实际存在|不存在|格式|扩展名|后缀|大小|"
                    + "尺寸|时长|持续时间|分辨率|帧率|码率|秒|分钟|字节|KB|MB|GB|"
                    + "exists?|existence|format|extension|suffix|file size|duration|"
                    + "resolution|frame rate|bitrate|seconds?|minutes?|bytes?|kb|mb|gb)",
            Pattern.CASE_INSENSITIVE);

    boolean isExtractiveDescription(RuleCandidate candidate) {
        if (candidate.getProposedChange() == null
                || candidate.getProposedChange().getValue() == null
                || !candidate.getProposedChange().getValue().isJsonPrimitive()
                || !candidate.getProposedChange().getValue().getAsJsonPrimitive().isString()) {
            return false;
        }
        String evidence = normalized(candidate.getSourceEvidence().getExcerpt());
        String description = normalized(candidate.getProposedChange().getValue().getAsString());
        return !description.isEmpty() && evidence.contains(description);
    }

    boolean isExternalResourceSemantics(RuleCandidate candidate) {
        String evidence = candidate.getSourceEvidence().getExcerpt();
        return RESOURCE_SUBJECT.matcher(evidence).find()
                && EXTERNAL_FACT.matcher(evidence).find();
    }

    boolean supportsConstraint(RuleCandidate candidate, JsonObject draft) {
        String evidence = normalized(candidate.getSourceEvidence().getExcerpt());
        if (!NORMATIVE_LANGUAGE.matcher(evidence).find()) {
            return false;
        }
        String message = normalized(string(draft, "message"));
        if (message.isEmpty() || !evidence.contains(message)) {
            return false;
        }
        String condition = string(draft, "condition");
        Set<String> attributes = new HashSet<>();
        Matcher attributeMatcher = ATTRIBUTE_REFERENCE.matcher(condition);
        while (attributeMatcher.find()) {
            String attribute = attributeMatcher.group(1);
            attributes.add(attribute);
            if (!containsToken(evidence, attribute)) {
                return false;
            }
        }
        Matcher stringMatcher = STRING_LITERAL.matcher(condition);
        while (stringMatcher.find()) {
            String literal = stringMatcher.group(1);
            if (!attributes.contains(literal) && !containsToken(evidence, literal)) {
                return false;
            }
        }
        Matcher numberMatcher = NUMBER_LITERAL.matcher(condition);
        while (numberMatcher.find()) {
            if (!containsToken(evidence, numberMatcher.group())) {
                return false;
            }
        }
        return !attributes.isEmpty() || condition.contains("element.children");
    }

    private boolean containsToken(String evidence, String token) {
        return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(
                token.toLowerCase(Locale.ROOT)) + "(?![A-Za-z0-9_])")
                .matcher(evidence).find();
    }

    private String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace("`", "")
                .replaceAll("^[\\s>*+-]+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String string(JsonObject object, String field) {
        return object.has(field) && object.get(field).isJsonPrimitive()
                ? object.get(field).getAsString() : "";
    }
}
