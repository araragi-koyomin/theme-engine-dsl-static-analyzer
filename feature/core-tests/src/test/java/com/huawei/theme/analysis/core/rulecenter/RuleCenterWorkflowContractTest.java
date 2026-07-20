package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleCenterWorkflowContractTest {

    private final Path repositoryRoot = Path.of(System.getProperty("user.dir"))
            .resolve("../..")
            .normalize();

    @Test
    void gradleWrapperUsesPortableOfficialDistribution() throws IOException {
        String properties = read("gradle/wrapper/gradle-wrapper.properties");

        assertTrue(properties.contains(
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.2-bin.zip"));
        assertFalse(properties.contains("file\\:///"));
        assertFalse(properties.contains("C\\:/Users"));
    }

    @Test
    void validationWorkflowHasModelsPermissionAndProducesAuthorFeedback() throws IOException {
        String workflow = read(".github/workflows/validate-document.yml");

        assertTrue(workflow.contains("pull_request_target:"));
        assertFalse(workflow.contains("\n  pull_request:\n"));
        assertTrue(workflow.contains("models: read"));
        assertTrue(workflow.contains("pull-requests: write"));
        assertTrue(workflow.contains("./gradlew --no-daemon ruleCenterValidateDocument"));
        assertTrue(workflow.contains("GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}"));
        assertTrue(workflow.contains("feedback.json"));
        assertTrue(workflow.contains("release-report.json"));
        assertTrue(workflow.contains("actions/github-script@"));
        assertTrue(workflow.contains("actions/upload-artifact@"));
        assertFalse(workflow.contains("gh release create"));
        Map<?, ?> parsed = new Yaml().load(workflow);
        assertTrue(parsed.get("jobs") instanceof Map);
    }

    @Test
    void pullRequestRunsOnlyTrustedBaseCodeAndTreatsHeadCheckoutAsDocumentData()
            throws IOException {
        String workflow = read(".github/workflows/validate-document.yml");

        assertTrue(workflow.contains("ref: ${{ github.event.pull_request.base.sha }}"));
        assertTrue(workflow.contains("path: trusted"));
        assertTrue(workflow.contains("ref: ${{ github.event.pull_request.head.sha }}"));
        assertTrue(workflow.contains("path: proposal"));
        assertTrue(workflow.contains("sparse-checkout: rule-center/docs"));
        assertTrue(workflow.contains("persist-credentials: false"));
        assertTrue(workflow.contains("working-directory: trusted"));
        assertTrue(workflow.contains("RULE_CENTER_DOCUMENT_ROOT:"));
        assertTrue(workflow.contains("proposal/rule-center/docs"));
        assertFalse(workflow.matches("(?s).*uses: actions/checkout@v[0-9].*"));
    }

    @Test
    void validationWorkflowRunsOnGitHubHostedJava17WithoutLocalMachinePaths()
            throws IOException {
        String workflow = read(".github/workflows/validate-document.yml");

        assertTrue(workflow.contains("runs-on: ubuntu-latest"));
        assertTrue(workflow.contains("distribution: temurin"));
        assertTrue(workflow.contains("java-version: '17'"));
        assertFalse(workflow.contains("C:\\"));
        assertFalse(workflow.contains("Downloads/gradle"));
    }

    @Test
    void workflowCommandIsBackedByARealBatchOrchestratorAndAuditArtifacts()
            throws IOException {
        String build = read("build.gradle");
        String main = read("feature/analysis/src/main/java/com/huawei/theme/analysis/"
                + "core/rulecenter/RuleCenterWorkflowMain.java");

        assertTrue(build.contains("tasks.register('ruleCenterValidateDocument', JavaExec)"));
        assertTrue(build.contains("RuleCenterWorkflowMain"));
        assertTrue(main.contains("validateBatch"));
        assertTrue(main.contains("candidates.json"));
        assertTrue(main.contains("feedback.json"));
        assertTrue(main.contains("release-report.json"));
        assertTrue(main.contains("audit.json"));
        assertTrue(main.contains("feedback-summary.md"));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(repositoryRoot.resolve(relativePath));
    }
}
