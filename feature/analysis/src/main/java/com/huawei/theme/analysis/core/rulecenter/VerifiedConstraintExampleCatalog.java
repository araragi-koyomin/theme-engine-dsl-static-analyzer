package com.huawei.theme.analysis.core.rulecenter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.huawei.theme.analysis.core.rulecenter.model.ConstraintVerification;
import com.huawei.theme.analysis.core.rulecenter.model.VerificationStatus;

public class VerifiedConstraintExampleCatalog {
    private static final int MAX_RESULTS = 3;

    private final List<IndexedExample> examples;

    public VerifiedConstraintExampleCatalog(
            List<VerifiedConstraintExample> examples,
            StrictConditionAcceptor conditionAcceptor) {
        Objects.requireNonNull(examples, "examples");
        Objects.requireNonNull(conditionAcceptor, "conditionAcceptor");
        List<IndexedExample> accepted = new ArrayList<>();
        for (VerifiedConstraintExample example : examples) {
            if (!hasPassingVerification(example)) {
                continue;
            }
            ConditionAcceptance acceptance = conditionAcceptor.accept(example.getCondition());
            if (acceptance.isAccepted()) {
                accepted.add(new IndexedExample(example, acceptance));
            }
        }
        this.examples = List.copyOf(accepted);
    }

    public List<VerifiedConstraintExample> findSimilar(ConstraintExampleQuery query) {
        validateQuery(query);
        return examples.stream()
                .filter(indexed -> matches(indexed, query))
                .sorted(Comparator
                        .comparingInt((IndexedExample indexed) -> relevance(indexed.example, query))
                        .reversed()
                        .thenComparing(indexed -> indexed.example.getRuleId()))
                .limit(MAX_RESULTS)
                .map(indexed -> indexed.example)
                .toList();
    }

    private boolean hasPassingVerification(VerifiedConstraintExample example) {
        if (example == null
                || example.getEvidenceScope() != ConstraintEvidenceScope.DSL_TEXT_ONLY
                || example.getTargetKind() == null
                || example.getRelation() == null
                || example.getAttributes() == null
                || example.getRuleId() == null
                || example.getCondition() == null) {
            return false;
        }
        ConstraintVerification verification = example.getVerification();
        return verification != null
                && verification.getStatus() == VerificationStatus.PASSED
                && verification.isParserAccepted()
                && example.getRuleId().equals(verification.getRuleId())
                && example.getCondition().equals(verification.getCondition())
                && verification.getPositiveObservedRuleIds() != null
                && verification.getPositiveObservedRuleIds().contains(example.getRuleId())
                && verification.getNegativeObservedRuleIds() != null
                && !verification.getNegativeObservedRuleIds().contains(example.getRuleId());
    }

    private boolean matches(IndexedExample indexed, ConstraintExampleQuery query) {
        VerifiedConstraintExample example = indexed.example;
        return example.getTargetKind() == query.getTargetKind()
                && example.getRelation() == query.getRelation()
                && indexed.acceptance.getCapabilities().containsAll(query.getRequiredCapabilities());
    }

    private int relevance(VerifiedConstraintExample example, ConstraintExampleQuery query) {
        int score = Objects.equals(example.getTargetElement(), query.getTargetElement()) ? 100 : 0;
        if (example.getAttributes().equals(query.getAttributes())) {
            score += 20;
        } else if (!Collections.disjoint(example.getAttributes(), query.getAttributes())) {
            score += 5;
        }
        return score;
    }

    private void validateQuery(ConstraintExampleQuery query) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(query.getTargetKind(), "query.targetKind");
        Objects.requireNonNull(query.getAttributes(), "query.attributes");
        Objects.requireNonNull(query.getRelation(), "query.relation");
        Objects.requireNonNull(query.getRequiredCapabilities(), "query.requiredCapabilities");
    }

    private static final class IndexedExample {
        private final VerifiedConstraintExample example;
        private final ConditionAcceptance acceptance;

        private IndexedExample(
                VerifiedConstraintExample example,
                ConditionAcceptance acceptance) {
            this.example = example;
            this.acceptance = acceptance;
        }
    }
}
