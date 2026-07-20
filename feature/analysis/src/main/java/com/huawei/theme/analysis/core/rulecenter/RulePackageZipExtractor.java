package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class RulePackageZipExtractor {
    private static final int MAX_ENTRIES = 10_000;
    private static final long MAX_UNCOMPRESSED_BYTES = 100L * 1024L * 1024L;

    public Path extractAndValidate(Path zipPath, Path outputDirectory) {
        if (!Files.isRegularFile(zipPath)) {
            throw new IllegalArgumentException("baseline rule-package.zip does not exist");
        }
        if (Files.exists(outputDirectory)) {
            throw new IllegalArgumentException("baseline output directory already exists");
        }
        Path parent = outputDirectory.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("baseline output must have a parent directory");
        }
        Path staging = null;
        Path validationAssets = null;
        try {
            Files.createDirectories(parent);
            staging = Files.createTempDirectory(parent, "baseline-extract-");
            extract(zipPath, staging);
            validationAssets = Files.createTempDirectory(parent, "baseline-validation-");
            new RulePackagePublicationPreparer().prepare(staging, validationAssets);
            deleteTree(validationAssets);
            validationAssets = null;
            Files.move(staging, outputDirectory, StandardCopyOption.ATOMIC_MOVE);
            staging = null;
            return outputDirectory;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to extract baseline rule package", exception);
        } finally {
            deleteTreeQuietly(validationAssets);
            deleteTreeQuietly(staging);
        }
    }

    private void extract(Path zipPath, Path staging) throws IOException {
        Path normalizedStaging = staging.toAbsolutePath().normalize();
        int entries = 0;
        long totalBytes = 0;
        try (InputStream input = Files.newInputStream(zipPath);
                ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IllegalArgumentException("baseline zip contains too many entries");
                }
                Path target = normalizedStaging.resolve(entry.getName()).normalize();
                if (!target.startsWith(normalizedStaging) || target.equals(normalizedStaging)) {
                    throw new IllegalArgumentException("baseline zip entry escapes output directory");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (OutputStream output = Files.newOutputStream(target)) {
                        int read;
                        while ((read = zip.read(buffer)) >= 0) {
                            totalBytes += read;
                            if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                                throw new IllegalArgumentException(
                                        "baseline zip exceeds uncompressed size limit");
                            }
                            output.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private void deleteTreeQuietly(Path root) {
        if (root == null) {
            return;
        }
        try {
            deleteTree(root);
        } catch (IOException ignored) {
            // Best-effort cleanup of a uniquely created temporary directory.
        }
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
