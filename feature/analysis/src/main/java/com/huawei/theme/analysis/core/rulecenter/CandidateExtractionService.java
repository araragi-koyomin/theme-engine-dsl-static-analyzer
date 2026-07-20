package com.huawei.theme.analysis.core.rulecenter;

@FunctionalInterface
public interface CandidateExtractionService {
    CandidateExtractionResult extract(CandidateExtractionRequest request);
}
