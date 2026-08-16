package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        Map<String, Object> root = yaml(workflow);
        Map<String, Object> triggers = mapping(root, "on");
        Map<String, Object> pullRequestTarget = mapping(triggers, "pull_request_target");
        Map<String, Object> job = job(workflow, "validate-pull-request");
        Map<String, Object> base = step(job, "Check out trusted base implementation");
        Map<String, Object> proposal = step(
                job, "Check out proposed Markdown as untrusted data only");
        Map<String, Object> execute = step(
                job, "Extract, validate, repair, and assemble report with trusted code");

        assertEquals(List.of("main"), pullRequestTarget.get("branches"));
        assertEquals("github.event_name == 'pull_request_target'"
                        + " && github.event.pull_request.base.ref == 'main'",
                job.get("if"));
        assertEquals("actions/checkout@11d5960a326750d5838078e36cf38b85af677262",
                base.get("uses"));
        assertEquals("${{ github.event.pull_request.base.sha }}", with(base).get("ref"));
        assertEquals("trusted", with(base).get("path"));
        assertEquals("${{ github.event.pull_request.head.repo.full_name }}",
                with(proposal).get("repository"));
        assertEquals("${{ github.event.pull_request.head.sha }}",
                with(proposal).get("ref"));
        assertEquals("proposal", with(proposal).get("path"));
        assertEquals("rule-center/docs", with(proposal).get("sparse-checkout"));
        assertEquals(false, with(proposal).get("persist-credentials"));
        assertEquals(true, with(proposal).get("allow-unsafe-pr-checkout"));
        assertEquals("trusted", execute.get("working-directory"));
        assertEquals("${{ github.workspace }}/proposal/rule-center/docs",
                env(execute).get("RULE_CENTER_DOCUMENT_ROOT"));
        assertEquals("./gradlew --no-daemon ruleCenterValidateDocument", execute.get("run"));
        assertFalse(workflow.matches("(?s).*uses: actions/checkout@v[0-9].*"));
    }

    @Test
    void workflowScopesWritePermissionToPullRequestJob() throws IOException {
        Map<String, Object> root = yaml(read(".github/workflows/validate-document.yml"));
        Map<String, Object> pullRequestJob = job(root, "validate-pull-request");
        Map<String, Object> manualJob = job(root, "validate-manual");

        assertEquals(null, root.get("permissions"));
        assertEquals("write", mapping(pullRequestJob, "permissions").get("pull-requests"));
        assertEquals("read", mapping(pullRequestJob, "permissions").get("contents"));
        assertEquals("read", mapping(pullRequestJob, "permissions").get("models"));
        assertEquals(Map.of("contents", "read", "models", "read"),
                mapping(manualJob, "permissions"));
    }

    @Test
    void everyExecutablePullRequestStepStaysOutsideUntrustedCheckout() throws IOException {
        Map<String, Object> pullRequestJob = job(
                yaml(read(".github/workflows/validate-document.yml")),
                "validate-pull-request");
        Set<String> allowedRunSteps = Set.of(
                "Resolve changed Markdown data through the GitHub API",
                "Extract, validate, repair, and assemble report with trusted code");
        String expectedResolver = """
                set -euo pipefail
                mkdir -p trusted/build/rule-center
                gh api --paginate "repos/${GITHUB_REPOSITORY}/pulls/${PR_NUMBER}/files?per_page=100" <CONT>
                  --jq '.[] | select(.status != "removed") | .filename' <CONT>
                  > trusted/build/rule-center/changed-files.txt
                while IFS= read -r file; do
                  case "$file" in
                    rule-center/docs/*.md)
                      printf '%s/proposal/%s\\n' "$GITHUB_WORKSPACE" "$file" <CONT>
                        >> trusted/build/rule-center/documents.txt
                      ;;
                  esac
                done < trusted/build/rule-center/changed-files.txt
                if [[ ! -s trusted/build/rule-center/documents.txt ]]; then
                  echo "No added or modified rule Markdown documents were found" >&2
                  exit 1
                fi
                """.replace("<CONT>", "\\");

        for (Map<String, Object> executable : steps(pullRequestJob)) {
            String name = String.valueOf(executable.get("name"));
            if (executable.containsKey("run")) {
                assertTrue(allowedRunSteps.contains(name), name);
                Object workingDirectory = executable.get("working-directory");
                assertFalse("proposal".equals(workingDirectory), name);
                assertFalse(String.valueOf(workingDirectory).contains("/proposal"), name);
            }
            if (executable.containsKey("uses")) {
                String uses = String.valueOf(executable.get("uses"));
                assertFalse(uses.startsWith("./proposal"), name);
                assertFalse(uses.startsWith("proposal/"), name);
            }
        }
        assertEquals(expectedResolver.strip(), String.valueOf(step(
                pullRequestJob,
                "Resolve changed Markdown data through the GitHub API").get("run")).strip());
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> job(String workflow, String name) {
        return job(yaml(workflow), name);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> job(Map<String, Object> root, String name) {
        return (Map<String, Object>) ((Map<String, Object>) root.get("jobs")).get(name);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> yaml(String workflow) {
        return new Yaml().load(workflow);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapping(Map<String, Object> parent, String name) {
        Object value = parent.get(name);
        if (value == null && "on".equals(name)) {
            value = parent.get(Boolean.TRUE);
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> step(Map<String, Object> job, String name) {
        return steps(job).stream()
                .filter(item -> name.equals(item.get("name")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Map<String, Object>> steps(Map<String, Object> job) {
        return (java.util.List<Map<String, Object>>) job.get("steps");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> with(Map<String, Object> step) {
        return (Map<String, Object>) step.get("with");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> env(Map<String, Object> step) {
        return (Map<String, Object>) step.get("env");
    }
}
