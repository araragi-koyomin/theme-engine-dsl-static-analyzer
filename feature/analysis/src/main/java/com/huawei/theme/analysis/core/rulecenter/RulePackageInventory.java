package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RulePackageInventory {
    @Builder.Default
    List<String> ruleFiles = List.of();
    @Builder.Default
    List<String> functionFiles = List.of();

    static RulePackageInventory fromPackage(Path packageDirectory) throws IOException {
        return builder()
                .ruleFiles(files(packageDirectory.resolve("rules"), "rules"))
                .functionFiles(files(packageDirectory.resolve("functions"), "functions"))
                .build();
    }

    static RulePackageInventory fromInputs(Path rulesDirectory, Path functionsDirectory)
            throws IOException {
        return builder()
                .ruleFiles(files(rulesDirectory, "rules"))
                .functionFiles(files(functionsDirectory, "functions"))
                .build();
    }

    List<String> missingFrom(RulePackageInventory actual) {
        List<String> missing = new ArrayList<>();
        for (String path : ruleFiles) {
            if (!actual.ruleFiles.contains(path)) {
                missing.add(path);
            }
        }
        for (String path : functionFiles) {
            if (!actual.functionFiles.contains(path)) {
                missing.add(path);
            }
        }
        return List.copyOf(missing);
    }

    private static List<String> files(Path root, String prefix) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .map(path -> prefix + "/" + path.toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }
}
