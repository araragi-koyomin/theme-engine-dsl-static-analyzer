package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(workflow.contains("fetch-depth: 0"));
        assertTrue(workflow.contains("refs/heads/main"));
        assertTrue(workflow.contains("RULE_CENTER_CREATED_AT"));
        assertTrue(new Yaml().load(workflow) instanceof Map);
    }

    @Test
    void releaseIsCreatedOnlyAfterGateWithExactlyThreeFixedAssets() throws IOException {
        String workflow = read(".github/workflows/publish-rule-package.yml");
        Map<String, Object> publish = job(workflow, "publish");
        String command = (String) step(
                publish, "Create immutable approved GitHub Release").get("run");

        assertTrue(command.contains("gh release create"));
        assertTrue(workflow.contains("rules-v${RULE_CENTER_PACKAGE_VERSION}"));
        assertTrue(workflow.contains("rule-package.zip"));
        assertTrue(workflow.contains("manifest.json"));
        assertTrue(workflow.contains("release-report.json"));
        assertTrue(workflow.contains("--notes-file"));
        assertTrue(workflow.contains("--verify-tag"));
        assertTrue(workflow.contains("immutable-releases"));
        assertTrue(workflow.contains("X-GitHub-Api-Version: 2026-03-10"));
        assertTrue(workflow.contains(".immutable == true"));
        assertFalse(workflow.contains("--draft"));
        assertFalse(workflow.contains("--prerelease"));
        assertFalse(workflow.contains("actions/download-artifact"));
        assertTrue(workflow.indexOf("ruleCenterPrepareRelease")
                < workflow.indexOf("gh release create"));
        assertEquals(List.of(
                        "build/rule-center-publish/release/rule-package.zip",
                        "build/rule-center-publish/release/manifest.json",
                        "build/rule-center-publish/release/release-report.json"),
                positionalReleaseAssets(command));
    }

    @Test
    void previousApprovedPackageIsOptionalBaselineAndChangedDocsAreExplicit()
            throws IOException {
        String workflow = read(".github/workflows/publish-rule-package.yml");
        String baseline = (String) step(
                job(workflow, "publish"),
                "Restore the previous approved complete-package baseline").get("run");
        int selectionStart = baseline.indexOf("latest_tag=");
        int selectionEnd = baseline.indexOf("\nif [[ -z", selectionStart);
        String baselineSelection = baseline.substring(selectionStart, selectionEnd);

        assertTrue(workflow.contains("gh release download"));
        assertTrue(workflow.contains("RULE_CENTER_BASELINE_RULES"));
        assertTrue(workflow.contains("RULE_CENTER_DOCUMENT_LIST"));
        assertTrue(workflow.contains("--diff-filter=AM"));
        assertTrue(baselineSelection.contains("--paginate"));
        assertTrue(baselineSelection.contains(".immutable == true"));
        assertTrue(baselineSelection.contains("| sort -V | tail -n 1"));
        assertFalse(baselineSelection.contains("published_at"));
        assertTrue(baseline.contains(
                "Package version must be greater than latest approved version"));
        assertTrue(workflow.contains("No previous approved Release"));
        assertTrue(workflow.indexOf("No previous approved Release")
                < workflow.lastIndexOf("git ls-files 'rule-center/docs/**/*.md'"));
        assertFalse(workflow.matches("(?s).*uses: actions/checkout@v[0-9].*"));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(repositoryRoot.resolve(relativePath));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> job(String workflow, String name) {
        Map<String, Object> root = new Yaml().load(workflow);
        return (Map<String, Object>) ((Map<String, Object>) root.get("jobs")).get(name);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> step(Map<String, Object> job, String name) {
        return ((List<Map<String, Object>>) job.get("steps")).stream()
                .filter(item -> name.equals(item.get("name")))
                .findFirst()
                .orElseThrow();
    }

    private List<String> positionalReleaseAssets(String command) {
        String normalized = command.replace("\\\r\n", " ")
                .replace("\\\n", " ");
        String[] tokens = normalized.trim().split("\\s+");
        List<String> assets = new ArrayList<>();
        int create = -1;
        for (int index = 0; index + 2 < tokens.length; index++) {
            if ("gh".equals(tokens[index]) && "release".equals(tokens[index + 1])
                    && "create".equals(tokens[index + 2])) {
                create = index + 3;
                break;
            }
        }
        if (create < 0 || create >= tokens.length) {
            return List.of();
        }
        for (int index = create + 1; index < tokens.length; index++) {
            String token = tokens[index];
            if (token.startsWith("--")) {
                break;
            }
            assets.add(token);
        }
        return List.copyOf(assets);
    }
}
