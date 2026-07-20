package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulecenter.model.ConstraintVerification;
import com.huawei.theme.analysis.core.rulecenter.model.TargetKind;
import com.huawei.theme.analysis.core.rulecenter.model.VerificationStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifiedConstraintExampleCatalogTest {

    private static final String MUTEX =
            "element.attrs['src'] != null AND element.attrs['srcExp'] != null";
    private final StrictConditionAcceptor acceptor = new StrictConditionAcceptor(
            new ConditionCapabilityRegistry());

    @Test
    void returnsVerifiedSameTargetExampleBeforeReusableGenericPattern() {
        VerifiedConstraintExample generic = example(
                "SEM-TEXT-901", "Text", Set.of("text", "textExp"),
                ConstraintRelation.MUTUAL_EXCLUSION, ConstraintEvidenceScope.DSL_TEXT_ONLY, true,
                "element.attrs['text'] != null AND element.attrs['textExp'] != null");
        VerifiedConstraintExample sameTarget = example(
                "SEM-IMG-002", "Image", Set.of("src", "srcExp"),
                ConstraintRelation.MUTUAL_EXCLUSION, ConstraintEvidenceScope.DSL_TEXT_ONLY, true, MUTEX);
        VerifiedConstraintExampleCatalog catalog = catalog(generic, sameTarget);

        List<VerifiedConstraintExample> matches = catalog.findSimilar(
                ConstraintExampleQuery.builder()
                        .targetKind(TargetKind.ELEMENT_ATTRIBUTE)
                        .targetElement("Image")
                        .attributes(Set.of("src", "srcExp"))
                        .relation(ConstraintRelation.MUTUAL_EXCLUSION)
                        .requiredCapabilities(Set.of(ConditionCapability.BASE_GRAMMAR))
                        .build());

        assertEquals(List.of("SEM-IMG-002", "SEM-TEXT-901"), ruleIds(matches));
    }

    @Test
    void excludesExampleWithoutPassingVerificationRecord() {
        VerifiedConstraintExample unverified = example(
                "SEM-IMG-UNVERIFIED", "Image", Set.of("src", "srcExp"),
                ConstraintRelation.MUTUAL_EXCLUSION, ConstraintEvidenceScope.DSL_TEXT_ONLY, false, MUTEX);

        List<VerifiedConstraintExample> matches = catalog(unverified).findSimilar(query());

        assertTrue(matches.isEmpty());
    }

    @Test
    void excludesExternalResourceSemanticsEvenWithForgedPassingRecord() {
        VerifiedConstraintExample resourceSemantic = example(
                "SEM-VIDEO-RESOURCE", "Video", Set.of("src"),
                ConstraintRelation.ATTRIBUTE_VALUE,
                ConstraintEvidenceScope.EXTERNAL_RESOURCE, true,
                "element.attrs['src'] != null");

        List<VerifiedConstraintExample> matches = catalog(resourceSemantic).findSimilar(
                ConstraintExampleQuery.builder()
                        .targetKind(TargetKind.ELEMENT_ATTRIBUTE)
                        .targetElement("Video")
                        .attributes(Set.of("src"))
                        .relation(ConstraintRelation.ATTRIBUTE_VALUE)
                        .requiredCapabilities(Set.of(ConditionCapability.BASE_GRAMMAR))
                        .build());

        assertTrue(matches.isEmpty());
    }

    @Test
    void excludesMismatchedVerificationAndUnsupportedCondition() {
        VerifiedConstraintExample mismatched = example(
                "SEM-IMG-MISMATCH", "Image", Set.of("src", "srcExp"),
                ConstraintRelation.MUTUAL_EXCLUSION, ConstraintEvidenceScope.DSL_TEXT_ONLY, true, MUTEX);
        mismatched.getVerification().setCondition("element.attrs['other'] != null");
        VerifiedConstraintExample unsupported = example(
                "SEM-IMG-UNSUPPORTED", "Image", Set.of("src"),
                ConstraintRelation.ATTRIBUTE_VALUE, ConstraintEvidenceScope.DSL_TEXT_ONLY, true,
                "element.attrs['src'].endsWith('.mp4')");

        VerifiedConstraintExampleCatalog catalog = catalog(mismatched, unsupported);

        assertTrue(catalog.findSimilar(query()).isEmpty());
    }

    @Test
    void filtersByRelationAndCapabilitiesAndReturnsAtMostThree() {
        VerifiedConstraintExample[] examples = new VerifiedConstraintExample[5];
        for (int index = 0; index < examples.length; index++) {
            examples[index] = example(
                    "SEM-EXAMPLE-" + index, "Element" + index, Set.of("a", "b"),
                    ConstraintRelation.MUTUAL_EXCLUSION,
                    ConstraintEvidenceScope.DSL_TEXT_ONLY, true,
                    "element.attrs['a'] != null AND element.attrs['b'] != null");
        }
        VerifiedConstraintExampleCatalog catalog = new VerifiedConstraintExampleCatalog(
                List.of(examples), acceptor);

        List<VerifiedConstraintExample> matches = catalog.findSimilar(query());
        List<VerifiedConstraintExample> wrongRelation = catalog.findSimilar(
                ConstraintExampleQuery.builder()
                        .targetKind(TargetKind.ELEMENT_ATTRIBUTE)
                        .targetElement("Image")
                        .attributes(Set.of("src"))
                        .relation(ConstraintRelation.CHILD_COUNT)
                        .requiredCapabilities(Set.of(ConditionCapability.CHILDREN_TAG_COUNT))
                        .build());

        assertEquals(3, matches.size());
        assertTrue(wrongRelation.isEmpty());
    }

    private VerifiedConstraintExampleCatalog catalog(VerifiedConstraintExample... examples) {
        return new VerifiedConstraintExampleCatalog(List.of(examples), acceptor);
    }

    private ConstraintExampleQuery query() {
        return ConstraintExampleQuery.builder()
                .targetKind(TargetKind.ELEMENT_ATTRIBUTE)
                .targetElement("Image")
                .attributes(Set.of("src", "srcExp"))
                .relation(ConstraintRelation.MUTUAL_EXCLUSION)
                .requiredCapabilities(Set.of(ConditionCapability.BASE_GRAMMAR))
                .build();
    }

    private VerifiedConstraintExample example(
            String ruleId,
            String element,
            Set<String> attributes,
            ConstraintRelation relation,
            ConstraintEvidenceScope evidenceScope,
            boolean verified,
            String condition) {
        ConstraintVerification verification = verified
                ? ConstraintVerification.builder()
                        .ruleId(ruleId)
                        .condition(condition)
                        .parserAccepted(true)
                        .positiveFixture("fixtures/" + ruleId + "/positive.xml")
                        .negativeFixture("fixtures/" + ruleId + "/negative.xml")
                        .positiveObservedRuleIds(List.of(ruleId))
                        .negativeObservedRuleIds(List.of())
                        .evidenceCandidateIds(List.of("candidate-" + ruleId))
                        .status(VerificationStatus.PASSED)
                        .build()
                : null;
        return VerifiedConstraintExample.builder()
                .ruleId(ruleId)
                .targetKind(TargetKind.ELEMENT_ATTRIBUTE)
                .targetElement(element)
                .attributes(attributes)
                .relation(relation)
                .evidenceScope(evidenceScope)
                .condition(condition)
                .verification(verification)
                .build();
    }

    private List<String> ruleIds(List<VerifiedConstraintExample> examples) {
        return examples.stream().map(VerifiedConstraintExample::getRuleId).toList();
    }
}
