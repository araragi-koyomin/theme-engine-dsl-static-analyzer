package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleCenterPublishWorkflowContractTest {
    private final Path repositoryRoot = Path.of(System.getProperty("user.dir"))
            .resolve("../..")
            .normalize();

    @Test
    void mainPublicationRerunsValidationBehindProductionEnvironmentApproval()
            throws IOException {
        String workflow = read(".github/workflows/publish-rule-package.yml");

        assertTrue(workflow.contains("push:"));
        assertTrue(workflow.contains("branches: [main]"));
        assertTrue(workflow.contains("environment: dsl-rule-production"));
        assertTrue(workflow.contains("contents: write"));
        assertTrue(workflow.contains("models: read"));
        assertTrue(workflow.contains("./gradlew --no-daemon ruleCenterValidateDocument"));
        assertTrue(workflow.contains("./gradlew --no-daemon ruleCenterExtractBaseline"));
        assertTrue(workflow.contains("./gradlew --no-daemon ruleCenterPrepareRelease"));
    }

    @Test
    void releaseIsCreatedOnlyAfterGateWithExactlyThreeFixedAssets() throws IOException {
        String workflow = read(".github/workflows/publish-rule-package.yml");

        assertTrue(workflow.contains("gh release create"));
        assertTrue(workflow.contains("rules-v${RULE_CENTER_PACKAGE_VERSION}"));
        assertTrue(workflow.contains("rule-package.zip"));
        assertTrue(workflow.contains("manifest.json"));
        assertTrue(workflow.contains("release-report.json"));
        assertTrue(workflow.contains("--notes-file"));
        assertFalse(workflow.contains("--draft"));
        assertFalse(workflow.contains("--prerelease"));
        assertFalse(workflow.contains("actions/download-artifact"));
        assertTrue(workflow.indexOf("ruleCenterPrepareRelease")
                < workflow.indexOf("gh release create"));
    }

    @Test
    void previousApprovedPackageIsOptionalBaselineAndChangedDocsAreExplicit()
            throws IOException {
        String workflow = read(".github/workflows/publish-rule-package.yml");

        assertTrue(workflow.contains("gh release download"));
        assertTrue(workflow.contains("RULE_CENTER_BASELINE_RULES"));
        assertTrue(workflow.contains("RULE_CENTER_DOCUMENT_LIST"));
        assertTrue(workflow.contains("--diff-filter=AM"));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(repositoryRoot.resolve(relativePath));
    }
}
