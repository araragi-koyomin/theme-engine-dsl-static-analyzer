package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.huawei.theme.analysis.core.rulecenter.model.TargetKind;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

public final class BuiltInVerifiedConstraintExamples {
    private static final Map<String, FixturePair> IMAGE_FIXTURES = Map.of(
            "SEM-IMG-002", new FixturePair(
                    "<Image src=\"a.png\" srcExp=\"#source\"/>",
                    "<Image src=\"a.png\"/>",
                    Set.of("src", "srcExp"), ConstraintRelation.MUTUAL_EXCLUSION),
            "SEM-IMG-SRC", new FixturePair(
                    "<Image/>",
                    "<Image src=\"a.png\"/>",
                    Set.of("src", "srcExp"), ConstraintRelation.REQUIRED_TOGETHER),
            "SEM-ATTR-001", new FixturePair(
                    "<Image alpha=\"-1\"/>",
                    "<Image alpha=\"100\"/>",
                    Set.of("alpha"), ConstraintRelation.ATTRIBUTE_VALUE),
            "SEM-ATTR-005", new FixturePair(
                    "<Image isBackground=\"true\" scaleType=\"fitCenter\"/>",
                    "<Image isBackground=\"true\" scaleType=\"center_crop\"/>",
                    Set.of("isBackground", "scaleType"), ConstraintRelation.ATTRIBUTE_VALUE));

    private BuiltInVerifiedConstraintExamples() {
    }

    public static List<VerifiedConstraintExample> load(
            Path rulesDirectory,
            ConstraintVerificationRunner verificationRunner) {
        Path imageRule = rulesDirectory.resolve("elements/view/Image.json");
        if (!Files.isRegularFile(imageRule)) {
            return List.of();
        }
        try {
            JsonObject image = JsonParser.parseString(Files.readString(imageRule)).getAsJsonObject();
            JsonArray constraints = image.getAsJsonArray("constraints");
            List<VerifiedConstraintExample> examples = new ArrayList<>();
            for (var item : constraints) {
                JsonObject json = item.getAsJsonObject();
                String ruleId = json.get("ruleId").getAsString();
                FixturePair fixtures = IMAGE_FIXTURES.get(ruleId);
                if (fixtures == null) {
                    continue;
                }
                RuleConstraint constraint = RuleConstraint.builder()
                        .ruleId(ruleId)
                        .condition(json.get("condition").getAsString())
                        .message(json.get("message").getAsString())
                        .severity(DiagnosticSeverity.valueOf(
                                json.get("severity").getAsString().toUpperCase(Locale.ROOT)))
                        .build();
                ConstraintVerificationRunResult result = verificationRunner.verify(
                        ConstraintVerificationRequest.builder()
                                .targetElement("Image")
                                .constraint(constraint)
                                .positiveFixturePath(ruleId + "-positive.xml")
                                .positiveFixtureContent(fixtures.positive)
                                .negativeFixturePath(ruleId + "-negative.xml")
                                .negativeFixtureContent(fixtures.negative)
                                .evidenceCandidateIds(List.of("built-in:" + ruleId))
                                .build());
                if (result.isPassed()) {
                    examples.add(VerifiedConstraintExample.builder()
                            .ruleId(ruleId)
                            .targetKind(TargetKind.ELEMENT_ATTRIBUTE)
                            .targetElement("Image")
                            .attributes(fixtures.attributes)
                            .relation(fixtures.relation)
                            .evidenceScope(ConstraintEvidenceScope.DSL_TEXT_ONLY)
                            .condition(constraint.getCondition())
                            .verification(result.getVerification())
                            .build());
                }
            }
            return List.copyOf(examples);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load built-in verified examples", exception);
        }
    }

    private record FixturePair(
            String positive,
            String negative,
            Set<String> attributes,
            ConstraintRelation relation) {
    }
}
