package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class RulePackageDigest {
    private static final List<String> CONTENT_ROOTS = List.of(
            "rules", "functions", "source-markdown", "verification");

    private RulePackageDigest() {
    }

    public static String compute(Path packageDirectory) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Path file : contentFiles(packageDirectory)) {
                String relative = packageDirectory.relativize(file)
                        .toString()
                        .replace('\\', '/');
                byte[] content = normalizedContent(packageDirectory, file, relative);
                digest.update(relative.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(content);
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static List<Path> contentFiles(Path packageDirectory) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String rootName : CONTENT_ROOTS) {
            Path root = packageDirectory.resolve(rootName);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile).forEach(files::add);
            }
        }
        files.sort(Comparator.comparing(path -> packageDirectory.relativize(path)
                .toString()
                .replace('\\', '/')));
        return files;
    }

    private static byte[] normalizedContent(
            Path packageDirectory,
            Path file,
            String relative) throws IOException {
        if (!"verification/release-report.json".equals(relative)) {
            return Files.readAllBytes(file);
        }
        JsonObject report = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        report.addProperty("manifestContentSha256", "");
        return new Gson().toJson(report).getBytes(StandardCharsets.UTF_8);
    }
}
