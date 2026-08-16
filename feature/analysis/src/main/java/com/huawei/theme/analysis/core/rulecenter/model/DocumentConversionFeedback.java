package com.huawei.theme.analysis.core.rulecenter.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentConversionFeedback {
    String documentId;
    String documentRevision;
    ConversionStatus conversionStatus;
    String releaseVersion;
    FeedbackSummary summary;
    List<FeedbackItem> items;

    @Data
    @Builder
    public static class FeedbackSummary {
        int published;
        int descriptionOnly;
        int skipped;
        int validationErrors;
        int carriedForward;
    }

    @Data
    @Builder
    public static class FeedbackItem {
        RuleCandidate.SourceEvidence sourceEvidence;
        FeedbackOutcome outcome;
        FeedbackReasonCode reasonCode;
        AuthorAction authorAction;
        boolean previousRuleRetained;
    }
}
