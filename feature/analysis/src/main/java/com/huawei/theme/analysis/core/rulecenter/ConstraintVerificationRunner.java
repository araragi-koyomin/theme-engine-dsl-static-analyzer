package com.huawei.theme.analysis.core.rulecenter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.huawei.theme.analysis.core.rulecenter.model.ConstraintVerification;
import com.huawei.theme.analysis.core.rulecenter.model.ValidationFailure;
import com.huawei.theme.analysis.core.rulecenter.model.VerificationStatus;
import com.huawei.theme.analysis.core.rulelibrary.DefaultRuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.semanticanalysis.analyzers.ConstraintAnalyzer;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;

public class ConstraintVerificationRunner {
    private final StrictConditionAcceptor conditionAcceptor;
    private final ConstraintAnalyzer constraintAnalyzer;

    public ConstraintVerificationRunner(StrictConditionAcceptor conditionAcceptor) {
        this.conditionAcceptor = Objects.requireNonNull(conditionAcceptor);
        this.constraintAnalyzer = new ConstraintAnalyzer();
    }

    public ConstraintVerificationRunResult verify(ConstraintVerificationRequest request) {
        validateRequest(request);
        ConditionAcceptance acceptance = conditionAcceptor.accept(request.getConstraint().getCondition());
        if (!acceptance.isAccepted()) {
            throw new IllegalArgumentException(acceptance.getStatus().name());
        }

        RuleRepository ruleRepository = repositoryFor(request);
        FixtureAnalysis positive = analyzeFixture(
                request.getPositiveFixturePath(),
                request.getPositiveFixtureContent(),
                ruleRepository);
        FixtureAnalysis negative = analyzeFixture(
                request.getNegativeFixturePath(),
                request.getNegativeFixtureContent(),
                ruleRepository);

        if (!positive.parsed || !negative.parsed) {
            return failed(ValidationFailure.FIXTURE_PARSE_ERROR, positive.ruleIds, negative.ruleIds);
        }
        String ruleId = request.getConstraint().getRuleId();
        if (!positive.ruleIds.contains(ruleId)) {
            return failed(ValidationFailure.POSITIVE_FIXTURE_MISSED, positive.ruleIds, negative.ruleIds);
        }
        if (negative.ruleIds.contains(ruleId)) {
            return failed(ValidationFailure.NEGATIVE_FIXTURE_HIT, positive.ruleIds, negative.ruleIds);
        }

        ConstraintVerification verification = ConstraintVerification.builder()
                .ruleId(ruleId)
                .condition(request.getConstraint().getCondition())
                .parserAccepted(true)
                .positiveFixture(request.getPositiveFixturePath())
                .negativeFixture(request.getNegativeFixturePath())
                .positiveObservedRuleIds(positive.ruleIds)
                .negativeObservedRuleIds(negative.ruleIds)
                .evidenceCandidateIds(List.copyOf(request.getEvidenceCandidateIds()))
                .status(VerificationStatus.PASSED)
                .build();
        return ConstraintVerificationRunResult.builder()
                .passed(true)
                .verification(verification)
                .positiveObservedRuleIds(positive.ruleIds)
                .negativeObservedRuleIds(negative.ruleIds)
                .build();
    }

    private RuleRepository repositoryFor(ConstraintVerificationRequest request) {
        RuleConstraint constraint = request.getConstraint();
        DslElementRule elementRule = DslElementRule.builder()
                .elementName(request.getTargetElement())
                .constraints(List.of(constraint))
                .build();
        return new DefaultRuleRepository(
                Map.of(request.getTargetElement(), elementRule),
                Map.of(),
                Map.of());
    }

    private FixtureAnalysis analyzeFixture(String path, String content, RuleRepository ruleRepository) {
        DslFileNode ast = new AstBuilder(ruleRepository).getDslAst(path, content);
        DslElementNode root = ast.getRootElement();
        if (root == null || root.isHasError()) {
            return new FixtureAnalysis(false, List.of());
        }

        LinkedHashSet<String> observed = new LinkedHashSet<>();
        analyzeElement(root, ast, ruleRepository, observed);
        return new FixtureAnalysis(true, List.copyOf(observed));
    }

    private void analyzeElement(
            DslElementNode element,
            DslFileNode ast,
            RuleRepository ruleRepository,
            LinkedHashSet<String> observed) {
        List<Diagnostic> diagnostics = constraintAnalyzer.analyze(
                element,
                new DslContext(ruleRepository, null, ast.getFilePath(), ast));
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.getRuleId() != null) {
                observed.add(diagnostic.getRuleId());
            }
        }
        for (DslElementNode child : element.getChildElements()) {
            analyzeElement(child, ast, ruleRepository, observed);
        }
    }

    private ConstraintVerificationRunResult failed(
            ValidationFailure failure,
            List<String> positiveRuleIds,
            List<String> negativeRuleIds) {
        return ConstraintVerificationRunResult.builder()
                .passed(false)
                .failure(failure)
                .positiveObservedRuleIds(positiveRuleIds)
                .negativeObservedRuleIds(negativeRuleIds)
                .build();
    }

    private void validateRequest(ConstraintVerificationRequest request) {
        Objects.requireNonNull(request, "request");
        requireText(request.getTargetElement(), "targetElement");
        Objects.requireNonNull(request.getConstraint(), "constraint");
        requireText(request.getConstraint().getRuleId(), "constraint.ruleId");
        requireText(request.getConstraint().getCondition(), "constraint.condition");
        requireText(request.getPositiveFixturePath(), "positiveFixturePath");
        Objects.requireNonNull(request.getPositiveFixtureContent(), "positiveFixtureContent");
        requireText(request.getNegativeFixturePath(), "negativeFixturePath");
        Objects.requireNonNull(request.getNegativeFixtureContent(), "negativeFixtureContent");
        Objects.requireNonNull(request.getEvidenceCandidateIds(), "evidenceCandidateIds");
    }

    private void requireText(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
    }

    private static final class FixtureAnalysis {
        private final boolean parsed;
        private final List<String> ruleIds;

        private FixtureAnalysis(boolean parsed, List<String> ruleIds) {
            this.parsed = parsed;
            this.ruleIds = ruleIds;
        }
    }
}
