package com.huawei.theme.analysis.core.rulecenter;

import com.huawei.theme.analysis.core.rulecenter.model.DocumentConversionFeedback;

@FunctionalInterface
public interface DocumentFeedbackPublisher {
    void publish(DocumentConversionFeedback feedback);
}
