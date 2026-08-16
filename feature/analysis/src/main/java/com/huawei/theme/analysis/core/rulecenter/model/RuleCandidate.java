package com.huawei.theme.analysis.core.rulecenter.model;

import java.util.List;

import com.google.gson.JsonElement;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RuleCandidate {
    String candidateId;
    String documentId;
    String documentRevision;
    SourceEvidence sourceEvidence;
    CandidateTarget target;
    ProposedKind proposedKind;
    ProposedChange proposedChange;
    CandidateStatus status;
    SkipReason skipReason;
    ValidationFailure validationFailure;

    @Data
    @Builder
    public static class SourceEvidence {
        List<String> sectionPath;
        SourceLocation location;
        String excerpt;
    }

    @Data
    @Builder
    public static class SourceLocation {
        int startLine;
        int endLine;
    }

    @Data
    @Builder
    public static class CandidateTarget {
        TargetKind kind;
        String element;
        String attribute;
    }

    @Data
    @Builder
    public static class ProposedChange {
        String field;
        JsonElement value;
    }
}
