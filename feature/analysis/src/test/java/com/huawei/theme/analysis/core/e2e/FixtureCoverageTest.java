package com.huawei.theme.analysis.core.e2e;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixtureCoverageTest {

    private static final Path FIXTURES_ROOT = Path.of("src/test/resources/fixtures");
    private static final Path DSL_ROOT = Path.of("src/test/resources/dsl");

    @Test
    void everyXmlFixture_hasMatchingGoldenFile() throws Exception {
        List<String> missing = new ArrayList<>();
        for (Path root : new Path[]{FIXTURES_ROOT, DSL_ROOT}) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".xml"))
                        .forEach(p -> {
                            Path golden = Path.of(p.toString().replace(".xml", ".expected.json"));
                            if (!Files.exists(golden)) {
                                missing.add(p.toString());
                            }
                        });
            }
        }
        assertTrue(missing.isEmpty(),
                "Following fixtures lack a .expected.json golden file:\n" + String.join("\n", missing));
    }

    @Test
    void everyGoldenFile_hasMatchingXmlFixture() throws Exception {
        List<String> orphans = new ArrayList<>();
        for (Path root : new Path[]{FIXTURES_ROOT, DSL_ROOT}) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".expected.json"))
                        .forEach(p -> {
                            Path xml = Path.of(p.toString().replace(".expected.json", ".xml"));
                            if (!Files.exists(xml)) {
                                orphans.add(p.toString());
                            }
                        });
            }
        }
        assertTrue(orphans.isEmpty(),
                "Following golden files have no matching .xml fixture:\n" + String.join("\n", orphans));
    }
}
