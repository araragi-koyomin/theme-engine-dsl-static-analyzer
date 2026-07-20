package com.huawei.theme.analysis.core.rulecenter;

import java.nio.file.Path;
import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RulePackageAssemblyResult {
    Path packageDirectory;
    ReleaseReportStatus status;
    String contentSha256;
    List<String> errors;
}
