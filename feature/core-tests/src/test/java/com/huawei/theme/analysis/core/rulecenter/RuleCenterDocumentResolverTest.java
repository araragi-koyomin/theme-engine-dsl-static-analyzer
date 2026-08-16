package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleCenterDocumentResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void acceptsProposalDocumentsOutsideTrustedCheckoutWithExplicitRootAndRevision()
            throws IOException {
        Path trusted = tempDir.resolve("trusted");
        Path proposalDocs = tempDir.resolve("proposal/rule-center/docs");
        Path document = proposalDocs.resolve("elements/view/Image/image.md");
        Files.createDirectories(document.getParent());
        Files.writeString(document, "# Image", StandardCharsets.UTF_8);
        Path list = trusted.resolve("build/documents.txt");
        Files.createDirectories(list.getParent());
        Files.writeString(list, document.toString(), StandardCharsets.UTF_8);

        List<RuleDocumentRevision> documents = new RuleCenterDocumentResolver().resolve(
                trusted, Map.of(
                        "RULE_CENTER_DOCUMENT_LIST", list.toString(),
                        "RULE_CENTER_DOCUMENT_ROOT", proposalDocs.toString(),
                        "RULE_CENTER_DOCUMENT_REVISION", "head-sha"));

        assertEquals(1, documents.size());
        assertEquals("elements/view/Image/image", documents.get(0).getDocumentId());
        assertEquals("elements/view/Image/image.md",
                documents.get(0).getSourceMarkdownRelativePath());
        assertEquals("head-sha", documents.get(0).getRevision());
    }

    @Test
    void rejectsDocumentThatEscapesTheExplicitDocumentRoot() throws IOException {
        Path trusted = tempDir.resolve("trusted");
        Path proposalDocs = tempDir.resolve("proposal/rule-center/docs");
        Files.createDirectories(proposalDocs);
        Path outside = tempDir.resolve("proposal/evil.md");
        Files.writeString(outside, "# Evil", StandardCharsets.UTF_8);
        Path list = trusted.resolve("documents.txt");
        Files.createDirectories(trusted);
        Files.writeString(list, outside.toString(), StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class,
                () -> new RuleCenterDocumentResolver().resolve(trusted, Map.of(
                        "RULE_CENTER_DOCUMENT_LIST", list.toString(),
                        "RULE_CENTER_DOCUMENT_ROOT", proposalDocs.toString())));
    }
}
