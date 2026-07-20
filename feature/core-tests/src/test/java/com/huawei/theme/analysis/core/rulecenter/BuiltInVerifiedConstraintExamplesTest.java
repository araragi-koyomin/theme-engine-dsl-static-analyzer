package com.huawei.theme.analysis.core.rulecenter;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInVerifiedConstraintExamplesTest {

    @Test
    void exposesOnlyExamplesThatPassTheRealStaticAnalyzer() {
        Path rules = Path.of(System.getProperty("user.dir"))
                .resolve("../analysis/src/main/resources/rules")
                .normalize();
        StrictConditionAcceptor acceptor = new StrictConditionAcceptor(
                new ConditionCapabilityRegistry());

        List<VerifiedConstraintExample> examples = BuiltInVerifiedConstraintExamples.load(
                rules, new ConstraintVerificationRunner(acceptor));

        assertTrue(examples.size() >= 3);
        assertTrue(examples.stream().anyMatch(example -> "SEM-IMG-002"
                .equals(example.getRuleId())));
        assertTrue(examples.stream().allMatch(example -> example.getVerification() != null
                && example.getVerification().isParserAccepted()
                && example.getVerification().getPositiveObservedRuleIds()
                        .contains(example.getRuleId())
                && !example.getVerification().getNegativeObservedRuleIds()
                        .contains(example.getRuleId())));
    }
}
